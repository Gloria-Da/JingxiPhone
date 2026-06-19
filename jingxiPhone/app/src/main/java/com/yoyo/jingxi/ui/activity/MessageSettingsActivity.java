package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.utils.SpUtils;

public class MessageSettingsActivity extends AppCompatActivity {

    private SeekBar sbHistoryRounds;
    private EditText etHistoryRounds;
    private SeekBar sbSummaryRounds;
    private EditText etSummaryRounds;
    private EditText etMemoryCallCount;
    private SwitchCompat swMemoryV2Enabled;
    private RadioGroup rgMemoryMode;
    private SeekBar sbProfileCuratorInterval;
    private EditText etProfileCuratorInterval;
    private SeekBar sbCuratorInterval;
    private EditText etCuratorInterval;
    private SeekBar sbMomentHistoryRounds;
    private EditText etMomentHistoryRounds;
    private RadioGroup rgMomentInteractionMode;
    private Button btnSave;

    private boolean isUpdating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("消息与记忆设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        sbHistoryRounds = findViewById(R.id.sbHistoryRounds);
        etHistoryRounds = findViewById(R.id.etHistoryRounds);
        sbSummaryRounds = findViewById(R.id.sbSummaryRounds);
        etSummaryRounds = findViewById(R.id.etSummaryRounds);
        etMemoryCallCount = findViewById(R.id.etMemoryCallCount);
        swMemoryV2Enabled = findViewById(R.id.swMemoryV2Enabled);
        rgMemoryMode = findViewById(R.id.rgMemoryMode);
        sbProfileCuratorInterval = findViewById(R.id.sbProfileCuratorInterval);
        etProfileCuratorInterval = findViewById(R.id.etProfileCuratorInterval);
        sbCuratorInterval = findViewById(R.id.sbCuratorInterval);
        etCuratorInterval = findViewById(R.id.etCuratorInterval);
        sbMomentHistoryRounds = findViewById(R.id.sbMomentHistoryRounds);
        etMomentHistoryRounds = findViewById(R.id.etMomentHistoryRounds);
        rgMomentInteractionMode = findViewById(R.id.rgMomentInteractionMode);
        btnSave = findViewById(R.id.btnSave);

        // Load current values
        int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
        int summaryRounds = SpUtils.getInt("SETTING_SUMMARY_ROUNDS", 0);
        int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);

        sbHistoryRounds.setProgress(historyRounds);
        etHistoryRounds.setText(String.valueOf(historyRounds));

        sbSummaryRounds.setProgress(summaryRounds);
        etSummaryRounds.setText(String.valueOf(summaryRounds));

        etMemoryCallCount.setText(String.valueOf(memoryCallCount));

        // Memory 2.0 settings
        boolean v2Enabled = SpUtils.getBoolean("MEMORY_V2_ENABLED", true);
        swMemoryV2Enabled.setChecked(v2Enabled);
        String v2Mode = SpUtils.getString("MEMORY_V2_MODE", "economy");
        if ("immersive".equals(v2Mode)) {
            rgMemoryMode.check(R.id.rbImmersive);
        } else {
            rgMemoryMode.check(R.id.rbEconomy);
        }

        // New curator settings
        int profileCuratorInterval = SpUtils.getInt("PROFILE_CURATOR_INTERVAL", 10);
        sbProfileCuratorInterval.setProgress(profileCuratorInterval);
        etProfileCuratorInterval.setText(String.valueOf(profileCuratorInterval));

        int unifiedCuratorInterval = SpUtils.getInt("UNIFIED_CURATOR_INTERVAL", 5);
        int unifiedProgress = Math.max(0, Math.min(sbCuratorInterval.getMax(), unifiedCuratorInterval));
        sbCuratorInterval.setProgress(unifiedProgress);
        etCuratorInterval.setText(String.valueOf(unifiedCuratorInterval));

        int momentHistoryRounds = SpUtils.getInt("SETTING_MOMENT_HISTORY_ROUNDS", 15);
        sbMomentHistoryRounds.setProgress(momentHistoryRounds);
        etMomentHistoryRounds.setText(String.valueOf(momentHistoryRounds));

        String interactionMode = SpUtils.getString("MOMENT_INTERACTION_MODE", "individual");
        if ("batch".equals(interactionMode)) {
            rgMomentInteractionMode.check(R.id.rbBatch);
        } else {
            rgMomentInteractionMode.check(R.id.rbIndividual);
        }

        setupSync(sbHistoryRounds, etHistoryRounds);
        setupSync(sbSummaryRounds, etSummaryRounds);
        setupSync(sbProfileCuratorInterval, etProfileCuratorInterval);
        setupSync(sbCuratorInterval, etCuratorInterval);
        setupSync(sbMomentHistoryRounds, etMomentHistoryRounds);

        btnSave.setOnClickListener(v -> {
            try {
                int hRounds = Integer.parseInt(etHistoryRounds.getText().toString());
                int sRounds = Integer.parseInt(etSummaryRounds.getText().toString());
                int mCallCount = Integer.parseInt(etMemoryCallCount.getText().toString());
                int profileInterval = Integer.parseInt(etProfileCuratorInterval.getText().toString());
                int curatorInterval = Integer.parseInt(etCuratorInterval.getText().toString());
                int momentHRounds = Integer.parseInt(etMomentHistoryRounds.getText().toString());

                if (hRounds < 0 || hRounds > 200 || sRounds < 0 || sRounds > 200
                    || mCallCount < 0 || profileInterval < 0 || profileInterval > 50
                    || curatorInterval < 1 || curatorInterval > 50
                    || momentHRounds < 0 || momentHRounds > 30) {
                    Toast.makeText(this, "请输入有效范围的数字", Toast.LENGTH_SHORT).show();
                    return;
                }

                SpUtils.putInt("SETTING_HISTORY_ROUNDS", hRounds);
                SpUtils.putInt("SETTING_SUMMARY_ROUNDS", sRounds);
                SpUtils.putInt("SETTING_MEMORY_CALL_COUNT", mCallCount);

                // Memory 2.0 settings
                SpUtils.putBoolean("MEMORY_V2_ENABLED", swMemoryV2Enabled.isChecked());
                String mode = rgMemoryMode.getCheckedRadioButtonId() == R.id.rbImmersive ? "immersive" : "economy";
                SpUtils.putString("MEMORY_V2_MODE", mode);

                // Curator settings
                SpUtils.putInt("PROFILE_CURATOR_INTERVAL", profileInterval);
                SpUtils.putInt("UNIFIED_CURATOR_INTERVAL", curatorInterval);
                SpUtils.putInt("SETTING_MOMENT_HISTORY_ROUNDS", momentHRounds);

                // Moment interaction mode
                String momentMode = rgMomentInteractionMode.getCheckedRadioButtonId() == R.id.rbBatch ? "batch" : "individual";
                SpUtils.putString("MOMENT_INTERACTION_MODE", momentMode);

                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSync(SeekBar seekBar, EditText editText) {
        setupSync(seekBar, editText, seekBar.getMax());
    }

    private void setupSync(SeekBar seekBar, EditText editText, int max) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    isUpdating = true;
                    editText.setText(String.valueOf(progress));
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdating) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val >= 0 && val <= max) {
                            seekBar.setProgress(val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }
}
