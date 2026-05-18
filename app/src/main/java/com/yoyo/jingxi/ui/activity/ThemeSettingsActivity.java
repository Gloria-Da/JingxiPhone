package com.yoyo.jingxi.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.ThemeManager;

public class ThemeSettingsActivity extends AppCompatActivity {

    private ImageView ivBgPreview;
    private ImageView ivGlobalBgPreview;
    private String currentBgPath;
    private String currentGlobalBgPath;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        startCrop(imageUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cropImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        currentBgPath = resultUri.toString();
                        ThemeManager.setBgImagePath(this, currentBgPath);
                        if (!isFinishing() && !isDestroyed()) {
                            Glide.with(ThemeSettingsActivity.this.getApplicationContext()).load(currentBgPath).into(ivBgPreview);
                        }
                        Toast.makeText(this, "背景图片已更新", Toast.LENGTH_SHORT).show();
                        ThemeManager.applyGlobalBackground(this);
                        loadCurrentGlobalBg();
                    }
                } else if (result.getResultCode() == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                    Throwable cropError = com.yalantis.ucrop.UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> pickGlobalImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        startCropGlobal(imageUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cropGlobalImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        currentGlobalBgPath = resultUri.toString();
                        ThemeManager.setGlobalBgImagePath(this, currentGlobalBgPath);
                        loadCurrentGlobalBg();
                        ThemeManager.applyGlobalBackground(this);
                        Toast.makeText(this, "功能背景已更新", Toast.LENGTH_SHORT).show();
                    }
                } else if (result.getResultCode() == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                    Throwable cropError = com.yalantis.ucrop.UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private void startCrop(Uri sourceUri) {
        // 创建一个用于保存裁剪后图片的临时文件 URI
        String destinationFileName = "cropped_bg_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new java.io.File(getCacheDir(), destinationFileName));

        com.yalantis.ucrop.UCrop uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri);
        
        // 桌面背景通常是屏幕比例，比如 9:16
        uCrop.withAspectRatio(9, 16);
        uCrop.withMaxResultSize(1080, 1920);

        Intent intent = uCrop.getIntent(this);
        cropImageLauncher.launch(intent);
    }

    private void startCropGlobal(Uri sourceUri) {
        String destinationFileName = "cropped_global_bg_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new java.io.File(getCacheDir(), destinationFileName));

        com.yalantis.ucrop.UCrop uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri);
        
        uCrop.withAspectRatio(9, 16);
        uCrop.withMaxResultSize(1080, 1920);

        Intent intent = uCrop.getIntent(this);
        cropGlobalImageLauncher.launch(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RadioGroup rgThemes = findViewById(R.id.rgThemes);
        RadioGroup rgNightMode = findViewById(R.id.rgNightMode);
        ivBgPreview = findViewById(R.id.ivBgPreview);
        ivGlobalBgPreview = findViewById(R.id.ivGlobalBgPreview);
        Button btnSelectBg = findViewById(R.id.btnSelectBg);
        Button btnClearBg = findViewById(R.id.btnClearBg);
        Button btnSelectGlobalBg = findViewById(R.id.btnSelectGlobalBg);
        Button btnClearGlobalBg = findViewById(R.id.btnClearGlobalBg);

        // 初始化深色模式单选组
        int nightMode = ThemeManager.getNightMode(this);
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            rgNightMode.check(R.id.rbNightYes);
        } else if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            rgNightMode.check(R.id.rbNightNo);
        } else {
            rgNightMode.check(R.id.rbNightFollowSystem);
        }

        rgNightMode.setOnCheckedChangeListener((group, checkedId) -> {
            int newNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.rbNightYes) {
                newNightMode = AppCompatDelegate.MODE_NIGHT_YES;
            } else if (checkedId == R.id.rbNightNo) {
                newNightMode = AppCompatDelegate.MODE_NIGHT_NO;
            }
            
            ThemeManager.setNightMode(this, newNightMode);
            AppCompatDelegate.setDefaultNightMode(newNightMode);
        });

        // 初始化当前主题选中状态
        int currentTheme = ThemeManager.getTheme(this);
        switch (currentTheme) {
            case ThemeManager.THEME_YELLOW:
                rgThemes.check(R.id.rbThemeYellow);
                break;
            case ThemeManager.THEME_PINK:
                rgThemes.check(R.id.rbThemePink);
                break;
            case ThemeManager.THEME_BLUE:
                rgThemes.check(R.id.rbThemeBlue);
                break;
            case ThemeManager.THEME_WHITE:
                rgThemes.check(R.id.rbThemeWhite);
                break;
        }

        // 监听主题切换
        rgThemes.setOnCheckedChangeListener((group, checkedId) -> {
            int newTheme = ThemeManager.THEME_YELLOW;
            if (checkedId == R.id.rbThemeYellow) newTheme = ThemeManager.THEME_YELLOW;
            else if (checkedId == R.id.rbThemePink) newTheme = ThemeManager.THEME_PINK;
            else if (checkedId == R.id.rbThemeBlue) newTheme = ThemeManager.THEME_BLUE;
            else if (checkedId == R.id.rbThemeWhite) newTheme = ThemeManager.THEME_WHITE;

            if (newTheme != currentTheme) {
                ThemeManager.setTheme(this, newTheme);
                // 提示用户需要回到桌面生效，因为现在使用 DesktopActivity
                Toast.makeText(this, "主题已更改，返回桌面生效", Toast.LENGTH_SHORT).show();
            }
        });

        // 初始化背景预览
        currentBgPath = ThemeManager.getBgImagePath(this);
        if (currentBgPath != null) {
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(ThemeSettingsActivity.this.getApplicationContext()).load(currentBgPath).into(ivBgPreview);
            }
        }

        btnSelectBg.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnClearBg.setOnClickListener(v -> {
            ThemeManager.setBgImagePath(this, null);
            currentBgPath = null;
            ivBgPreview.setImageDrawable(null);
            ThemeManager.applyGlobalBackground(this);
            loadCurrentGlobalBg();
            Toast.makeText(this, "背景已清除", Toast.LENGTH_SHORT).show();
        });

        loadCurrentGlobalBg();

        btnSelectGlobalBg.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickGlobalImageLauncher.launch(intent);
        });

        btnClearGlobalBg.setOnClickListener(v -> {
            ThemeManager.setGlobalBgImagePath(this, null);
            currentGlobalBgPath = null;
            loadCurrentGlobalBg();
            ThemeManager.applyGlobalBackground(this);
            Toast.makeText(this, "功能背景已恢复跟随桌面", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadCurrentGlobalBg() {
        currentGlobalBgPath = ThemeManager.getGlobalBgImagePath(this);
        if (currentGlobalBgPath != null && !currentGlobalBgPath.isEmpty()) {
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(ThemeSettingsActivity.this.getApplicationContext()).load(currentGlobalBgPath).into(ivGlobalBgPreview);
                ivGlobalBgPreview.clearColorFilter();
            }
        } else {
            ivGlobalBgPreview.setImageDrawable(null);
            
            String desktopBgPath = ThemeManager.getBgImagePath(this);
            if (desktopBgPath != null && !desktopBgPath.isEmpty()) {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(ThemeSettingsActivity.this.getApplicationContext()).load(desktopBgPath).into(ivGlobalBgPreview);
                    ivGlobalBgPreview.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                }
            } else {
                ivGlobalBgPreview.setBackgroundColor(getResources().getColor(ThemeManager.isDarkMode(this) ? R.color.theme_dark_bg : R.color.colorBackground));
            }
        }
    }
}
