package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ImageView;
import android.net.Uri;
import android.content.Intent;
import android.app.Activity;
import android.util.Log;
import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.bumptech.glide.Glide;
import com.yalantis.ucrop.UCrop;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;

import java.util.concurrent.Executors;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

public class AddFriendActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private TextInputEditText etGender;
    private TextInputEditText etPersona;
    private TextInputEditText etAvatarUrl;
    private TextInputEditText etVoiceId;
    private android.widget.EditText etVoicePitch;
    private com.google.android.material.slider.Slider sliderVoicePitch;
    private android.widget.EditText etVoiceIntensity;
    private com.google.android.material.slider.Slider sliderVoiceIntensity;
    private android.widget.EditText etVoiceTimbre;
    private com.google.android.material.slider.Slider sliderVoiceTimbre;
    private android.widget.AutoCompleteTextView etSoundEffect;
    private ImageView ivAvatar;
    private Button btnSave;
    
    private android.widget.Switch switchAutoMoment;
    private android.widget.EditText etAutoMomentInterval;
    private android.widget.EditText etAutoMomentStartTime;
    private android.widget.EditText etAutoMomentEndTime;

    private boolean isEditMode = false;
    private int characterId = -1;
    
    private String currentAvatarUri = "";

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri sourceUri = result.getData().getData();
                    if (sourceUri != null) {
                        startCrop(sourceUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        currentAvatarUri = resultUri.toString();
                        etAvatarUrl.setText(currentAvatarUri); // keep the hidden field updated
                        if (!isFinishing() && !isDestroyed()) {
                            Glide.with(AddFriendActivity.this.getApplicationContext())
                                 .load(currentAvatarUri)
                                 .circleCrop()
                                 .placeholder(R.drawable.ic_launcher_round)
                                 .into(ivAvatar);
                        }
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                    Throwable cropError = UCrop.getError(result.getData());
                    Log.e("AddFriendActivity", "Crop error", cropError);
                    Toast.makeText(this, "图片裁剪失败", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void startCrop(Uri sourceUri) {
        String destinationFileName = "avatar_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destinationFileName));

        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(500, 500);

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        uCrop.withOptions(options);

        cropLauncher.launch(uCrop.getIntent(this));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.etName);
        etGender = findViewById(R.id.etGender);
        etPersona = findViewById(R.id.etPersona);
        etAvatarUrl = findViewById(R.id.etAvatarUrl);
        etVoiceId = findViewById(R.id.etVoiceId);
        etVoicePitch = findViewById(R.id.etVoicePitch);
        sliderVoicePitch = findViewById(R.id.sliderVoicePitch);
        etVoiceIntensity = findViewById(R.id.etVoiceIntensity);
        sliderVoiceIntensity = findViewById(R.id.sliderVoiceIntensity);
        etVoiceTimbre = findViewById(R.id.etVoiceTimbre);
        sliderVoiceTimbre = findViewById(R.id.sliderVoiceTimbre);
        etSoundEffect = findViewById(R.id.etSoundEffect);
        ivAvatar = findViewById(R.id.ivAvatar);
        btnSave = findViewById(R.id.btnSave);
        
        switchAutoMoment = findViewById(R.id.switchAutoMoment);
        etAutoMomentInterval = findViewById(R.id.etAutoMomentInterval);
        etAutoMomentStartTime = findViewById(R.id.etAutoMomentStartTime);
        etAutoMomentEndTime = findViewById(R.id.etAutoMomentEndTime);
        
        etAutoMomentStartTime.setOnClickListener(v -> showTimePickerDialog(etAutoMomentStartTime));
        etAutoMomentEndTime.setOnClickListener(v -> showTimePickerDialog(etAutoMomentEndTime));

        // 设置 Slider 监听以更新 EditText
        sliderVoicePitch.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) etVoicePitch.setText(String.valueOf((int)value));
        });
        sliderVoiceIntensity.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) etVoiceIntensity.setText(String.valueOf((int)value));
        });
        sliderVoiceTimbre.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) etVoiceTimbre.setText(String.valueOf((int)value));
        });
        
        // 设置 EditText 监听以更新 Slider
        setupEditTextToSliderLink(etVoicePitch, sliderVoicePitch, true);
        setupEditTextToSliderLink(etVoiceIntensity, sliderVoiceIntensity, true);
        setupEditTextToSliderLink(etVoiceTimbre, sliderVoiceTimbre, true);

        // 设置音效下拉菜单
        String[] soundEffects = {"无", "空旷回音 (spacious_echo)", "礼堂广播 (auditorium_echo)", "电话失真 (lofi_telephone)", "电音 (robotic)"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, soundEffects);
        etSoundEffect.setAdapter(adapter);

        ivAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        if (getIntent().hasExtra("character_id")) {
            isEditMode = true;
            characterId = getIntent().getIntExtra("character_id", -1);
            toolbar.setTitle("编辑 AI 角色");
            loadCharacterData(characterId);
        } else {
            toolbar.setTitle("新建 AI 角色");
        }

        btnSave.setOnClickListener(v -> saveCharacter());
    }

    private void showTimePickerDialog(android.widget.EditText editText) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = calendar.get(java.util.Calendar.MINUTE);
        
        String currentTime = editText.getText().toString();
        if (!currentTime.isEmpty() && currentTime.contains(":")) {
            String[] parts = currentTime.split(":");
            try {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Ignore and use current time
            }
        }

        new android.app.TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            String time = String.format("%02d:%02d", hourOfDay, minuteOfHour);
            editText.setText(time);
        }, hour, minute, true).show();
    }

    private void setupEditTextToSliderLink(android.widget.EditText editText, com.google.android.material.slider.Slider slider, boolean isInteger) {
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!s.toString().isEmpty() && editText.hasFocus()) {
                    try {
                        float val = Float.parseFloat(s.toString());
                        float min = slider.getValueFrom();
                        float max = slider.getValueTo();
                        
                        if (val >= min && val <= max) {
                            slider.setValue(val);
                        } else if (val > max) {
                            slider.setValue(max);
                            String maxStr = isInteger ? String.valueOf((int)max) : String.format("%.1f", max);
                            editText.setText(maxStr);
                            editText.setSelection(maxStr.length());
                        } else if (val < min) {
                            slider.setValue(min);
                            String minStr = isInteger ? String.valueOf((int)min) : String.format("%.1f", min);
                            editText.setText(minStr);
                            editText.setSelection(minStr.length());
                        }
                    } catch (NumberFormatException e) {
                        // ignore invalid input
                    }
                }
            }
        });
    }

    private void loadCharacterData(int id) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Character character = AppDatabase.getDatabase(this).characterDao().getCharacterById(id);
                    if (character != null) {
                        runOnUiThread(() -> {
                            etName.setText(character.name);
                            
                            // 尝试从 persona 中提取性别并显示（简单的实现方式：如果以 "[性别: " 开头）
                            String loadedPersona = character.persona;
                            if (loadedPersona != null && loadedPersona.startsWith("[性别: ")) {
                                int endIdx = loadedPersona.indexOf("] ");
                                if (endIdx != -1) {
                                    String genderStr = loadedPersona.substring(5, endIdx);
                                    etGender.setText(genderStr);
                                    loadedPersona = loadedPersona.substring(endIdx + 2); // 移除前缀
                                }
                            }
                            etPersona.setText(loadedPersona);
                            etAvatarUrl.setText(character.avatarPath);
                            etVoiceId.setText(character.voiceId);
                            
                            sliderVoicePitch.setValue(character.voicePitch);
                            etVoicePitch.setText(String.valueOf(character.voicePitch));
                            sliderVoiceIntensity.setValue(character.voiceIntensity);
                            etVoiceIntensity.setText(String.valueOf(character.voiceIntensity));
                            sliderVoiceTimbre.setValue(character.voiceTimbre);
                            etVoiceTimbre.setText(String.valueOf(character.voiceTimbre));
                            
                            boolean isAutoMomentEnabled = com.yoyo.jingxi.utils.SpUtils.getBoolean("AUTO_MOMENT_ENABLED_" + character.id, false);
                            switchAutoMoment.setChecked(isAutoMomentEnabled);
                            etAutoMomentInterval.setText(String.valueOf(character.autoMomentIntervalHours));
                            etAutoMomentStartTime.setText(character.autoMomentStartTime);
                            etAutoMomentEndTime.setText(character.autoMomentEndTime);

                            if (character.soundEffect != null && !character.soundEffect.isEmpty()) {
                                String displayEffect = "无";
                                switch (character.soundEffect) {
                                    case "spacious_echo": displayEffect = "空旷回音 (spacious_echo)"; break;
                                    case "auditorium_echo": displayEffect = "礼堂广播 (auditorium_echo)"; break;
                                    case "lofi_telephone": displayEffect = "电话失真 (lofi_telephone)"; break;
                                    case "robotic": displayEffect = "电音 (robotic)"; break;
                                    default: displayEffect = character.soundEffect;
                                }
                                etSoundEffect.setText(displayEffect, false);
                            } else {
                                etSoundEffect.setText("无", false);
                            }
                            
                            currentAvatarUri = character.avatarPath;
                            
                            if (character.avatarPath != null && !character.avatarPath.isEmpty()) {
                                if (!isFinishing() && !isDestroyed()) {
                                    Glide.with(AddFriendActivity.this.getApplicationContext())
                                         .load(character.avatarPath)
                                         .circleCrop()
                                         .placeholder(R.drawable.ic_launcher_round)
                                         .into(ivAvatar);
                                }
                            }
                        });
                    }
        });
    }

    private void saveCharacter() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String gender = etGender.getText() != null ? etGender.getText().toString().trim() : "";
        String persona = etPersona.getText() != null ? etPersona.getText().toString().trim() : "";
        String avatarUrl = etAvatarUrl.getText() != null ? etAvatarUrl.getText().toString().trim() : "";
        String voiceId = etVoiceId.getText() != null ? etVoiceId.getText().toString().trim() : "";
        int pitch = (int) sliderVoicePitch.getValue();
        int intensity = (int) sliderVoiceIntensity.getValue();
        int timbre = (int) sliderVoiceTimbre.getValue();
        
        float autoMomentInterval = 8.0f;
        try {
            autoMomentInterval = Float.parseFloat(etAutoMomentInterval.getText().toString());
        } catch (NumberFormatException ignored) {}
        String autoMomentStartTime = etAutoMomentStartTime.getText().toString();
        String autoMomentEndTime = etAutoMomentEndTime.getText().toString();
        boolean autoMomentEnabled = switchAutoMoment.isChecked();
        
        String displayEffect = etSoundEffect.getText().toString().trim();
        String soundEffect = null;
        if (displayEffect.contains("spacious_echo")) soundEffect = "spacious_echo";
        else if (displayEffect.contains("auditorium_echo")) soundEffect = "auditorium_echo";
        else if (displayEffect.contains("lofi_telephone")) soundEffect = "lofi_telephone";
        else if (displayEffect.contains("robotic")) soundEffect = "robotic";
        else if (!displayEffect.equals("无")) soundEffect = displayEffect;

        if (name.isEmpty() || persona.isEmpty()) {
            Toast.makeText(this, "姓名和人设不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 将性别拼接到人设前面
        String finalPersona = persona;
        if (!gender.isEmpty()) {
            finalPersona = "[性别: " + gender + "] " + persona;
        }

        Character character = new Character();
        character.name = name;
        character.persona = finalPersona;
        // 优先使用当前选中的本地/裁剪的图片URI，如果没有则使用输入框内容
        character.avatarPath = currentAvatarUri.isEmpty() ? avatarUrl : currentAvatarUri;
        character.voiceId = voiceId;
        character.voicePitch = pitch;
        character.voiceIntensity = intensity;
        character.voiceTimbre = timbre;
        character.soundEffect = soundEffect;
        character.autoMomentIntervalHours = autoMomentInterval;
        character.autoMomentStartTime = autoMomentStartTime;
        character.autoMomentEndTime = autoMomentEndTime;
        
        Executors.newSingleThreadExecutor().execute(() -> {
            long id = character.id;
            if (isEditMode) {
                character.id = characterId;
                AppDatabase.getDatabase(this).characterDao().update(character);
                id = characterId;
            } else {
                id = AppDatabase.getDatabase(this).characterDao().insert(character);
            }
            com.yoyo.jingxi.utils.SpUtils.putBoolean("AUTO_MOMENT_ENABLED_" + id, autoMomentEnabled);
            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}