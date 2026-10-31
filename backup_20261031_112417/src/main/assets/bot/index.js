// ARSLAN BOT — Group Manager for Baileys 6.7.x
// Commands: .menu .help .set .warn .warns .resetwarn .kick .add .promote .demote
//           .open .close .admins .tagall .rules .setrules .info .owner .mute .unmute
// Important: the bot must be a group admin to delete messages/remove members/change group settings.

const {
  default: makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
  jidNormalizedUser,
  getContentType,
} = require('@whiskeysockets/baileys');
const P = require('pino');
const qrcode = require('qrcode-terminal');
const Database = require('better-sqlite3');
const fs = require('fs');

const PREFIX = process.env.BOT_PREFIX || '.';
const BOT_PHONE = (process.env.BOT_PHONE || '').replace(/\D/g, '');
const OWNER_NUMBER = (process.env.BOT_OWNER || '').replace(/\D/g, '');
const MAX_WARN = 3;
const AUTH_DIR = process.env.BOT_AUTH_DIR || 'auth';
const DB_PATH = process.env.BOT_DB_PATH || 'arsalan.db';

const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');
db.exec(`
CREATE TABLE IF NOT EXISTS settings (grp TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(grp,key));
CREATE TABLE IF NOT EXISTS warns (grp TEXT NOT NULL, usr TEXT NOT NULL, count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(grp,usr));
CREATE TABLE IF NOT EXISTS rules (grp TEXT PRIMARY KEY, body TEXT NOT NULL DEFAULT '');
CREATE TABLE IF NOT EXISTS counts (grp TEXT PRIMARY KEY, joined INTEGER NOT NULL DEFAULT 0, left_count INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS muted (grp TEXT NOT NULL, usr TEXT NOT NULL, PRIMARY KEY(grp,usr));
CREATE TABLE IF NOT EXISTS blocked_words (grp TEXT NOT NULL, word TEXT NOT NULL, PRIMARY KEY(grp,word));
`);
const DEFAULTS = {
  antilink: 'on', welcome: 'on', farewell: 'on', antiflood: 'on',
  antirepeat: 'on', antiads: 'on', onlyadmin: 'off', protectsettings: 'on',
};
const getSetting = db.prepare('SELECT value FROM settings WHERE grp=? AND key=?');
const putSetting = db.prepare('INSERT INTO settings(grp,key,value) VALUES(?,?,?) ON CONFLICT(grp,key) DO UPDATE SET value=excluded.value');
const getSet = (g,k) => (getSetting.get(g,k)?.value ?? DEFAULTS[k] ?? 'off') === 'on';
const setSet = (g,k,v) => putSetting.run(g,k,v);
const getWarn = (g,u) => db.prepare('SELECT count FROM warns WHERE grp=? AND usr=?').get(g,u)?.count || 0;
const addWarn = (g,u) => {
  db.prepare('INSERT INTO warns(grp,usr,count) VALUES(?,?,1) ON CONFLICT(grp,usr) DO UPDATE SET count=count+1').run(g,u);
  return getWarn(g,u);
};
const resetWarn = (g,u) => db.prepare('DELETE FROM warns WHERE grp=? AND usr=?').run(g,u);
const getRules = g => db.prepare('SELECT body FROM rules WHERE grp=?').get(g)?.body || 'قوانین گروه هنوز تنظیم نشده است.';
const setRules = (g,t) => db.prepare('INSERT INTO rules(grp,body) VALUES(?,?) ON CONFLICT(grp) DO UPDATE SET body=excluded.body').run(g,t);
const isMuted = (g,u) => !!db.prepare('SELECT 1 FROM muted WHERE grp=? AND usr=?').get(g,u);
const mentionJid = s => (s || '').replace(/[^0-9]/g,'') + '@s.whatsapp.net';
const digits = s => (s || '').split('@')[0].split(':')[0].replace(/\D/g,'');
const jidOf = p => p?.id || p?.jid || '';
const LINK_RE = /(?:https?:\/\/|www\.|chat\.whatsapp\.com\/|wa\.me\/|t\.me\/|telegram\.me\/|instagram\.com\/|facebook\.com\/|bit\.ly\/|tinyurl\.com\/)/i;
const AD_RE = /\b(buy now|discount|promo code|earn money|investment opportunity|تبلیغ|خرید و فروش|تخفیف ویژه)\b/i;
const BOX = '╔═══━━━─── • ───━━━═══╗';
const DIV = '╠══════════════════════╣';
const END = '╚═══━━━─── • ───━━━═══╝';
const BRAND = '𓆩 ♛ 𝑨𝑹𝑺𝑳𝑨𝑵 𝑩𝑶𝑻 ♛ 𓆪';
const crown = (title, body) => `${BOX}\n${BRAND}\n${DIV}\n👑 ${title}\n${DIV}\n${body}\n${END}`;
const menuText = () => crown('منوی فرماندهی', [
'⚔️ مدیریت: .kick @عضو | .add شماره',
'🛡️ ادمین: .promote @عضو | .demote @عضو',
'🔒 گروه: .close | .open | .mute | .unmute',
'🚫 امنیت: .set antilink on/off',
'🌊 ضدسیل: .set antiflood on/off',
'🔁 ضدتکرار: .set antirepeat on/off',
'📢 ضدتبلیغ: .set antiads on/off',
'👁️ فقط ادمین: .set onlyadmin on/off',
'🎉 خوش‌آمد: .set welcome on/off',
'📜 قوانین: .rules | .setrules متن قوانین',
'⚠️ اخطار: .warn @عضو دلیل | .warns | .resetwarn @عضو',
'👑 .admins | .tagall | .info | .owner',
'⚙️ تنظیمات: .set نام on/off',
].join('\n'));
function unwrapMessage(m) {
  let msg = m?.message;
  if (!msg) return { type: '', text: '', msg: {} };

  for (let i = 0; i < 5; i++) {
    const t = getContentType(msg);
    if (t === 'ephemeralMessage') {
      msg = msg.ephemeralMessage?.message;
      continue;
    }
    if (t === 'viewOnceMessage' || t === 'viewOnceMessageV2') {
      msg = msg[t]?.message;
      continue;
    }
    if (t === 'documentWithCaptionMessage') {
      msg = msg.documentWithCaptionMessage?.message;
      continue;
    }
    break;
  }

  const type = getContentType(msg) || '';
  const obj = msg?.[type];
  let text = '';

  if (type === 'conversation') {
    text = typeof obj === 'string' ? obj : '';
  } else if (type === 'extendedTextMessage') {
    text = obj?.text || '';
  } else {
    text = obj?.text || obj?.caption || '';
  }

  return {
    type,
    text: String(text || ''),
    msg: obj && typeof obj === 'object' ? obj : {}
  };
}
function getMentioned(m) {
  const x = m.message?.extendedTextMessage?.contextInfo?.mentionedJid || [];
  return [...new Set(x.map(jidNormalizedUser))];
}
function safeErr(label,e) { console.error(`[${label}]`, e?.stack || e?.message || e); }
let active = false;
let retryTimer = null;
let sockRef = null;
const recent = new Map();

