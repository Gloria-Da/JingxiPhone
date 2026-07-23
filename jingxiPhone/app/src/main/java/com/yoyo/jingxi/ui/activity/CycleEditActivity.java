package com.yoyo.jingxi.ui.activity;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CycleRecord;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CycleEditActivity extends AppCompatActivity {

    private TextView tvStartDate, tvEndDate;
    private RadioGroup rgFlowLevel;
    private CheckBox cbCramps, cbHeadache, cbFatigue, cbBloating, cbMood, cbOtherSymptom;
    private TextInputEditText etNotes;
    private Button btnSave, btnDelete;

    private Calendar startDate = Calendar.getInstance();
    private Calendar endDate = Calendar.getInstance();

    private int editRecordId = -1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cycle_edit);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupViews();
        loadIntentData();
    }

    private void setupViews() {
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        rgFlowLevel = findViewById(R.id.rgFlowLevel);
        cbCramps = findViewById(R.id.cbCramps);
        cbHeadache = findViewById(R.id.cbHeadache);
        cbFatigue = findViewById(R.id.cbFatigue);
        cbBloating = findViewById(R.id.cbBloating);
        cbMood = findViewById(R.id.cbMood);
        cbOtherSymptom = findViewById(R.id.cbOtherSymptom);
        etNotes = findViewById(R.id.etNotes);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);

        tvStartDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                startDate.set(year, month, dayOfMonth);
                tvStartDate.setText(dateFormat.format(startDate.getTime()));
                // Auto-set end date to start date if end is before start
                if (endDate.before(startDate)) {
                    endDate.setTime(startDate.getTime());
                    tvEndDate.setText(dateFormat.format(endDate.getTime()));
                }
            }, startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DAY_OF_MONTH)).show();
        });

        tvEndDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                endDate.set(year, month, dayOfMonth);
                tvEndDate.setText(dateFormat.format(endDate.getTime()));
            }, endDate.get(Calendar.YEAR), endDate.get(Calendar.MONTH),
                    endDate.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnSave.setOnClickListener(v -> saveRecord());
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.delete_cycle_confirm)
                    .setPositiveButton(R.string.delete, (dialog, which) -> deleteRecord())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void loadIntentData() {
        String presetDate = getIntent().getStringExtra("date");
        editRecordId = getIntent().getIntExtra("recordId", -1);

        if (presetDate != null) {
            try {
                startDate.setTime(dateFormat.parse(presetDate));
                endDate.setTime(dateFormat.parse(presetDate));
                // Default: period = 5 days
                endDate.add(Calendar.DAY_OF_MONTH, 4);
            } catch (Exception ignored) {}
        }
        tvStartDate.setText(dateFormat.format(startDate.getTime()));
        tvEndDate.setText(dateFormat.format(endDate.getTime()));

        if (editRecordId != -1) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.edit_cycle);
            }
            btnDelete.setVisibility(View.VISIBLE);
            loadRecord(editRecordId);
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.record_cycle);
            }
        }
    }

    private void loadRecord(int recordId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CycleRecord record = AppDatabase.getDatabase(this).cycleRecordDao().getRecordById(recordId);
            if (record != null) {
                runOnUiThread(() -> fillFromRecord(record));
            }
        });
    }

    private void fillFromRecord(CycleRecord record) {
        try {
            startDate.setTime(dateFormat.parse(record.startDate));
            tvStartDate.setText(dateFormat.format(startDate.getTime()));
            endDate.setTime(dateFormat.parse(record.endDate));
            tvEndDate.setText(dateFormat.format(endDate.getTime()));
        } catch (Exception ignored) {}

        // Flow level
        if (record.flowLevel == null) {
            rgFlowLevel.clearCheck();
        } else if (record.flowLevel == 1) rgFlowLevel.check(R.id.rbLight);
        else if (record.flowLevel == 3) rgFlowLevel.check(R.id.rbHeavy);
        else rgFlowLevel.check(R.id.rbMedium);

        // Symptoms
        if (record.symptoms != null) {
            String[] parts = record.symptoms.split(",");
            for (String s : parts) {
                s = s.trim();
                if (s.equals("cramps")) cbCramps.setChecked(true);
                else if (s.equals("headache")) cbHeadache.setChecked(true);
                else if (s.equals("fatigue")) cbFatigue.setChecked(true);
                else if (s.equals("bloating")) cbBloating.setChecked(true);
                else if (s.equals("mood")) cbMood.setChecked(true);
                else if (s.equals("other")) cbOtherSymptom.setChecked(true);
            }
        }

        if (record.notes != null) etNotes.setText(record.notes);
    }

    private void saveRecord() {
        CycleRecord record = new CycleRecord();
        if (editRecordId != -1) {
            record.id = editRecordId;
        }

        // Ensure end >= start
        if (endDate.before(startDate)) {
            Toast.makeText(this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
            return;
        }

        record.startDate = dateFormat.format(startDate.getTime());
        record.endDate = dateFormat.format(endDate.getTime());

        // Flow level
        int checkedId = rgFlowLevel.getCheckedRadioButtonId();
        if (checkedId == R.id.rbLight) record.flowLevel = 1;
        else if (checkedId == R.id.rbHeavy) record.flowLevel = 3;
        else if (checkedId == R.id.rbMedium) record.flowLevel = 2;
        else record.flowLevel = null;  // 未选择时为 null

        // Symptoms
        List<String> symptoms = new ArrayList<>();
        if (cbCramps.isChecked()) symptoms.add("cramps");
        if (cbHeadache.isChecked()) symptoms.add("headache");
        if (cbFatigue.isChecked()) symptoms.add("fatigue");
        if (cbBloating.isChecked()) symptoms.add("bloating");
        if (cbMood.isChecked()) symptoms.add("mood");
        if (cbOtherSymptom.isChecked()) symptoms.add("other");
        record.symptoms = String.join(",", symptoms);

        record.notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

        Executors.newSingleThreadExecutor().execute(() -> {
            if (editRecordId == -1) {
                AppDatabase.getDatabase(this).cycleRecordDao().insert(record);
            } else {
                AppDatabase.getDatabase(this).cycleRecordDao().update(record);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void deleteRecord() {
        if (editRecordId == -1) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            CycleRecord record = AppDatabase.getDatabase(this).cycleRecordDao().getRecordById(editRecordId);
            if (record != null) {
                AppDatabase.getDatabase(this).cycleRecordDao().delete(record);
            }
            runOnUiThread(this::finish);
        });
    }
}
