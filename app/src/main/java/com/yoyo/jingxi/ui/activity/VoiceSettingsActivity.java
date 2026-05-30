package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.SpUtils;

public class VoiceSettingsActivity extends AppCompatActivity {

    private TextInputEditText etMinimaxGroupId;
    private TextInputEditText etMinimaxApiKey;
    private Spinner spinnerMinimaxModel;
    private SwitchCompat switchMinimaxToneTags;
    private SwitchCompat switchMinimaxEmotion;

    private SeekBar seekBarVolume;
    private EditText etVolumeValue;
    private SeekBar seekBarPitch;
    private EditText etPitchValue;

    private TextInputEditText etSttBaseUrl;
    private TextInputEditText etSttApiKey;
    private TextInputEditText etSttModel;

    private Button btnSave;

    // 默认模型列表
    private final String[] minimaxModels = {
            "speech-01-turbo",
            "speech-01-hd",
            "speech-2.8-hd",
            "speech-2.8-turbo",
            "speech-2.6-hd",
            "speech-2.6-turbo",
            "speech-02-hd",
            "speech-02-turbo"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);

        initViews();
        setupToolbar();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        etMinimaxGroupId = findViewById(R.id.etMinimaxGroupId);
        etMinimaxApiKey = findViewById(R.id.etMinimaxApiKey);
        spinnerMinimaxModel = findViewById(R.id.spinnerMinimaxModel);
        switchMinimaxToneTags = findViewById(R.id.switchMinimaxToneTags);
        switchMinimaxEmotion = findViewById(R.id.switchMinimaxEmotion);

        seekBarVolume = findViewById(R.id.seekBarVolume);
        etVolumeValue = findViewById(R.id.etVolumeValue);
        seekBarPitch = findViewById(R.id.seekBarPitch);
        etPitchValue = findViewById(R.id.etPitchValue);

        etSttBaseUrl = findViewById(R.id.etSttBaseUrl);
        etSttApiKey = findViewById(R.id.etSttApiKey);
        etSttModel = findViewById(R.id.etSttModel);

        btnSave = findViewById(R.id.btnSave);

        // 设置Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, minimaxModels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMinimaxModel.setAdapter(adapter);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("语音设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        // Volume 监听 (范围: 0.1 到 10.0, 默认1.0) -> Progress 1 到 100
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress == 0) {
                        seekBar.setProgress(1);
                        progress = 1;
                    }
                    float volume = progress / 10f;
                    etVolumeValue.setText(String.format("%.1f", volume));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pitch 监听 (范围: -12 到 12, 默认0) -> Progress 0 到 24 -> 实际进度 = progress - 12
        seekBarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int pitch = progress - 12;
                    etPitchValue.setText(String.valueOf(pitch));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 输入框监听以同步更新 SeekBar
        etVolumeValue.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float val = Float.parseFloat(s.toString());
                    if (val > 0 && val <= 10.0f) {
                        seekBarVolume.setProgress((int)(val * 10));
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        etPitchValue.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int val = Integer.parseInt(s.toString());
                    if (val >= -12 && val <= 12) {
                        seekBarPitch.setProgress(val + 12);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        // MiniMax
        etMinimaxGroupId.setText(SpUtils.getString("minimax_group_id", ""));
        etMinimaxApiKey.setText(SpUtils.getString("minimax_api_key", ""));
        switchMinimaxToneTags.setChecked(SpUtils.getBoolean("minimax_tone_tags", false));
        switchMinimaxEmotion.setChecked(SpUtils.getBoolean("minimax_emotion", false));

        String savedModel = SpUtils.getString("minimax_model", "speech-01-turbo");
        for (int i = 0; i < minimaxModels.length; i++) {
            if (minimaxModels[i].equals(savedModel)) {
                spinnerMinimaxModel.setSelection(i);
                break;
            }
        }

        // Voice Parameters
        float vol = SpUtils.getFloat("minimax_vol", 1.0f);
        etVolumeValue.setText(String.format("%.1f", vol));
        seekBarVolume.setProgress((int)(vol * 10));

        int pitch = SpUtils.getInt("minimax_pitch", 0);
        etPitchValue.setText(String.valueOf(pitch));
        seekBarPitch.setProgress(pitch + 12);

        // STT
        etSttBaseUrl.setText(SpUtils.getString("stt_base_url", "https://api.siliconflow.cn/"));
        etSttApiKey.setText(SpUtils.getString("stt_api_key", ""));
        etSttModel.setText(SpUtils.getString("stt_model", "FunAudioLLM/SenseVoiceSmall"));
    }

    private void saveSettings() {
        SpUtils.putString("minimax_base_url", "https://api.minimax.chat/");
        SpUtils.putString("minimax_group_id", etMinimaxGroupId.getText().toString().trim());
        SpUtils.putString("minimax_api_key", etMinimaxApiKey.getText().toString().trim());
        SpUtils.putString("minimax_model", spinnerMinimaxModel.getSelectedItem().toString());
        SpUtils.putBoolean("minimax_tone_tags", switchMinimaxToneTags.isChecked());
        SpUtils.putBoolean("minimax_emotion", switchMinimaxEmotion.isChecked());

        try {
            float vol = Float.parseFloat(etVolumeValue.getText().toString().trim());
            if(vol <= 0 || vol > 10.0f) vol = 1.0f;
            SpUtils.putFloat("minimax_vol", vol);
        } catch (NumberFormatException e) {
            SpUtils.putFloat("minimax_vol", 1.0f);
        }

        try {
            int pitch = Integer.parseInt(etPitchValue.getText().toString().trim());
            if(pitch < -12 || pitch > 12) pitch = 0;
            SpUtils.putInt("minimax_pitch", pitch);
        } catch (NumberFormatException e) {
            SpUtils.putInt("minimax_pitch", 0);
        }

        SpUtils.putString("stt_base_url", etSttBaseUrl.getText().toString().trim());
        SpUtils.putString("stt_api_key", etSttApiKey.getText().toString().trim());
        SpUtils.putString("stt_model", etSttModel.getText().toString().trim());

        Toast.makeText(this, "语音配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
