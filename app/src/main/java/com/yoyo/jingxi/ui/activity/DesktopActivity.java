package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DesktopActivity extends AppCompatActivity {

    private TextView tvTime;
    private TextView tvDate;
    private TextView tvSignature;
    private ImageView ivDesktopBg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentTheme;

    private String cityName = "未知";
    private String weatherDesc = "多云";
    private String temperature = "26°C";

    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateTime();
            handler.postDelayed(this, 1000); // 每秒更新一次
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        currentTheme = ThemeManager.getTheme(this);
        super.onCreate(savedInstanceState);
        
        // 检查是否显示过引导页，如果没有则跳转并结束当前 Activity
        if (!com.yoyo.jingxi.utils.SpUtils.getBoolean("HAS_SHOWN_GUIDE", false)) {
            startActivity(new Intent(this, GuideActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_desktop);
        ThemeManager.applyDesktopThemeBackground(this);

        tvTime = findViewById(R.id.tvTime);
        tvDate = findViewById(R.id.tvDate);
        tvSignature = findViewById(R.id.tvSignature);
        ivDesktopBg = findViewById(R.id.ivDesktopBg);

        String savedSignature = com.yoyo.jingxi.utils.SpUtils.getString("desktop_signature", "点这里写字");
        tvSignature.setText(savedSignature);

        updateTime();
        setupClickListeners();
        checkLocationPermissionAndRefreshWeather();
        checkNotificationPermission();
        checkAndShowAnnouncement();
    }

    private void checkAndShowAnnouncement() {
        String hideDate = com.yoyo.jingxi.utils.SpUtils.getString("announcement_hide_date", "");
        int hideVersion = com.yoyo.jingxi.utils.SpUtils.getInt("announcement_hide_version", -1);
        
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        int currentVersion = -1;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (today.equals(hideDate)) {
            return;
        }
        if (currentVersion != -1 && currentVersion <= hideVersion) {
            return;
        }

        showAnnouncementDialog();
    }

    private void showAnnouncementDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_announcement, null);
        builder.setView(view);
        builder.setCancelable(false);
        
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        android.widget.TextView tvContent = view.findViewById(R.id.tvAnnouncementContent);
        // The CDATA block in strings.xml preserves actual newlines, so we don't need to replace \n manually
        // unless it's escaped. Let's make sure it displays correctly by just passing the string directly.
        String markdownText = getString(R.string.announcement_content);
        io.noties.markwon.Markwon markwon = io.noties.markwon.Markwon.create(this);
        markwon.setMarkdown(tvContent, markdownText);
        
        android.widget.RadioGroup rgOptions = view.findViewById(R.id.rgAnnouncementOptions);
        android.widget.Button btnConfirm = view.findViewById(R.id.btnAnnouncementConfirm);
        
        // 15s countdown
        new android.os.CountDownTimer(15000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnConfirm.setText(getString(R.string.announcement_confirm_wait, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                btnConfirm.setEnabled(true);
                btnConfirm.setText(R.string.announcement_confirm_ready);
            }
        }.start();

        btnConfirm.setOnClickListener(v -> {
            int checkedId = rgOptions.getCheckedRadioButtonId();
            if (checkedId == R.id.rbHideToday) {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                com.yoyo.jingxi.utils.SpUtils.putString("announcement_hide_date", today);
                com.yoyo.jingxi.utils.SpUtils.putInt("announcement_hide_version", -1);
            } else if (checkedId == R.id.rbHideUntilUpdate) {
                try {
                    android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    com.yoyo.jingxi.utils.SpUtils.putInt("announcement_hide_version", pInfo.versionCode);
                    com.yoyo.jingxi.utils.SpUtils.putString("announcement_hide_date", "");
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                com.yoyo.jingxi.utils.SpUtils.putString("announcement_hide_date", "");
                com.yoyo.jingxi.utils.SpUtils.putInt("announcement_hide_version", -1);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTheme != ThemeManager.getTheme(this)) {
            recreate();
            return;
        }

        handler.post(timeUpdater);
        
        // 加载背景
        String bgPath = ThemeManager.getBgImagePath(this);
        if (bgPath != null && !bgPath.isEmpty()) {
            ivDesktopBg.setVisibility(View.VISIBLE);
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(this.getApplicationContext()).load(bgPath).into(ivDesktopBg);
            }
            if (ThemeManager.isDarkMode(this)) {
                ivDesktopBg.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else {
                ivDesktopBg.clearColorFilter();
            }
        } else {
            ivDesktopBg.setVisibility(View.GONE);
        }

        // 加载自定义照片
        String photo1Path = ThemeManager.getDesktopPhoto1Path(this);
        if (photo1Path != null && !photo1Path.isEmpty()) {
            ImageView ivCustomPhoto = findViewById(R.id.ivCustomPhoto);
            if (ivCustomPhoto != null) {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this.getApplicationContext()).load(photo1Path).into(ivCustomPhoto);
                }
                if (ThemeManager.isDarkMode(this)) {
                    ivCustomPhoto.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                } else {
                    ivCustomPhoto.clearColorFilter();
                }
            }
        }

        String photo2Path = ThemeManager.getDesktopPhoto2Path(this);
        if (photo2Path != null && !photo2Path.isEmpty()) {
            ImageView ivCustomPhoto2 = findViewById(R.id.ivCustomPhoto2);
            if (ivCustomPhoto2 != null) {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this.getApplicationContext()).load(photo2Path).into(ivCustomPhoto2);
                }
                if (ThemeManager.isDarkMode(this)) {
                    ivCustomPhoto2.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                } else {
                    ivCustomPhoto2.clearColorFilter();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(timeUpdater);
    }

    private void updateTime() {
        long now = System.currentTimeMillis();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault());
        
        tvTime.setText(timeFormat.format(new Date(now)));
        tvDate.setText(dateFormat.format(new Date(now)));
    }

    private void setupClickListeners() {
        // Weather card
        findViewById(R.id.cardWeather).setOnClickListener(v -> {
            Intent intent = new Intent(DesktopActivity.this, WeatherActivity.class);
            intent.putExtra("cityName", cityName);
            intent.putExtra("weatherDesc", weatherDesc);
            intent.putExtra("temperature", temperature);
            startActivity(intent);
        });

        // App 区
        findViewById(R.id.btnChat).setOnClickListener(v -> startActivity(new Intent(this, ChatMainActivity.class)));
        findViewById(R.id.btnWorldbook).setOnClickListener(v -> startActivity(new Intent(this, WorldbookActivity.class)));
        findViewById(R.id.btnSchedule).setOnClickListener(v -> startActivity(new Intent(this, ScheduleActivity.class)));
        findViewById(R.id.btnMemory).setOnClickListener(v -> startActivity(new Intent(this, MemoryActivity.class)));

        // Dock 区
        findViewById(R.id.btnCall).setOnClickListener(v -> startActivity(new Intent(this, CallHistoryActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnTheme).setOnClickListener(v -> startActivity(new Intent(this, ThemeSettingsActivity.class)));

        // 签名
        findViewById(R.id.layoutSignature).setOnClickListener(v -> showEditSignatureDialog());

        // 照片卡片
        findViewById(R.id.cardPhoto).setOnClickListener(v -> startPhotoPicker(1));
        findViewById(R.id.cardPhoto2).setOnClickListener(v -> startPhotoPicker(2));
    }

    private int currentPhotoTarget = 0;
    
    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPhotoLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        startCrop(imageUri);
                    }
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<Intent> cropPhotoLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        String path = resultUri.toString();
                        if (currentPhotoTarget == 1) {
                            ThemeManager.setDesktopPhoto1Path(this.getApplicationContext(), path);
                            ImageView ivCustomPhoto = findViewById(R.id.ivCustomPhoto);
                            if (ivCustomPhoto != null) {
                                if (!isFinishing() && !isDestroyed()) {
                                    Glide.with(this.getApplicationContext()).load(path).into(ivCustomPhoto);
                                }
                                if (ThemeManager.isDarkMode(this)) {
                                    ivCustomPhoto.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                                } else {
                                    ivCustomPhoto.clearColorFilter();
                                }
                            }
                        } else if (currentPhotoTarget == 2) {
                            ThemeManager.setDesktopPhoto2Path(this.getApplicationContext(), path);
                            ImageView ivCustomPhoto2 = findViewById(R.id.ivCustomPhoto2);
                            if (ivCustomPhoto2 != null) {
                                if (!isFinishing() && !isDestroyed()) {
                                    Glide.with(this.getApplicationContext()).load(path).into(ivCustomPhoto2);
                                }
                                if (ThemeManager.isDarkMode(this)) {
                                    ivCustomPhoto2.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                                } else {
                                    ivCustomPhoto2.clearColorFilter();
                                }
                            }
                        }
                    }
                } else if (result.getResultCode() == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                    Throwable cropError = com.yalantis.ucrop.UCrop.getError(result.getData());
                    if (cropError != null) {
                        android.widget.Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private void startPhotoPicker(int target) {
        currentPhotoTarget = target;
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickPhotoLauncher.launch(intent);
    }

    private void startCrop(android.net.Uri sourceUri) {
        String destinationFileName = "cropped_desktop_photo_" + System.currentTimeMillis() + ".jpg";
        android.net.Uri destinationUri = android.net.Uri.fromFile(new java.io.File(getCacheDir(), destinationFileName));

        com.yalantis.ucrop.UCrop uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri);
        
        // 照片卡片通常是方形或特定比例
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(800, 800);

        Intent intent = uCrop.getIntent(this);
        cropPhotoLauncher.launch(intent);
    }

    private void showEditSignatureDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑签名");
        
        final EditText input = new EditText(this);
        input.setText(tvSignature.getText().toString().equals("点这里写字") ? "" : tvSignature.getText().toString());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newSig = input.getText().toString();
            if (newSig.isEmpty()) {
                tvSignature.setText("点这里写字");
                com.yoyo.jingxi.utils.SpUtils.putString("desktop_signature", "点这里写字");
            } else {
                tvSignature.setText(newSig);
                com.yoyo.jingxi.utils.SpUtils.putString("desktop_signature", newSig);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void checkLocationPermissionAndRefreshWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLocationAndRefreshWeather();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocationAndRefreshWeather();
            } else {
                refreshWeather(39.9042, 116.4074); // Default to Beijing
            }
        }
    }

    private void getLocationAndRefreshWeather() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            refreshWeather(39.9042, 116.4074);
            return;
        }

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (location != null) {
                refreshWeather(location.getLatitude(), location.getLongitude());
            } else {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        refreshWeather(loc.getLatitude(), loc.getLongitude());
                        locationManager.removeUpdates(this);
                    }
                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override
                    public void onProviderEnabled(String provider) {}
                    @Override
                    public void onProviderDisabled(String provider) {}
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            refreshWeather(39.9042, 116.4074);
        } catch (Exception e) {
            refreshWeather(39.9042, 116.4074);
        }
    }

    private void refreshWeather(double lat, double lon) {
        String amapKey = com.yoyo.jingxi.utils.SpUtils.getString("AMAP_WEATHER_KEY", "");
        if (android.text.TextUtils.isEmpty(amapKey)) {
            fetchOpenMeteoWeather(lat, lon);
        } else {
            fetchAmapWeather(lat, lon, amapKey);
        }
    }
    
    private void updateWeatherUI() {
        runOnUiThread(() -> {
            TextView tvWeather = findViewById(R.id.tvWeatherDesc);
            TextView tvTemp = findViewById(R.id.tvWeatherTemp);
            if (tvWeather != null) {
                tvWeather.setText(weatherDesc);
            }
            if (tvTemp != null) {
                tvTemp.setText(temperature);
            }
        });
    }

    private void fetchOpenMeteoWeather(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                cityName = address.getLocality();
                if (cityName == null) cityName = address.getAdminArea();
                if (cityName == null) cityName = "未知";
                if (cityName.endsWith("市")) cityName = cityName.substring(0, cityName.length() - 1);
            }
        } catch (Exception e) {
            // fallback
        }

        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
        com.yoyo.jingxi.network.OpenMeteoApi api = retrofit.create(com.yoyo.jingxi.network.OpenMeteoApi.class);
        
        api.getWeather(lat, lon, true, "Asia/Shanghai").enqueue(new retrofit2.Callback<com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse> call, retrofit2.Response<com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().current_weather != null) {
                    int code = response.body().current_weather.weathercode;
                    weatherDesc = parseOpenMeteoCode(code);
                    temperature = (int)response.body().current_weather.temperature + "°C";
                    updateWeatherUI();
                }
            }
            @Override
            public void onFailure(retrofit2.Call<com.yoyo.jingxi.network.OpenMeteoApi.OpenMeteoResponse> call, Throwable t) {}
        });
    }

    private String parseOpenMeteoCode(int code) {
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code <= 49) return "雾/霾";
        if (code <= 69) return "雨";
        if (code <= 79) return "雪";
        if (code <= 99) return "雷阵雨";
        return "未知";
    }

    private void fetchAmapWeather(double lat, double lon, String amapKey) {
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://restapi.amap.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
        com.yoyo.jingxi.network.AmapWeatherApi api = retrofit.create(com.yoyo.jingxi.network.AmapWeatherApi.class);
        
        String location = lon + "," + lat;
        api.getGeocode(location, amapKey).enqueue(new retrofit2.Callback<com.yoyo.jingxi.network.AmapWeatherApi.AmapGeocodeResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.yoyo.jingxi.network.AmapWeatherApi.AmapGeocodeResponse> call, retrofit2.Response<com.yoyo.jingxi.network.AmapWeatherApi.AmapGeocodeResponse> response) {
                if (response.isSuccessful() && response.body() != null && "1".equals(response.body().status)) {
                    if (response.body().regeocode != null && response.body().regeocode.addressComponent != null) {
                        String adcode = response.body().regeocode.addressComponent.adcode;
                        api.getWeather(adcode, amapKey, "base").enqueue(new retrofit2.Callback<com.yoyo.jingxi.network.AmapWeatherApi.AmapWeatherResponse>() {
                            @Override
                            public void onResponse(retrofit2.Call<com.yoyo.jingxi.network.AmapWeatherApi.AmapWeatherResponse> call, retrofit2.Response<com.yoyo.jingxi.network.AmapWeatherApi.AmapWeatherResponse> weatherResponse) {
                                if (weatherResponse.isSuccessful() && weatherResponse.body() != null && "1".equals(weatherResponse.body().status)) {
                                    if (weatherResponse.body().lives != null && !weatherResponse.body().lives.isEmpty()) {
                                        com.yoyo.jingxi.network.AmapWeatherApi.AmapWeatherResponse.Lives live = weatherResponse.body().lives.get(0);
                                        cityName = live.city;
                                        if (cityName != null && cityName.endsWith("市")) {
                                            cityName = cityName.substring(0, cityName.length() - 1);
                                        }
                                        weatherDesc = live.weather;
                                        temperature = live.temperature + "°C";
                                        updateWeatherUI();
                                    }
                                }
                            }
                            @Override
                            public void onFailure(retrofit2.Call<com.yoyo.jingxi.network.AmapWeatherApi.AmapWeatherResponse> call, Throwable t) {}
                        });
                    }
                }
            }
            @Override
            public void onFailure(retrofit2.Call<com.yoyo.jingxi.network.AmapWeatherApi.AmapGeocodeResponse> call, Throwable t) {}
        });
    }
}
