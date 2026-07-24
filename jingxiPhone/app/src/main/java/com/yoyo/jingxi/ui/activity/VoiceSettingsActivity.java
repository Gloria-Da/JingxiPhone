package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.network.SttManager;
import com.yoyo.jingxi.network.SttModelManager;
import com.yoyo.jingxi.utils.SpUtils;

public class VoiceSettingsActivity extends AppCompatActivity {

    private static final int PICK_MODEL_FILE = 1001;
    private static final int PICK_RINGTONE = 1002;

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

    private SwitchCompat switchUseLocalStt;
    private TextView tvModelStatus;
    private ProgressBar progressModelDownload;
    private Button btnDownloadModel;
    private Button btnImportModel;

    private Button btnSave;

    // 来电铃声
    private TextView tvRingtoneName;
    private Button btnPickRingtone;

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

        switchUseLocalStt = findViewById(R.id.switchUseLocalStt);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        progressModelDownload = findViewById(R.id.progressModelDownload);
        btnDownloadModel = findViewById(R.id.btnDownloadModel);
        btnImportModel = findViewById(R.id.btnImportModel);

        btnSave = findViewById(R.id.btnSave);

        // 来电铃声
        tvRingtoneName = findViewById(R.id.tvRingtoneName);
        btnPickRingtone = findViewById(R.id.btnPickRingtone);

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

        btnPickRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择来电铃声");
            // 预选当前铃声
            String currentUri = SpUtils.getString("RINGTONE_URI", "");
            if (!currentUri.isEmpty()) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri));
            }
            startActivityForResult(intent, PICK_RINGTONE);
        });

        btnImportModel.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/octet-stream", "*/*"
            });
            startActivityForResult(Intent.createChooser(intent, "选择 model_q8.onnx 文件"), PICK_MODEL_FILE);
        });

        switchUseLocalStt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateModelStatusDisplay();
        });

        btnDownloadModel.setOnClickListener(v -> {
            SttModelManager manager = SttModelManager.getInstance();
            if (manager.getStatus() == SttModelManager.Status.DOWNLOADING) {
                Toast.makeText(this, "正在下载中...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (manager.getStatus() == SttModelManager.Status.READY) {
                Toast.makeText(this, "模型已就绪", Toast.LENGTH_SHORT).show();
                return;
            }

            btnDownloadModel.setEnabled(false);
            btnDownloadModel.setText("正在准备下载...");
            progressModelDownload.setVisibility(View.VISIBLE);
            progressModelDownload.setProgress(0);

            manager.startDownload(new SttModelManager.DownloadCallback() {
                @Override
                public void onProgress(int percent) {
                    runOnUiThread(() -> {
                        progressModelDownload.setProgress(percent);
                        btnDownloadModel.setText("下载中 " + percent + "%");
                    });
                }

                @Override
                public void onComplete(boolean success, String message) {
                    runOnUiThread(() -> {
                        btnDownloadModel.setEnabled(true);
                        if (success) {
                            btnDownloadModel.setText("重新下载");
                            progressModelDownload.setVisibility(View.GONE);
                            Toast.makeText(VoiceSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                        } else {
                            btnDownloadModel.setText("下载失败，点击重试");
                            progressModelDownload.setVisibility(View.GONE);
                            Toast.makeText(VoiceSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                        updateModelStatusDisplay();
                    });
                }
            });
        });
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
        etSttBaseUrl.setText(SpUtils.getString("stt_base_url", "https://api.siliconflow.cn/v1/"));
        etSttApiKey.setText(SpUtils.getString("stt_api_key", ""));
        etSttModel.setText(SpUtils.getString("stt_model", "FunAudioLLM/SenseVoiceSmall"));

        // Ringtone
        String ringtoneName = SpUtils.getString("RINGTONE_NAME", "默认铃声");
        tvRingtoneName.setText(ringtoneName);

        // Local STT
        switchUseLocalStt.setChecked(SpUtils.getBoolean("stt_use_local", false));
        updateModelStatusDisplay();
    }

    private void updateModelStatusDisplay() {
        SttModelManager manager = SttModelManager.getInstance();
        SttModelManager.Status status = manager.getStatus();
        long modelSize = manager.getModelSize();

        switch (status) {
            case READY:
                tvModelStatus.setText("模型已就绪 (" + formatSize(modelSize) + ")");
                btnDownloadModel.setText("重新下载");
                btnDownloadModel.setEnabled(true);
                progressModelDownload.setVisibility(View.GONE);
                break;
            case DOWNLOADING:
                tvModelStatus.setText("正在下载... " + manager.getDownloadProgress() + "%");
                btnDownloadModel.setText("下载中...");
                btnDownloadModel.setEnabled(false);
                progressModelDownload.setVisibility(View.VISIBLE);
                progressModelDownload.setProgress(manager.getDownloadProgress());
                break;
            case ERROR:
                tvModelStatus.setText("下载失败: " +
                    (manager.getErrorMessage() != null ? manager.getErrorMessage() : "未知错误"));
                btnDownloadModel.setText("重试下载");
                btnDownloadModel.setEnabled(true);
                progressModelDownload.setVisibility(View.GONE);
                break;
            default:
                tvModelStatus.setText("模型未下载 (约228MB)");
                btnDownloadModel.setText("下载离线模型");
                btnDownloadModel.setEnabled(true);
                progressModelDownload.setVisibility(View.GONE);
                break;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return (bytes / (1024 * 1024)) + "MB";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_RINGTONE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                SpUtils.putString("RINGTONE_URI", uri.toString());
                String name = RingtoneManager.getRingtone(this, uri).getTitle(this);
                SpUtils.putString("RINGTONE_NAME", name);
                tvRingtoneName.setText(name);
                Toast.makeText(this, "已设置铃声: " + name, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode != PICK_MODEL_FILE || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        Toast.makeText(this, "正在导入模型文件...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = SttModelManager.getInstance().importModelFile(this, uri);
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "模型导入成功", Toast.LENGTH_SHORT).show();
                } else {
                    String err = SttModelManager.getInstance().getErrorMessage();
                    Toast.makeText(this, "导入失败: " + (err != null ? err : "未知错误"), Toast.LENGTH_LONG).show();
                }
                updateModelStatusDisplay();
            });
        }).start();
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
        SpUtils.putBoolean("stt_use_local", switchUseLocalStt.isChecked());

        // Rebuild STT provider chain with new settings
        SttManager.getInstance().rebuildProviderChain();

        Toast.makeText(this, "语音配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
