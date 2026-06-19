package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.SemesterConfig;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class PeriodSettingsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private PeriodAdapter adapter;
    private CheckBox cbPerDay;
    private Spinner spinnerDay;
    private int semesterId;
    private SemesterConfig semester;
    private int currentDay = 0; // 0=Mon

    private Map<Integer, List<String[]>> dayPeriods = new HashMap<>(); // day -> list of [start,end]
    private List<String[]> sharedPeriods = new ArrayList<>(); // shared across all days

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_period_settings);

        Toolbar tb = findViewById(R.id.toolbar); tb.setTitle("课时设置"); setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvPeriods); rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PeriodAdapter(); rv.setAdapter(adapter);
        cbPerDay = findViewById(R.id.cbPerDay);
        spinnerDay = findViewById(R.id.spinnerDay);
        spinnerDay.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"周一","周二","周三","周四","周五","周六","周日"}));
        ((android.widget.ArrayAdapter<?>)spinnerDay.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        semesterId = getIntent().getIntExtra("semesterId", -1);
        if (semesterId == -1) { finish(); return; }

        cbPerDay.setOnCheckedChangeListener((btn, checked) -> {
            spinnerDay.setVisibility(checked ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        });
        spinnerDay.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { currentDay = pos; adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnResetDefault).setOnClickListener(v -> resetDefault());

        loadSemester();
    }

    private void loadSemester() {
        Executors.newSingleThreadExecutor().execute(() -> {
            SemesterConfig sc = AppDatabase.getDatabase(this).semesterConfigDao().getById(semesterId);
            if (sc == null) { finish(); return; }
            runOnUiThread(() -> {
                semester = sc;
                parseCustomPeriods(sc.customPeriods);
                if (sharedPeriods.isEmpty()) generateDefaults();
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void parseCustomPeriods(String raw) {
        dayPeriods.clear(); sharedPeriods.clear();
        if (raw == null || raw.isEmpty()) return;
        if (raw.startsWith("{")) {
            try {
                Gson gson = new Gson();
                Map<String, Object> root = gson.fromJson(raw, new TypeToken<Map<String, Object>>(){}.getType());
                if (Boolean.TRUE.equals(root.get("perDay"))) {
                    cbPerDay.setChecked(true);
                    spinnerDay.setVisibility(View.VISIBLE);
                    Map<String, List<String>> days = (Map<String, List<String>>) root.get("days");
                    if (days != null) {
                        for (Map.Entry<String, List<String>> e : days.entrySet()) {
                            int d = Integer.parseInt(e.getKey());
                            List<String[]> periods = new ArrayList<>();
                            for (String s : e.getValue()) {
                                String[] parts = s.split("-");
                                if (parts.length == 2) periods.add(new String[]{parts[0].trim(), parts[1].trim()});
                            }
                            dayPeriods.put(d, periods);
                        }
                    }
                } else {
                    List<String> arr = (List<String>) root.get("periods");
                    if (arr != null) for (String s : arr) { String[] p = s.split("-"); if (p.length == 2) sharedPeriods.add(new String[]{p[0].trim(), p[1].trim()}); }
                }
                return;
            } catch (Exception ignored) {}
        }
        // Old format: comma-separated time ranges
        for (String seg : raw.split(",")) {
            String[] parts = seg.trim().split("-");
            if (parts.length == 2) sharedPeriods.add(new String[]{parts[0].trim(), parts[1].trim()});
        }
    }

    private void generateDefaults() {
        sharedPeriods.clear();
        if (semester == null) return;
        try {
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.US);
            java.util.Date base = tf.parse(semester.firstPeriodStart);
            Calendar cal = Calendar.getInstance(); cal.setTime(base);
            for (int i = 0; i < semester.periodsPerDay; i++) {
                String s = tf.format(cal.getTime());
                cal.add(Calendar.MINUTE, semester.periodDuration);
                String e = tf.format(cal.getTime());
                cal.add(Calendar.MINUTE, semester.periodBreak);
                sharedPeriods.add(new String[]{s, e});
            }
        } catch (Exception ignored) {}
    }

    private List<String[]> currentPeriods() {
        if (cbPerDay.isChecked()) {
            List<String[]> p = dayPeriods.get(currentDay);
            return p != null ? p : new ArrayList<>();
        }
        return sharedPeriods;
    }

    private void resetDefault() {
        new AlertDialog.Builder(this).setMessage("恢复为学期默认时间？所有自定义修改将丢失")
                .setPositiveButton("恢复", (d, w) -> { generateDefaults(); dayPeriods.clear(); cbPerDay.setChecked(false); adapter.notifyDataSetChanged(); })
                .setNegativeButton("取消", null).show();
    }

    private void save() {
        // Collect from adapter
        for (int i = 0; i < adapter.rows.size(); i++) {
            PeriodAdapter.Row r = adapter.rows.get(i);
            String start = r.etStart.getText().toString().trim(), end = r.etEnd.getText().toString().trim();
            if (start.isEmpty() || end.isEmpty()) continue;
            if (cbPerDay.isChecked()) {
                if (!dayPeriods.containsKey(currentDay)) dayPeriods.put(currentDay, new ArrayList<>());
                List<String[]> list = dayPeriods.get(currentDay);
                if (i < list.size()) { list.get(i)[0] = start; list.get(i)[1] = end; }
                else list.add(new String[]{start, end});
            } else {
                if (i < sharedPeriods.size()) { sharedPeriods.get(i)[0] = start; sharedPeriods.get(i)[1] = end; }
                else sharedPeriods.add(new String[]{start, end});
            }
        }

        // Serialize to JSON
        Map<String, Object> root = new HashMap<>();
        if (cbPerDay.isChecked()) {
            root.put("perDay", true);
            Map<String, List<String>> days = new HashMap<>();
            for (Map.Entry<Integer, List<String[]>> e : dayPeriods.entrySet()) {
                List<String> arr = new ArrayList<>();
                for (String[] p : e.getValue()) arr.add(p[0] + "-" + p[1]);
                days.put(String.valueOf(e.getKey()), arr);
            }
            root.put("days", days);
        } else {
            root.put("perDay", false);
            List<String> arr = new ArrayList<>();
            for (String[] p : sharedPeriods) arr.add(p[0] + "-" + p[1]);
            root.put("periods", arr);
        }
        String json = new Gson().toJson(root);

        Executors.newSingleThreadExecutor().execute(() -> {
            semester.customPeriods = json;
            AppDatabase.getDatabase(this).semesterConfigDao().update(semester);
            runOnUiThread(() -> { Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show(); finish(); });
        });
    }

    private class PeriodAdapter extends RecyclerView.Adapter<PeriodAdapter.VH> {
        List<Row> rows = new ArrayList<>();

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_period_row, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            List<String[]> list = currentPeriods();
            String[] data = pos < list.size() ? list.get(pos) : new String[]{"", ""};
            h.tvPeriod.setText("第" + (pos + 1) + "节");
            h.etStart.setText(data[0]); h.etEnd.setText(data[1]);
            Row r = rows.get(pos); r.etStart = h.etStart; r.etEnd = h.etEnd;
        }

        @Override public int getItemCount() {
            int size = currentPeriods().size();
            // Show at least the default count, plus one empty row for adding
            int min = semester != null ? semester.periodsPerDay : 0;
            int count = Math.max(min, size) + 1; // +1 for adding new
            while (rows.size() < count) rows.add(new Row());
            while (rows.size() > count) rows.remove(rows.size() - 1);
            return count;
        }

        class Row { EditText etStart, etEnd; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvPeriod; EditText etStart, etEnd;
            VH(View v) { super(v); tvPeriod = v.findViewById(R.id.tvPeriod); etStart = v.findViewById(R.id.etStart); etEnd = v.findViewById(R.id.etEnd); }
        }
    }
}