async function start() {
  if (active) return;
  active = true;
  try {
    if (!fs.existsSync(AUTH_DIR)) fs.mkdirSync(AUTH_DIR, { recursive: true });
    const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
    const sock = makeWASocket({
      auth: state,
      logger: P({ level: process.env.BOT_LOG_LEVEL || 'silent' }),
      printQRInTerminal: false,
      browser: ['ARSLAN BOT', 'Chrome', '1.0.0'],
      markOnlineOnConnect: false,
      syncFullHistory: false,
      connectTimeoutMs: 60000,
      defaultQueryTimeoutMs: 60000,
    });
    sockRef = sock;
    if (BOT_PHONE && !state.creds.registered) {
      setTimeout(async () => {
        try {
          const pairCode = await sock.requestPairingCode(BOT_PHONE);
          console.log('🔑 کد اتصال ARSLAN BOT:', pairCode);
          console.log('WhatsApp > Linked devices > Link with phone number');
        } catch (e) { safeErr('pairing-code', e); }
      }, 3000);
    }
    sock.ev.on('creds.update', saveCreds);
    sock.ev.on('connection.update', async ({ connection, lastDisconnect, qr }) => {
      if (qr && !BOT_PHONE) {
        console.log('\n📱 QR را در WhatsApp > Linked devices اسکن کن:\n');
        qrcode.generate(qr, { small: true });
      }
      if (connection === 'connecting') console.log('🔄 اتصال به واتساپ...');
      if (connection === 'open') console.log('✅ ARSLAN BOT وصل شد؛ شاهزاده آمادهٔ خدمت است.');
      if (connection === 'close') {
        active = false;
        const code = lastDisconnect?.error?.output?.statusCode;
        console.error('❌ اتصال بسته شد؛ statusCode:', code, '|', lastDisconnect?.error?.message || 'unknown');
        if (code === DisconnectReason.loggedOut || code === 401) {
          console.error(`🔐 نشست ${AUTH_DIR} رد شده است. برای نگه‌داشتن نسخهٔ قبلی، آن را حذف نکن؛ پوشه را backup بگیر و بعد نشست تازه بساز.`);
          return;
        }
        if (!retryTimer) retryTimer = setTimeout(() => { retryTimer = null; start().catch(e => { active=false; safeErr('restart',e); }); }, 5000);
      }
    });

    sock.ev.on('group-participants.update', async ({ id, participants, action }) => {
      try {
        const meta = await sock.groupMetadata(id);
        for (const raw of participants || []) {
          const u = jidNormalizedUser(raw);
          const c = db.prepare('SELECT joined,left_count FROM counts WHERE grp=?').get(id) || { joined:0,left_count:0 };
          if (action === 'add') {
            db.prepare('INSERT INTO counts(grp,joined,left_count) VALUES(?,?,?) ON CONFLICT(grp) DO UPDATE SET joined=joined+1').run(id,1,0);
            if (getSet(id,'welcome')) await sock.sendMessage(id,{text:crown('خوش آمدید',`🌟 خوش آمدی @${digits(u)} به خانوادهٔ ارسلان!\n👥 اعضای فعلی: ${meta.participants.length}`),mentions:[u]});
          } else if (action === 'remove') {
            db.prepare('INSERT INTO counts(grp,joined,left_count) VALUES(?,?,?) ON CONFLICT(grp) DO UPDATE SET left_count=left_count+1').run(id,0,1);
            if (getSet(id,'farewell')) await sock.sendMessage(id,{text:crown('بدرقه',`🕊️ @${digits(u)} از گروه خارج شد.\n👥 اعضای فعلی: ${meta.participants.length}`),mentions:[u]});
          }
        }
      } catch(e) { safeErr('participants',e); }
    });

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
      if (type !== 'notify') return;
      for (const m of messages || []) {
        try { await handleMessage(sock,m); } catch(e) { safeErr('message',e); }
      }
    });
  } catch(e) {
    active = false;
    safeErr('start',e);
    if (!retryTimer) retryTimer=setTimeout(()=>{retryTimer=null;start().catch(safeErr.bind(null,'retry'));},7000);
  }
}

