package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;

public class SettingsActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnApiSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, ApiSettingsActivity.class)));
            
        findViewById(R.id.btnVoiceSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, VoiceSettingsActivity.class)));
            
        findViewById(R.id.btnMessageSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, MessageSettingsActivity.class)));

        findViewById(R.id.btnDataSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, DataSettingsActivity.class)));

        findViewById(R.id.btnEmojiSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, EmojiManageActivity.class)));

        findViewById(R.id.btnLogSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, LogActivity.class)));
    }
}
