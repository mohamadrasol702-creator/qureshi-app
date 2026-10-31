package com.qureshisahib;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean has = FeatureManager.getPlan(this) != null;
        startActivity(new Intent(this, has ? FeaturesActivity.class : PlanActivity.class));
        finish();
    }
}
