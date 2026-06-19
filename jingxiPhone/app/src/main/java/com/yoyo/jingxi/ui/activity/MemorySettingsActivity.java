package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.TimeWeightCalculator;

public class MemorySettingsActivity extends AppCompatActivity {

    private SwitchCompat switchEnabled;
    private SeekBar seekLambdaBase;
    private SeekBar seekBeta;
    private SeekBar seekRetentionFloor;
    private TextView tvLambdaBase;
    private TextView tvBeta;
    private TextView tvRetentionFloor;
    private TextView tvPreview;
    private Button btnSave;

    private static final float LAMBDA_BASE_MIN = 0.005f;
    private static final float LAMBDA_BASE_MAX = 0.2f;
    private static final float BETA_MIN = 0.05f;
    private static final float BETA_MAX = 2.0f;
    private static final float RETENTION_FLOOR_MIN = 0.01f;
    private static final float RETENTION_FLOOR_MAX = 0.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("记忆权重设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        switchEnabled = findViewById(R.id.switchEnabled);
        seekLambdaBase = findViewById(R.id.seekLambdaBase);
        seekBeta = findViewById(R.id.seekBeta);
        seekRetentionFloor = findViewById(R.id.seekRetentionFloor);
        tvLambdaBase = findViewById(R.id.tvLambdaBase);
        tvBeta = findViewById(R.id.tvBeta);
        tvRetentionFloor = findViewById(R.id.tvRetentionFloor);
        tvPreview = findViewById(R.id.tvPreview);
        btnSave = findViewById(R.id.btnSave);

        // Load saved values
        boolean enabled = SpUtils.getBoolean(TimeWeightCalculator.KEY_ENABLED, false);
        switchEnabled.setChecked(enabled);

        float savedLambdaBase = SpUtils.getFloat(TimeWeightCalculator.KEY_LAMBDA_BASE, 0.05f);
        seekLambdaBase.setProgress(valueToProgress(savedLambdaBase, LAMBDA_BASE_MIN, LAMBDA_BASE_MAX, seekLambdaBase.getMax()));

        float savedBeta = SpUtils.getFloat(TimeWeightCalculator.KEY_BETA, 0.5f);
        seekBeta.setProgress(valueToProgress(savedBeta, BETA_MIN, BETA_MAX, seekBeta.getMax()));

        float savedRetentionFloor = SpUtils.getFloat(TimeWeightCalculator.KEY_RETENTION_FLOOR, 0.05f);
        seekRetentionFloor.setProgress(valueToProgress(savedRetentionFloor, RETENTION_FLOOR_MIN, RETENTION_FLOOR_MAX, seekRetentionFloor.getMax()));

        updateLabels();
        updatePreview();

        // SeekBar listeners
        seekLambdaBase.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLabels();
                updatePreview();
            }
        });
        seekBeta.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLabels();
                updatePreview();
            }
        });
        seekRetentionFloor.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLabels();
                updatePreview();
            }
        });

        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());

        btnSave.setOnClickListener(v -> {
            SpUtils.putBoolean(TimeWeightCalculator.KEY_ENABLED, switchEnabled.isChecked());
            SpUtils.putFloat(TimeWeightCalculator.KEY_LAMBDA_BASE, getLambdaBase());
            SpUtils.putFloat(TimeWeightCalculator.KEY_BETA, getBeta());
            SpUtils.putFloat(TimeWeightCalculator.KEY_RETENTION_FLOOR, getRetentionFloor());
            SpUtils.putFloat(TimeWeightCalculator.KEY_LAMBDA_MIN, 0.001f);
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        });
    }

    private float getLambdaBase() {
        return progressToValue(seekLambdaBase.getProgress(), LAMBDA_BASE_MIN, LAMBDA_BASE_MAX, seekLambdaBase.getMax());
    }

    private float getBeta() {
        return progressToValue(seekBeta.getProgress(), BETA_MIN, BETA_MAX, seekBeta.getMax());
    }

    private float getRetentionFloor() {
        return progressToValue(seekRetentionFloor.getProgress(), RETENTION_FLOOR_MIN, RETENTION_FLOOR_MAX, seekRetentionFloor.getMax());
    }

    private void updateLabels() {
        float lambdaBase = getLambdaBase();
        tvLambdaBase.setText(String.format("%.3f (%s)", lambdaBase, TimeWeightCalculator.getHalfLifeText(lambdaBase)));
        tvBeta.setText(String.format("%.2f", getBeta()));
        tvRetentionFloor.setText(String.format("%.2f", getRetentionFloor()));
    }

    private void updatePreview() {
        boolean enabled = switchEnabled.isChecked();
        if (!enabled) {
            tvPreview.setText("时间加权已关闭。所有记忆以其原始重要度显示。");
            return;
        }

        float lambdaBase = getLambdaBase();
        float beta = getBeta();
        float floor = getRetentionFloor();

        // Compute example values for preview
        // Scenario 1: never recalled, 14 days
        float lambda0 = lambdaBase / (1.0f + beta * 0);
        lambda0 = Math.max(0.001f, lambda0);
        float retNever14 = Math.max(floor, (float) Math.exp(-lambda0 * 14));
        float weightNever14 = 3f * retNever14;

        // Scenario 2: recalled 3 times, 14 days
        float lambda3 = lambdaBase / (1.0f + beta * 3);
        lambda3 = Math.max(0.001f, lambda3);
        float ret3_14 = Math.max(floor, (float) Math.exp(-lambda3 * 14));
        float weight3_14 = 3f * ret3_14;

        // Scenario 3: recalled 10 times, 100 days
        float lambda10 = lambdaBase / (1.0f + beta * 10);
        lambda10 = Math.max(0.001f, lambda10);
        float ret10_100 = Math.max(floor, (float) Math.exp(-lambda10 * 100));
        float weight10_100 = 3f * ret10_100;

        StringBuilder sb = new StringBuilder();
        sb.append("情景记忆（重要度=3）：\n");
        sb.append(String.format("  从未回忆，14天后 → λ=%.3f，权重=%.1f\n", lambda0, weightNever14));
        sb.append(String.format("  回忆3次，14天后 → λ=%.3f，权重=%.1f\n", lambda3, weight3_14));
        sb.append(String.format("  回忆10次，100天后 → λ=%.3f，权重=%.1f\n", lambda10, weight10_100));
        tvPreview.setText(sb.toString());
    }

    private static int valueToProgress(float value, float min, float max, int maxProgress) {
        float ratio = (value - min) / (max - min);
        return Math.round(Math.max(0, Math.min(1, ratio)) * maxProgress);
    }

    private static float progressToValue(int progress, float min, float max, int maxProgress) {
        float ratio = (float) progress / maxProgress;
        return min + ratio * (max - min);
    }

    private static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
