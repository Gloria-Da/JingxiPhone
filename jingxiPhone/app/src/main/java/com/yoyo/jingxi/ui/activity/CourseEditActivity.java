package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CourseEntry;
import com.yoyo.jingxi.utils.ThemeManager;

import java.util.concurrent.Executors;

public class CourseEditActivity extends AppCompatActivity {

    private static final int[] COLORS = {
            0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFCDD2, 0xFFFFF9C4, 0xFFE1BEE7, 0xFFFFE0B2
    };

    private TextInputEditText etName, etTeacher, etLocation, etNotes, etCustomWeeks;
    private TextInputLayout tilCustomWeeks;
    private Spinner spinnerDay;
    private NumberPicker npStartPeriod, npPeriodCount;
    private RadioGroup rgWeekPattern;
    private Button btnSave, btnDelete;
    private LinearLayout colorPicker;

    private int editCourseId = -1;
    private int selectedColor = 0xFFBBDEFB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_edit);

        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); }

        etName = findViewById(R.id.etName);
        etTeacher = findViewById(R.id.etTeacher);
        etLocation = findViewById(R.id.etLocation);
        etNotes = findViewById(R.id.etNotes);
        etCustomWeeks = findViewById(R.id.etCustomWeeks);
        tilCustomWeeks = findViewById(R.id.tilCustomWeeks);
        spinnerDay = findViewById(R.id.spinnerDay);
        npStartPeriod = findViewById(R.id.npStartPeriod);
        npPeriodCount = findViewById(R.id.npPeriodCount);
        rgWeekPattern = findViewById(R.id.rgWeekPattern);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        colorPicker = findViewById(R.id.colorPicker);

        spinnerDay.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"周一","周二","周三","周四","周五","周六","周日"}));
        ((android.widget.ArrayAdapter<?>)spinnerDay.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        npStartPeriod.setMinValue(1); npStartPeriod.setMaxValue(14); npStartPeriod.setValue(1);
        npPeriodCount.setMinValue(1); npPeriodCount.setMaxValue(6); npPeriodCount.setValue(1);

        rgWeekPattern.setOnCheckedChangeListener((g, id) -> {
            tilCustomWeeks.setVisibility(id == R.id.rbCustom ? View.VISIBLE : View.GONE);
        });

        // Color picker
        for (int c : COLORS) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(36, 36);
            lp.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(c);
            if (c == selectedColor) { bg.setStroke(3, 0xFF333333); }
            dot.setBackground(bg);
            final int color = c;
            dot.setOnClickListener(v -> {
                selectedColor = color;
                refreshColorPicker();
            });
            colorPicker.addView(dot);
        }

        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setMessage("删除此课程？")
                    .setPositiveButton("删除", (d,w) -> {
                        if (editCourseId != -1) {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                CourseEntry ce = AppDatabase.getDatabase(this).courseEntryDao().getById(editCourseId);
                                if (ce != null) AppDatabase.getDatabase(this).courseEntryDao().delete(ce);
                                runOnUiThread(this::finish);
                            });
                        }
                    }).setNegativeButton("取消", null).show();
        });

        loadIntent();
    }

    private void loadIntent() {
        editCourseId = getIntent().getIntExtra("courseId", -1);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(editCourseId == -1 ? "添加课程" : "编辑课程");

        if (editCourseId == -1) {
            btnDelete.setVisibility(View.GONE);
            int day = getIntent().getIntExtra("dayOfWeek", 0);
            int period = getIntent().getIntExtra("startPeriod", 1);
            spinnerDay.setSelection(day);
            npStartPeriod.setValue(period);
        } else {
            btnDelete.setVisibility(View.VISIBLE);
            Executors.newSingleThreadExecutor().execute(() -> {
                CourseEntry ce = AppDatabase.getDatabase(this).courseEntryDao().getById(editCourseId);
                if (ce != null) runOnUiThread(() -> fill(ce));
            });
        }
    }

    private void fill(CourseEntry ce) {
        etName.setText(ce.name);
        etTeacher.setText(ce.teacher);
        etLocation.setText(ce.location);
        if (ce.notes != null) etNotes.setText(ce.notes);
        spinnerDay.setSelection(ce.dayOfWeek);
        npStartPeriod.setValue(ce.startPeriod);
        npPeriodCount.setValue(ce.periodCount);
        selectedColor = ce.color;
        refreshColorPicker();

        switch (ce.weekPattern) {
            case "ODD": rgWeekPattern.check(R.id.rbOdd); break;
            case "EVEN": rgWeekPattern.check(R.id.rbEven); break;
            case "EVERY": default: rgWeekPattern.check(R.id.rbEvery); break;
        }
        if (!"EVERY".equals(ce.weekPattern) && !"ODD".equals(ce.weekPattern) && !"EVEN".equals(ce.weekPattern)) {
            rgWeekPattern.check(R.id.rbCustom);
            tilCustomWeeks.setVisibility(View.VISIBLE);
            etCustomWeeks.setText(ce.weekPattern);
        }
    }

    private void refreshColorPicker() {
        for (int i = 0; i < colorPicker.getChildCount(); i++) {
            View dot = colorPicker.getChildAt(i);
            GradientDrawable bg = (GradientDrawable) dot.getBackground();
            if (COLORS[i] == selectedColor) {
                bg.setStroke(3, 0xFF333333);
            } else {
                bg.setStroke(0, 0);
            }
        }
    }

    private void save() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) { Toast.makeText(this, "请输入课程名称", Toast.LENGTH_SHORT).show(); return; }

        CourseEntry ce = new CourseEntry();
        if (editCourseId != -1) ce.id = editCourseId;
        ce.name = name;
        ce.teacher = etTeacher.getText() != null ? etTeacher.getText().toString().trim() : "";
        ce.location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        ce.notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
        ce.dayOfWeek = spinnerDay.getSelectedItemPosition();
        ce.startPeriod = npStartPeriod.getValue();
        ce.periodCount = npPeriodCount.getValue();
        ce.color = selectedColor;

        int weekId = rgWeekPattern.getCheckedRadioButtonId();
        if (weekId == R.id.rbOdd) ce.weekPattern = "ODD";
        else if (weekId == R.id.rbEven) ce.weekPattern = "EVEN";
        else if (weekId == R.id.rbCustom) {
            ce.weekPattern = etCustomWeeks.getText() != null ? etCustomWeeks.getText().toString().trim() : "EVERY";
        } else ce.weekPattern = "EVERY";

        ce.semesterId = getIntent().getIntExtra("semesterId", -1);
        if (ce.semesterId == -1 && editCourseId != -1) {
            // Load from existing
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            if (editCourseId == -1) AppDatabase.getDatabase(this).courseEntryDao().insert(ce);
            else AppDatabase.getDatabase(this).courseEntryDao().update(ce);
            runOnUiThread(() -> { Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show(); finish(); });
        });
    }
}
