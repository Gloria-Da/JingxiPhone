package com.yoyo.jingxi.ui.activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CalendarEvent;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class EventEditActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etNotes;
    private TextView tvEventDate, tvStartTime, tvEndTime;
    private CheckBox cbAllDay;
    private CheckBox cbTimeRange;
    private View tvTimeSep;
    private Spinner spinnerRecurrence;
    private Button btnSave, btnDelete;
    private View layoutTime;

    private Calendar selectedDate = Calendar.getInstance();
    private Calendar startTime = Calendar.getInstance();
    private Calendar endTime = Calendar.getInstance();

    private int editEventId = -1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_edit);

        setupViews();
        loadIntentData();
    }

    private void setupViews() {
        // The event edit layout doesn't have a built-in toolbar — use a simple title
        // Actually, let me check... The layout activity_event_edit.xml doesn't include a toolbar.
        // Let me add support action bar manually
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(editEventId == -1 ? R.string.add_event : R.string.edit_event);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etTitle = findViewById(R.id.etTitle);
        etNotes = findViewById(R.id.etNotes);
        tvEventDate = findViewById(R.id.tvEventDate);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        tvTimeSep = findViewById(R.id.tvTimeSep);
        cbAllDay = findViewById(R.id.cbAllDay);
        cbTimeRange = findViewById(R.id.cbTimeRange);
        spinnerRecurrence = findViewById(R.id.spinnerRecurrence);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        layoutTime = findViewById(R.id.layoutTime);

        // Recurrence spinner
        String[] recurrences = {
                getString(R.string.recurrence_none),
                getString(R.string.recurrence_daily),
                getString(R.string.recurrence_weekly),
                getString(R.string.recurrence_monthly),
                getString(R.string.recurrence_yearly)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, recurrences);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRecurrence.setAdapter(adapter);

        // Date picker
        tvEventDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedDate.set(year, month, dayOfMonth);
                tvEventDate.setText(dateFormat.format(selectedDate.getTime()));
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Time pickers
        tvStartTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, hour, minute) -> {
                startTime.set(Calendar.HOUR_OF_DAY, hour);
                startTime.set(Calendar.MINUTE, minute);
                tvStartTime.setText(timeFormat.format(startTime.getTime()));
            }, startTime.get(Calendar.HOUR_OF_DAY), startTime.get(Calendar.MINUTE), true).show();
        });

        tvEndTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, hour, minute) -> {
                endTime.set(Calendar.HOUR_OF_DAY, hour);
                endTime.set(Calendar.MINUTE, minute);
                tvEndTime.setText(timeFormat.format(endTime.getTime()));
            }, endTime.get(Calendar.HOUR_OF_DAY), endTime.get(Calendar.MINUTE), true).show();
        });

        // All day toggle
        cbAllDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutTime.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            if (isChecked) {
                cbTimeRange.setChecked(false);
            }
        });

        // Time range toggle
        cbTimeRange.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvEndTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            tvTimeSep.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Save
        btnSave.setOnClickListener(v -> saveEvent());

        // Delete
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.delete_event_confirm)
                    .setPositiveButton(R.string.delete, (dialog, which) -> deleteEvent())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void loadIntentData() {
        String presetDate = getIntent().getStringExtra("date");
        editEventId = getIntent().getIntExtra("eventId", -1);

        if (presetDate != null) {
            try {
                selectedDate.setTime(dateFormat.parse(presetDate));
            } catch (Exception ignored) {}
        }
        tvEventDate.setText(dateFormat.format(selectedDate.getTime()));

        // Default times
        startTime.setTime(selectedDate.getTime());
        startTime.set(Calendar.HOUR_OF_DAY, 9);
        startTime.set(Calendar.MINUTE, 0);
        endTime.setTime(selectedDate.getTime());
        endTime.set(Calendar.HOUR_OF_DAY, 10);
        endTime.set(Calendar.MINUTE, 0);
        tvStartTime.setText(timeFormat.format(startTime.getTime()));
        tvEndTime.setText(timeFormat.format(endTime.getTime()));

        if (editEventId != -1) {
            // Load existing event
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.edit_event);
            }
            btnDelete.setVisibility(View.VISIBLE);
            loadEvent(editEventId);
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.add_event);
            }
        }
    }

    private void loadEvent(int eventId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CalendarEvent event = AppDatabase.getDatabase(this).calendarEventDao().getEventById(eventId);
            if (event != null) {
                runOnUiThread(() -> fillFromEvent(event));
            }
        });
    }

    private void fillFromEvent(CalendarEvent event) {
        etTitle.setText(event.title);
        if (event.notes != null) etNotes.setText(event.notes);

        try {
            selectedDate.setTime(dateFormat.parse(event.eventDate));
            tvEventDate.setText(dateFormat.format(selectedDate.getTime()));
        } catch (Exception ignored) {}

        cbAllDay.setChecked(event.allDay);
        layoutTime.setVisibility(event.allDay ? View.GONE : View.VISIBLE);

        if (event.startTime > 0) {
            startTime.setTimeInMillis(event.startTime);
            tvStartTime.setText(timeFormat.format(startTime.getTime()));
        }
        boolean isRange = event.endTime > 0 && event.endTime != event.startTime;
        cbTimeRange.setChecked(isRange);
        if (event.endTime > 0) {
            endTime.setTimeInMillis(event.endTime);
            tvEndTime.setText(timeFormat.format(endTime.getTime()));
            tvEndTime.setVisibility(isRange ? View.VISIBLE : View.GONE);
            tvTimeSep.setVisibility(isRange ? View.VISIBLE : View.GONE);
        } else {
            tvEndTime.setVisibility(View.GONE);
            tvTimeSep.setVisibility(View.GONE);
        }

        // Recurrence
        String[] recurrences = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"};
        for (int i = 0; i < recurrences.length; i++) {
            if (recurrences[i].equals(event.recurrence)) {
                spinnerRecurrence.setSelection(i);
                break;
            }
        }
    }

    private void saveEvent() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入日程标题", Toast.LENGTH_SHORT).show();
            return;
        }

        CalendarEvent event = new CalendarEvent();
        if (editEventId != -1) {
            event.id = editEventId;
        }
        event.title = title;
        event.notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
        event.eventDate = dateFormat.format(selectedDate.getTime());
        event.allDay = cbAllDay.isChecked();

        // Merge selected date with time
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(selectedDate.getTimeInMillis());
        startCal.set(Calendar.HOUR_OF_DAY, startTime.get(Calendar.HOUR_OF_DAY));
        startCal.set(Calendar.MINUTE, startTime.get(Calendar.MINUTE));
        event.startTime = startCal.getTimeInMillis();

        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(selectedDate.getTimeInMillis());
        endCal.set(Calendar.HOUR_OF_DAY, endTime.get(Calendar.HOUR_OF_DAY));
        endCal.set(Calendar.MINUTE, endTime.get(Calendar.MINUTE));
        // 非时间段 → endTime=0 表示时间点
        event.endTime = cbTimeRange.isChecked() ? endCal.getTimeInMillis() : 0;

        // Recurrence
        int recurrenceIndex = spinnerRecurrence.getSelectedItemPosition();
        String[] recurrences = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"};
        event.recurrence = recurrences[recurrenceIndex];

        Executors.newSingleThreadExecutor().execute(() -> {
            if (editEventId == -1) {
                AppDatabase.getDatabase(this).calendarEventDao().insert(event);
            } else {
                AppDatabase.getDatabase(this).calendarEventDao().update(event);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void deleteEvent() {
        if (editEventId == -1) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            CalendarEvent event = AppDatabase.getDatabase(this).calendarEventDao().getEventById(editEventId);
            if (event != null) {
                AppDatabase.getDatabase(this).calendarEventDao().delete(event);
            }
            runOnUiThread(this::finish);
        });
    }
}
