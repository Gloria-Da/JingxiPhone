package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CourseEntry;
import com.yoyo.jingxi.data.entity.SemesterConfig;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class CourseScheduleActivity extends AppCompatActivity {

    private Spinner spinnerSemester;
    private TextView tvWeekInfo;
    private LinearLayout tableContainer;
    private List<SemesterConfig> semesters = new ArrayList<>();
    private SemesterConfig currentSemester;
    private int currentWeek = 1;
    private List<CourseEntry> allCourses = new ArrayList<>();

    private static final int[] COURSE_COLORS = {
            0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFCDD2, 0xFFFFF9C4, 0xFFE1BEE7, 0xFFFFE0B2
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_schedule);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("课程表");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerSemester = findViewById(R.id.spinnerSemester);
        tvWeekInfo = findViewById(R.id.tvWeekInfo);
        tableContainer = findViewById(R.id.tableContainer);

        findViewById(R.id.btnPrevWeek).setOnClickListener(v -> { if (currentWeek > 1) { currentWeek--; refreshTable(); } });
        findViewById(R.id.btnNextWeek).setOnClickListener(v -> { if (currentSemester != null && currentWeek < currentSemester.totalWeeks) { currentWeek++; refreshTable(); } });

        spinnerSemester.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < semesters.size()) {
                    currentSemester = semesters.get(pos);
                    currentWeek = getCurrentWeekNumber();
                    loadCourses();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        loadSemesters();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_course_schedule, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_course) {
            if (currentSemester == null) { Toast.makeText(this, "请先创建学期", Toast.LENGTH_SHORT).show(); return true; }
            startActivity(new Intent(this, CourseEditActivity.class).putExtra("semesterId", currentSemester.id));
        } else if (item.getItemId() == R.id.action_manage_semesters) {
            startActivity(new Intent(this, SemesterManageActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    private int getCurrentWeekNumber() {
        if (currentSemester == null) return 1;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long start = sdf.parse(currentSemester.startDate).getTime();
            long now = System.currentTimeMillis();
            long diff = now - start;
            int week = (int) (diff / (7L * 24 * 60 * 60 * 1000)) + 1;
            if (week < 1) week = 1;
            if (week > currentSemester.totalWeeks) week = currentSemester.totalWeeks;
            return week;
        } catch (Exception e) { return 1; }
    }

    private void loadSemesters() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<SemesterConfig> list = AppDatabase.getDatabase(this).semesterConfigDao().getAll();
            SemesterConfig active = AppDatabase.getDatabase(this).semesterConfigDao().getActive();
            runOnUiThread(() -> {
                semesters = list;
                String[] names = new String[semesters.size()];
                int selIdx = 0;
                for (int i = 0; i < semesters.size(); i++) { names[i] = semesters.get(i).name; if (semesters.get(i).id == (active != null ? active.id : -1)) selIdx = i; }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerSemester.setAdapter(adapter);
                if (!semesters.isEmpty()) { spinnerSemester.setSelection(selIdx); }
                else { currentSemester = null; showNoSemesterDialog(); }
            });
        });
    }

    private void showNoSemesterDialog() {
        new AlertDialog.Builder(this)
                .setTitle("还没有学期")
                .setMessage("请先创建一个学期，设置课程时间参数")
                .setPositiveButton("创建学期", (d, w) -> showSemesterEditDialog(null))
                .setNegativeButton("取消", null).show();
    }

    private void showManageSemestersDialog() {
        if (semesters.isEmpty()) { showNoSemesterDialog(); return; }
        String[] names = new String[semesters.size() + 1];
        for (int i = 0; i < semesters.size(); i++) names[i] = semesters.get(i).name;
        names[names.length - 1] = "+ 添加学期";
        new AlertDialog.Builder(this)
                .setTitle("管理学期")
                .setItems(names, (d, w) -> {
                    if (w == names.length - 1) showSemesterEditDialog(null);
                    else showSemesterOptionsDialog(semesters.get(w));
                }).setNegativeButton("关闭", null).show();
    }

    private void showSemesterOptionsDialog(SemesterConfig sc) {
        new AlertDialog.Builder(this).setTitle(sc.name)
                .setItems(new String[]{"设为活跃学期", "编辑", "删除"}, (d, w) -> {
                    if (w == 0) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            AppDatabase.getDatabase(this).semesterConfigDao().deactivateAll();
                            AppDatabase.getDatabase(this).semesterConfigDao().setActive(sc.id);
                            runOnUiThread(() -> { loadSemesters(); Toast.makeText(this, "已切换活跃学期", Toast.LENGTH_SHORT).show(); });
                        });
                    } else if (w == 1) showSemesterEditDialog(sc);
                    else {
                        new AlertDialog.Builder(this).setMessage("删除学期将同时删除该学期所有课程，确定？")
                                .setPositiveButton("删除", (dd, ww) -> {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        AppDatabase.getDatabase(this).courseEntryDao().deleteBySemester(sc.id);
                                        AppDatabase.getDatabase(this).semesterConfigDao().delete(sc);
                                        runOnUiThread(() -> loadSemesters());
                                    });
                                }).setNegativeButton("取消", null).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showSemesterEditDialog(SemesterConfig sc) {
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(40, 16, 40, 0);

        TextView lbName = newLb("学期名称"); ll.addView(lbName);
        android.widget.EditText etName = new android.widget.EditText(this); etName.setTextColor(Color.BLACK); etName.setHint("如：2026春季学期"); if (sc != null) etName.setText(sc.name); ll.addView(etName);

        TextView lbDate = newLb("开始日期"); ll.addView(lbDate);
        TextView tvDate = new android.widget.TextView(this); tvDate.setTextSize(15); tvDate.setTextColor(0xFF333333); tvDate.setPadding(12, 14, 12, 14); tvDate.setBackgroundColor(0xFFF5F5F5);
        String initDate = (sc != null && sc.startDate != null) ? sc.startDate : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        tvDate.setText(initDate);
        tvDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            try { java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(tvDate.getText().toString()); cal.setTime(d); } catch (Exception ignored) {}
            new android.app.DatePickerDialog(this, (view, y, m, d) -> tvDate.setText(String.format("%04d-%02d-%02d", y, m+1, d)), cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        ll.addView(tvDate);

        TextView lbWeeks = newLb("总周数"); ll.addView(lbWeeks);
        android.widget.EditText etWeeks = new android.widget.EditText(this); etWeeks.setTextColor(Color.BLACK); etWeeks.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etWeeks.setText(sc != null ? String.valueOf(sc.totalWeeks) : "18"); ll.addView(etWeeks);

        TextView lbDur = newLb("每节课时长（分钟）"); ll.addView(lbDur);
        android.widget.EditText etPdDur = new android.widget.EditText(this); etPdDur.setTextColor(Color.BLACK); etPdDur.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdDur.setText(sc != null ? String.valueOf(sc.periodDuration) : "45"); ll.addView(etPdDur);

        TextView lbBrk = newLb("课间休息（分钟）"); ll.addView(lbBrk);
        android.widget.EditText etPdBrk = new android.widget.EditText(this); etPdBrk.setTextColor(Color.BLACK); etPdBrk.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdBrk.setText(sc != null ? String.valueOf(sc.periodBreak) : "10"); ll.addView(etPdBrk);

        TextView lbFirst = newLb("第一节开始时间"); ll.addView(lbFirst);
        android.widget.EditText etFirst = new android.widget.EditText(this); etFirst.setTextColor(Color.BLACK); etFirst.setHint("如：08:00"); etFirst.setText(sc != null ? sc.firstPeriodStart : "08:00"); ll.addView(etFirst);

        TextView lbPerDay = newLb("每天节数"); ll.addView(lbPerDay);
        android.widget.EditText etPerDay = new android.widget.EditText(this); etPerDay.setTextColor(Color.BLACK); etPerDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPerDay.setText(sc != null ? String.valueOf(sc.periodsPerDay) : "12"); ll.addView(etPerDay);

        android.widget.CheckBox cbAdvanced = new android.widget.CheckBox(this); cbAdvanced.setText("高级：自定义每节课时间"); cbAdvanced.setTextSize(13); cbAdvanced.setTextColor(0xFF666666); cbAdvanced.setPadding(0, 16, 0, 0); ll.addView(cbAdvanced);
        android.widget.EditText etCustomPeriods = new android.widget.EditText(this); etCustomPeriods.setTextColor(Color.BLACK); etCustomPeriods.setHint("如：08:00-08:45,08:50-09:35,..."); etCustomPeriods.setMinLines(2);
        etCustomPeriods.setVisibility(View.GONE);
        boolean hasCustom = sc != null && sc.customPeriods != null && !sc.customPeriods.isEmpty();
        if (hasCustom) { etCustomPeriods.setText(sc.customPeriods); etCustomPeriods.setVisibility(View.VISIBLE); cbAdvanced.setChecked(true); }
        cbAdvanced.setOnCheckedChangeListener((btn, checked) -> etCustomPeriods.setVisibility(checked ? View.VISIBLE : View.GONE));
        ll.addView(etCustomPeriods);

        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(ll);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(sc == null ? "新建学期" : "编辑学期")
                .setView(sv)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            SemesterConfig cfg = sc != null ? sc : new SemesterConfig();
            cfg.name = etName.getText().toString().trim(); cfg.startDate = tvDate.getText().toString().trim();
            try { cfg.totalWeeks = Integer.parseInt(etWeeks.getText().toString().trim()); } catch (Exception e) { cfg.totalWeeks = 18; }
            try { cfg.periodDuration = Integer.parseInt(etPdDur.getText().toString().trim()); } catch (Exception e) { cfg.periodDuration = 45; }
            try { cfg.periodBreak = Integer.parseInt(etPdBrk.getText().toString().trim()); } catch (Exception e) { cfg.periodBreak = 10; }
            cfg.firstPeriodStart = etFirst.getText().toString().trim();
            try { cfg.periodsPerDay = Integer.parseInt(etPerDay.getText().toString().trim()); } catch (Exception e) { cfg.periodsPerDay = 12; }
            cfg.customPeriods = cbAdvanced.isChecked() ? etCustomPeriods.getText().toString().trim() : null;
            if (cfg.customPeriods != null && cfg.customPeriods.isEmpty()) cfg.customPeriods = null;
            if (cfg.name.isEmpty() || cfg.startDate.isEmpty()) { Toast.makeText(this, "名称和开始日期必填", Toast.LENGTH_SHORT).show(); return; }
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (sc == null) { long id = db.semesterConfigDao().insert(cfg); db.semesterConfigDao().deactivateAll(); db.semesterConfigDao().setActive((int) id); }
                else { db.semesterConfigDao().update(cfg); }
                runOnUiThread(() -> { loadSemesters(); dialog.dismiss(); });
            });
        });
    }

    private TextView newLb(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(13); tv.setTextColor(0xFF666666); tv.setPadding(0, 12, 0, 0); return tv;
    }

    private void loadCourses() {
        if (currentSemester == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CourseEntry> courses = AppDatabase.getDatabase(this).courseEntryDao().getAllBySemester(currentSemester.id);
            runOnUiThread(() -> { allCourses = courses; refreshTable(); });
        });
    }

    private void refreshTable() {
        if (currentSemester == null) return;
        tvWeekInfo.setText("第 " + currentWeek + " 周 / 共 " + currentSemester.totalWeeks + " 周");
        tableContainer.removeAllViews();

        int periods = currentSemester.periodsPerDay;
        int colWidth = 78;
        float density = getResources().getDisplayMetrics().density;
        int colW = (int) (colWidth * density);
        int timeColW = (int) (62 * density);

        // Header row: time label + 7 days
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView timeHeader = makeCell("", timeColW, 0xFFCCCCCC, 10);
        headerRow.addView(timeHeader);
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (String dn : dayNames) {
            TextView tv = makeCell(dn, colW, 0xFF888888, 12);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(tv);
        }
        tableContainer.addView(headerRow);

        // Period rows: custom or auto-generated
        java.util.List<String[]> periodTimes = new java.util.ArrayList<>();
        if (currentSemester.customPeriods != null && !currentSemester.customPeriods.isEmpty()) {
            for (String seg : currentSemester.customPeriods.split(",")) {
                String[] parts = seg.trim().split("-");
                if (parts.length == 2) periodTimes.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        } else {
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.US);
            try { java.util.Date base = tf.parse(currentSemester.firstPeriodStart); Calendar cal = Calendar.getInstance(); cal.setTime(base);
                int dur = currentSemester.periodDuration, brk = currentSemester.periodBreak;
                for (int i = 0; i < periods; i++) { String s = tf.format(cal.getTime()); cal.add(Calendar.MINUTE, dur); String e = tf.format(cal.getTime()); cal.add(Calendar.MINUTE, brk); periodTimes.add(new String[]{s, e}); }
            } catch (Exception ignored) {}
        }

        for (int pi = 0; pi < periodTimes.size(); pi++) {
            String[] times = periodTimes.get(pi);
            String start = times[0], end = times[1];
            int pNum = pi + 1; // 1-based period number

            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            String label = "第" + pNum + "节\n" + start + "~" + end;
            TextView lbl = makeCell(label, timeColW, 0xFF888888, 10); lbl.setPadding(4, 4, 4, 4); row.addView(lbl);

            for (int d = 0; d < 7; d++) {
                CourseEntry match = null;
                for (CourseEntry ce : allCourses) {
                    if (ce.dayOfWeek == d && ce.startPeriod <= pNum && pNum < ce.startPeriod + ce.periodCount && isActiveWeek(ce, currentWeek)) { match = ce; break; }
                }
                if (match != null) {
                    if (pNum == match.startPeriod) {
                        int spanH = (int) (match.periodCount * 56 * density);
                        TextView card = makeCourseCard(match); card.setHeight(spanH);
                        card.setLayoutParams(new LinearLayout.LayoutParams(colW, spanH)); row.addView(card);
                    }
                } else {
                    boolean covered = false;
                    for (CourseEntry ce : allCourses) { if (ce.dayOfWeek == d && ce.startPeriod < pNum && pNum < ce.startPeriod + ce.periodCount && isActiveWeek(ce, currentWeek)) { covered = true; break; } }
                    if (!covered) {
                        TextView empty = makeCell("", colW, 0, 11); empty.setBackgroundResource(android.R.drawable.list_selector_background);
                        final int dy = d, pr = pNum;
                        empty.setOnClickListener(v -> onCellClick(dy, pr)); row.addView(empty);
                    }
                }
            }
            tableContainer.addView(row);
            View div = new View(this); div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)); div.setBackgroundColor(0xFFE0E0E0); tableContainer.addView(div);
        }
    }

    private boolean isActiveWeek(CourseEntry ce, int week) {
        if ("EVERY".equals(ce.weekPattern)) return true;
        if ("ODD".equals(ce.weekPattern)) return week % 2 == 1;
        if ("EVEN".equals(ce.weekPattern)) return week % 2 == 0;
        if (ce.weekPattern == null || ce.weekPattern.isEmpty()) return true;
        // Custom pattern like "1,3-5,8"
        Set<Integer> weeks = parseWeekPattern(ce.weekPattern);
        return weeks.contains(week);
    }

    private Set<Integer> parseWeekPattern(String pattern) {
        Set<Integer> set = new HashSet<>();
        try {
            for (String part : pattern.split(",")) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for (int i = from; i <= to; i++) set.add(i);
                } else {
                    set.add(Integer.parseInt(part));
                }
            }
        } catch (Exception ignored) {}
        return set;
    }

    private void onCellClick(int dayOfWeek, int period) {
        if (currentSemester == null) return;
        Intent intent = new Intent(this, CourseEditActivity.class);
        intent.putExtra("semesterId", currentSemester.id);
        intent.putExtra("dayOfWeek", dayOfWeek);
        intent.putExtra("startPeriod", period);
        startActivity(intent);
    }

    private TextView makeCell(String text, int width, int bgColor, int textSize) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(textSize);
        tv.setTextColor(0xFF333333);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(2, 6, 2, 6);
        if (bgColor != 0) tv.setBackgroundColor(bgColor);
        return tv;
    }

    private TextView makeCourseCard(CourseEntry ce) {
        TextView tv = new TextView(this);
        int colW = (int) (78 * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(new LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setTextColor(0xFF333333);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(2, 4, 2, 4);

        StringBuilder sb = new StringBuilder(ce.name);
        if (ce.location != null && !ce.location.isEmpty()) sb.append("\n").append(ce.location);
        if (ce.teacher != null && !ce.teacher.isEmpty()) sb.append("\n").append(ce.teacher);
        tv.setText(sb.toString());
        tv.setBackgroundColor(ce.color);

        tv.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(ce.name)
                    .setItems(new String[]{"编辑", "删除"}, (d, w) -> {
                        if (w == 0) startActivity(new Intent(this, CourseEditActivity.class).putExtra("courseId", ce.id));
                        else new AlertDialog.Builder(this).setMessage("删除课程「" + ce.name + "」？")
                                .setPositiveButton("删除", (dd, ww) -> {
                                    Executors.newSingleThreadExecutor().execute(() -> { AppDatabase.getDatabase(this).courseEntryDao().delete(ce);
                                        runOnUiThread(this::loadCourses); });
                                }).setNegativeButton("取消", null).show();
                    }).setNegativeButton("关闭", null).show();
        });
        return tv;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSemesters();
    }
}
