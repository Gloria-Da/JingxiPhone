package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.utils.SpUtils;

public class MessageSettingsActivity extends AppCompatActivity {

    private SeekBar sbHistoryRounds;
    private EditText etHistoryRounds;
    private SeekBar sbSummaryRounds;
    private EditText etSummaryRounds;
    private EditText etMemoryCallCount;
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
        btnSave = findViewById(R.id.btnSave);

        // Load current values
        int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
        int summaryRounds = SpUtils.getInt("SETTING_SUMMARY_ROUNDS", 50);
        int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);

        sbHistoryRounds.setProgress(historyRounds);
        etHistoryRounds.setText(String.valueOf(historyRounds));

        sbSummaryRounds.setProgress(summaryRounds);
        etSummaryRounds.setText(String.valueOf(summaryRounds));

        etMemoryCallCount.setText(String.valueOf(memoryCallCount));

        setupSync(sbHistoryRounds, etHistoryRounds);
        setupSync(sbSummaryRounds, etSummaryRounds);

        btnSave.setOnClickListener(v -> {
            try {
                int hRounds = Integer.parseInt(etHistoryRounds.getText().toString());
                int sRounds = Integer.parseInt(etSummaryRounds.getText().toString());
                int mCallCount = Integer.parseInt(etMemoryCallCount.getText().toString());

                if (hRounds < 0 || hRounds > 200 || sRounds < 0 || sRounds > 200 || mCallCount < 0) {
                    Toast.makeText(this, "请输入有效范围的数字", Toast.LENGTH_SHORT).show();
                    return;
                }

                SpUtils.putInt("SETTING_HISTORY_ROUNDS", hRounds);
                SpUtils.putInt("SETTING_SUMMARY_ROUNDS", sRounds);
                SpUtils.putInt("SETTING_MEMORY_CALL_COUNT", mCallCount);

                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSync(SeekBar seekBar, EditText editText) {
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
                        if (val >= 0 && val <= 200) {
                            seekBar.setProgress(val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }
}