async function handleMessage(sock,m) {
  if (!m?.message || m.key?.fromMe) return;
  const g = m.key.remoteJid;
  if (!g || g === 'status@broadcast' || !g.endsWith('@g.us')) return;
  const sender = jidNormalizedUser(m.key.participant || m.participant || '');
  if (!sender) return;
  const { text } = unwrapMessage(m);
  const trimmed = text.trim();
  const isCommand = trimmed.startsWith(PREFIX);
  const meta = await sock.groupMetadata(g);
  const matchesParticipant = (p, jid) => {
    const wanted = jidNormalizedUser(jid || '');
    const ids = [p?.id, p?.jid, p?.lid, p?.phoneNumber].filter(Boolean);

    return ids.some(value => {
      const valueStr = String(value);
      if (valueStr.includes('@')) {
        return jidNormalizedUser(valueStr) === wanted;
      }
      const number = valueStr.replace(/\D/g, '');
      return wanted.endsWith('@s.whatsapp.net') &&
        number.length > 0 && number === digits(wanted);
    });
  };

  const participant = meta.participants.find(p => matchesParticipant(p, sender));
  const isAdmin = !!participant?.admin;
  const botJid = jidNormalizedUser(sock.user?.id || '');
  const botP = meta.participants.find(p => matchesParticipant(p, botJid));
  const botAdmin = !!botP?.admin;
  const isOwner = OWNER_NUMBER && digits(sender) === OWNER_NUMBER;
  const isPrivileged = isAdmin || isOwner;

  const reply = (t, mentions=[]) => sock.sendMessage(g,{text:t,mentions},{quoted:m});
  const del = async () => {
    if (!botAdmin) return false;
    await sock.sendMessage(g,{delete:m.key});
    return true;
  };
  const targets = getMentioned(m);
  const target = targets[0];
  const isTargetValid = target && digits(target) !== digits(sender);

  // Anti-link: immediate delete + removal for non-admins, if bot has admin rights.
  if (!isCommand && trimmed && getSet(g,'antilink') && LINK_RE.test(trimmed) && !isPrivileged) {
    if (botAdmin) {
      try { await del(); } catch(e) { safeErr('delete-link',e); }
      try {
        await sock.groupParticipantsUpdate(g,[sender],'remove');
        await reply(crown('حکم امنیتی',`🚫 لینک ممنوعه شناسایی شد.\n👤 متخلف: @${digits(sender)}\n⚔️ اقدام: اخراج فوری`),[sender]);
      } catch(e) {
        safeErr('remove-link-sender',e);
        await reply(crown('هشدار امنیتی',`🚫 لینک شناسایی شد؛ اما ربات نتوانست عضو را اخراج کند. ادمین‌بودن ربات و سطح دسترسی را بررسی کن.\n👤 @${digits(sender)}`),[sender]);
      }
    } else {
      try { await del(); } catch(e) { safeErr('delete-link',e); }
      await reply(crown('هشدار امنیتی','🚫 لینک ممنوع است؛ برای اخراج فوری، ربات باید ادمین باشد.'));
    }
    return;
  }

  if (!isPrivileged && getSet(g,'onlyadmin') && !isCommand) {
    if (botAdmin) try { await del(); } catch(e) { safeErr('admin-only-delete',e); }
    return;
  }

  if (!isPrivileged && getSet(g,'antiflood') && !isCommand) {
    const now=Date.now(), key=g+':'+sender, arr=(recent.get(key)||[]).filter(t=>now-t<8000);
    arr.push(now); recent.set(key,arr);
    if (arr.length >= 6) {
      if (botAdmin) try { await del(); } catch(e) {}
      await reply(crown('کنترول سیل پیام',`🌊 @${digits(sender)} لطفاً از ارسال پیاپی خودداری کن.`),[sender]);
      return;
    }
  }
  if (!isPrivileged && getSet(g,'antirepeat') && !isCommand && trimmed.length>3) {
    const key=g+':'+sender, old=recent.get('text:'+key);
    if (old && old.text===trimmed && Date.now()-old.time<30000) {
      if (botAdmin) try { await del(); } catch(e) {}
      return;
    }
    recent.set('text:'+key,{text:trimmed,time:Date.now()});
  }
  if (!isPrivileged && getSet(g,'antiads') && !isCommand && AD_RE.test(trimmed)) {
    if (botAdmin) try { await del(); } catch(e) {}
    const n=addWarn(g,sender);
    if (n>=MAX_WARN && botAdmin) {
      await reply(crown('حکم اخراج',`⚠️ @${digits(sender)} سه اخطار تکمیل شد.`),[sender]);
      try { await sock.groupParticipantsUpdate(g,[sender],'remove'); resetWarn(g,sender); } catch(e) { safeErr('antiads-kick',e); }
    } else await reply(crown('اخطار تبلیغات',`⚠️ @${digits(sender)} تبلیغات مجاز نیست.\n📊 اخطار ${n}/${MAX_WARN}`),[sender]);
    return;
  }

  if (!isCommand) return;
  const [rawCmd,...args] = trimmed.slice(PREFIX.length).split(/\s+/);
  const cmd=(rawCmd||'').toLowerCase();
  const argText=args.join(' ');
  const needAdmin = async () => {
    if (!isPrivileged) { await reply(crown('دسترسی رد شد','🔐 این فرمان فقط برای ادمین‌ها و مالک ربات است.')); return false; }
    return true;
  };
  const needBotAdmin = async () => {
    if (!botAdmin) { await reply(crown('دسترسی ناکافی','⚙️ برای اجرای این فرمان، ربات باید ادمین گروه باشد.')); return false; }
    return true;
  };
  switch(cmd) {
    case 'menu': case 'help': case 'start': return reply(menuText());
    case 'owner': return reply(crown('مالک ربات', OWNER_NUMBER ? `👑 @${OWNER_NUMBER}\n📞 تماس با مالک` : 'شماره مالک تنظیم نشده؛ متغیر BOT_OWNER را تنظیم کن.'));
    case 'rules': return reply(crown('قوانین گروه',getRules(g)));
    case 'setrules':
      if (!(await needAdmin())) return;
      if (!argText) return reply('استفاده: .setrules متن قوانین');
      setRules(g,argText); return reply(crown('قوانین ثبت شد','📜 قوانین جدید ذخیره شد.'));
    case 'set': {
      if (!(await needAdmin())) return;
      const [k,v] = args;
      if (!k || !v || !Object.hasOwn(DEFAULTS,k) || !['on','off'].includes(v.toLowerCase()))
        return reply('نمونه: .set antilink on\nکلیدها: '+Object.keys(DEFAULTS).join(', '));
      setSet(g,k,v.toLowerCase()); return reply(crown('تنظیمات به‌روز شد',`⚙️ ${k} = ${v.toLowerCase()}`));
    }
    case 'warns': {
      const n=getWarn(g,sender);
      return reply(crown('پروندهٔ اخطار',`👤 @${digits(sender)}\n⚠️ ${n}/${MAX_WARN}`),[sender]);
    }
    case 'resetwarn': {
      if (!(await needAdmin())) return;
      if (!target) return reply('عضو را منشن کن: .resetwarn @عضو');
      resetWarn(g,target); return reply(crown('پرونده پاک شد',`♻️ اخطارهای @${digits(target)} پاک شد.`),[target]);
    }
    case 'warn': {
      if (!(await needAdmin())) return;
      if (!target) return reply('استفاده: .warn @عضو دلیل');
      if (!(await needBotAdmin())) return;
      const n=addWarn(g,target);
      if (n>=MAX_WARN) {
        await reply(crown('حکم اخراج',`⚠️ @${digits(target)} به ${MAX_WARN} اخطار رسید.\n📜 دلیل: ${argText||'تخلف از قوانین'}`),[target]);
        try { await sock.groupParticipantsUpdate(g,[target],'remove'); resetWarn(g,target); } catch(e) { safeErr('warn-kick',e); }
      } else await reply(crown('اخطار رسمی',`⚠️ عضو: @${digits(target)}\n📜 دلیل: ${argText||'تخلف از قوانین'}\n📊 اخطار ${n}/${MAX_WARN}`),[target]);
      return;
    }
    case 'kick': case 'remove': {
      if (!(await needAdmin()) || !(await needBotAdmin())) return;
      if (!target) return reply('عضو را منشن کن: .kick @عضو');
      if (digits(target)===digits(botJid)) return reply('❌ ربات نمی‌تواند خودش را اخراج کند.');
      await sock.groupParticipantsUpdate(g,[target],'remove');
      return reply(crown('حکم اجرا شد',`⚔️ @${digits(target)} از گروه اخراج شد.`),[target]);
    }
    case 'add': {
      if (!(await needAdmin()) || !(await needBotAdmin())) return;
      const number=(args[0]||'').replace(/\D/g,'');
      if (number.length<7) return reply('استفاده: .add شماره با کود کشور');
      const res=await sock.groupParticipantsUpdate(g,[mentionJid(number)],'add');
      return reply(crown('درخواست افزودن',JSON.stringify(res)));
    }
    case 'promote': case 'demote': {
      if (!(await needAdmin()) || !(await needBotAdmin())) return;
      if (!target) return reply(`استفاده: .${cmd} @عضو`);
      await sock.groupParticipantsUpdate(g,[target],cmd==='promote'?'promote':'demote');
      return reply(crown('مدیریت اعضا',`👑 @${digits(target)} ${cmd==='promote'?'ادمین شد':'از ادمینی برکنار شد'}.`),[target]);
    }
    case 'open': case 'close': {
      if (!(await needAdmin()) || !(await needBotAdmin())) return;
      await sock.groupSettingUpdate(g,cmd==='close'?'announcement':'not_announcement');
      return reply(crown('تنظیمات گروه',cmd==='close'?'🔒 فقط ادمین‌ها می‌توانند پیام بفرستند.':'🔓 همهٔ اعضا می‌توانند پیام بفرستند.'));
    }
    case 'mute': case 'unmute': {
      if (!(await needAdmin())) return;
      if (!target) return reply(`استفاده: .${cmd} @عضو`);
      if (cmd==='mute') db.prepare('INSERT OR IGNORE INTO muted(grp,usr) VALUES(?,?)').run(g,target);
      else db.prepare('DELETE FROM muted WHERE grp=? AND usr=?').run(g,target);
      return reply(crown('محدودیت عضو',`👤 @${digits(target)} ${cmd==='mute'?'در فهرست محدودیت ثبت شد':'از فهرست محدودیت خارج شد'}.\nتوجه: این فهرست به‌تنهایی دسترسی ارسال پیام را در واتساپ مسدود نمی‌کند.`),[target]);
    }
    case 'admins': {
      const list=meta.participants.filter(p=>p.admin).map(p=>'👑 @'+digits(jidOf(p)));
      return reply(crown('شورای ادمین‌ها',list.join('\n')||'ادمینی پیدا نشد.'),meta.participants.filter(p=>p.admin).map(p=>jidNormalizedUser(jidOf(p))));
    }
    case 'tagall': {
      if (!(await needAdmin())) return;
      const all=meta.participants.map(p=>jidNormalizedUser(jidOf(p)));
      return sock.sendMessage(g,{text:crown('فراخوان همگانی',`📣 ${argText||'توجه اعضای گروه'}\n\n`+all.map(u=>'𓆩 @'+digits(u)+' 𓆪').join('\n')),mentions:all},{quoted:m});
    }
    case 'info': {
      const c=db.prepare('SELECT joined,left_count FROM counts WHERE grp=?').get(g)||{joined:0,left_count:0};
      const admins=meta.participants.filter(p=>p.admin).length;
      return reply(crown('گزارش گروه',`🏷️ نام: ${meta.subject}\n👥 اعضا: ${meta.participants.length}\n👑 ادمین‌ها: ${admins}\n➕ ورود ثبت‌شده: ${c.joined}\n➖ خروج ثبت‌شده: ${c.left_count}\n🛡️ ضدلینک: ${getSet(g,'antilink')?'روشن':'خاموش'}`));
    }
    default: return;
  }
}

process.on('unhandledRejection', e => safeErr('unhandledRejection',e));
process.on('uncaughtException', e => { safeErr('uncaughtException',e); });
start().catch(e=>{active=false;safeErr('boot',e);});
