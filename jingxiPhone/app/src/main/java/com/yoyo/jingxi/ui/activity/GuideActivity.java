package com.yoyo.jingxi.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.ThemeManager;

public class GuideActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 101;

    private Button btnNotification;
    private Button btnBatteryOptimization;
    private Button btnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        btnNotification = findViewById(R.id.btnNotification);
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization);
        btnFinish = findViewById(R.id.btnFinish);

        btnNotification.setOnClickListener(v -> requestNotificationPermission());
        btnBatteryOptimization.setOnClickListener(v -> requestBatteryOptimizationExemption());
        btnFinish.setOnClickListener(v -> finishGuide());

        updateUIStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIStatus();
    }

    private void updateUIStatus() {
        // 检查通知权限
        boolean hasNotification = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotification = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        btnNotification.setText(hasNotification ? "已授权" : "去授权");
        btnNotification.setEnabled(!hasNotification);

        // 检查电池优化白名单
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isIgnoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            btnBatteryOptimization.setText(isIgnoring ? "已设置" : "去设置（推荐）");
            btnBatteryOptimization.setEnabled(!isIgnoring);
        } else {
            btnBatteryOptimization.setText("已设置");
            btnBatteryOptimization.setEnabled(false);
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Uri uri = android.net.Uri.parse("package:" + getPackageName());
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri);
            startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
        } else {
            // Android 13 以下，引导去设置页
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updateUIStatus();
        }
    }

    private void finishGuide() {
        SpUtils.putBoolean("HAS_SHOWN_GUIDE", true);
        startActivity(new Intent(this, DesktopActivity.class));
        finish();
    }
}