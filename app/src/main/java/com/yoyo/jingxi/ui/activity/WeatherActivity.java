package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.ThemeManager;

public class WeatherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);
        ThemeManager.applyDesktopThemeBackground(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("天气详情");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        String cityName = getIntent().getStringExtra("cityName");
        String weatherDesc = getIntent().getStringExtra("weatherDesc");
        String temperature = getIntent().getStringExtra("temperature");

        TextView tvCity = findViewById(R.id.tvCity);
        TextView tvTemp = findViewById(R.id.tvTemp);
        TextView tvDesc = findViewById(R.id.tvDesc);

        if (cityName != null) tvCity.setText(cityName);
        if (temperature != null) tvTemp.setText(temperature);
        if (weatherDesc != null) tvDesc.setText(weatherDesc);
    }
}
