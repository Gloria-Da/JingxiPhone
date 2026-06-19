package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.SemesterConfig;
import com.yoyo.jingxi.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SemesterManageActivity extends AppCompatActivity {

    private RecyclerView rv;
    private SemesterAdapter adapter;
    private List<SemesterConfig> semesters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semester_manage);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("学期管理");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvSemesters);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SemesterAdapter();
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showEditDialog(null));

        loadData();
    }

    private void loadData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<SemesterConfig> list = AppDatabase.getDatabase(this).semesterConfigDao().getAll();
            // Already sorted by startDate DESC from DAO, but let's sort by ID DESC for newest first
            java.util.Collections.sort(list, (a, b) -> Integer.compare(b.id, a.id));
            runOnUiThread(() -> { semesters = list; adapter.notifyDataSetChanged(); });
        });
    }

    private void showEditDialog(SemesterConfig sc) {
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(40, 16, 40, 0);

        TextView lbName = lb("学期名称"); ll.addView(lbName);
        EditText etName = new EditText(this); etName.setTextColor(Color.BLACK); etName.setHint("如：2026春季学期"); if (sc != null) etName.setText(sc.name); ll.addView(etName);

        TextView lbDate = lb("开始日期"); ll.addView(lbDate);
        TextView tvDate = new TextView(this); tvDate.setTextSize(15); tvDate.setTextColor(0xFF333333); tvDate.setPadding(12, 14, 12, 14); tvDate.setBackgroundColor(0xFFF5F5F5);
        String initDate = (sc != null && sc.startDate != null) ? sc.startDate : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        tvDate.setText(initDate);
        tvDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try { Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tvDate.getText().toString()); cal.setTime(d); } catch (Exception ignored) {}
            new android.app.DatePickerDialog(this, (view, y, m, d) -> tvDate.setText(String.format("%04d-%02d-%02d", y, m+1, d)), cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        ll.addView(tvDate);

        TextView lbWeeks = lb("总周数"); ll.addView(lbWeeks);
        EditText etWeeks = new EditText(this); etWeeks.setTextColor(Color.BLACK); etWeeks.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etWeeks.setText(sc != null ? String.valueOf(sc.totalWeeks) : "18"); ll.addView(etWeeks);

        TextView lbDur = lb("每节课时长（分钟）"); ll.addView(lbDur);
        EditText etPdDur = new EditText(this); etPdDur.setTextColor(Color.BLACK); etPdDur.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdDur.setText(sc != null ? String.valueOf(sc.periodDuration) : "45"); ll.addView(etPdDur);

        TextView lbBrk = lb("课间休息（分钟）"); ll.addView(lbBrk);
        EditText etPdBrk = new EditText(this); etPdBrk.setTextColor(Color.BLACK); etPdBrk.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdBrk.setText(sc != null ? String.valueOf(sc.periodBreak) : "10"); ll.addView(etPdBrk);

        TextView lbFirst = lb("第一节开始时间"); ll.addView(lbFirst);
        EditText etFirst = new EditText(this); etFirst.setTextColor(Color.BLACK); etFirst.setHint("如：08:00"); etFirst.setText(sc != null ? sc.firstPeriodStart : "08:00"); ll.addView(etFirst);

        TextView lbPerDay = lb("每天节数"); ll.addView(lbPerDay);
        EditText etPerDay = new EditText(this); etPerDay.setTextColor(Color.BLACK); etPerDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPerDay.setText(sc != null ? String.valueOf(sc.periodsPerDay) : "12"); ll.addView(etPerDay);

        // --- 高级设置按钮 ---
        final AlertDialog[] dialogRef = new AlertDialog[1];
        boolean hasCustom = sc != null && sc.customPeriods != null && !sc.customPeriods.isEmpty();
        Button btnAdvanced = new Button(this); btnAdvanced.setText(hasCustom ? "高级设置 ✓" : "高级设置"); btnAdvanced.setTextSize(13);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 20, 0, 0); btnAdvanced.setLayoutParams(btnLp);
        btnAdvanced.setOnClickListener(vv -> {
            if (sc != null) {
                startActivity(new Intent(this, PeriodSettingsActivity.class).putExtra("semesterId", sc.id));
            } else {
                SemesterConfig tmp = new SemesterConfig();
                tmp.name = etName.getText().toString().trim(); tmp.startDate = tvDate.getText().toString().trim();
                try { tmp.totalWeeks = Integer.parseInt(etWeeks.getText().toString().trim()); } catch (Exception e) { tmp.totalWeeks = 18; }
                try { tmp.periodDuration = Integer.parseInt(etPdDur.getText().toString().trim()); } catch (Exception e) { tmp.periodDuration = 45; }
                try { tmp.periodBreak = Integer.parseInt(etPdBrk.getText().toString().trim()); } catch (Exception e) { tmp.periodBreak = 10; }
                tmp.firstPeriodStart = etFirst.getText().toString().trim();
                try { tmp.periodsPerDay = Integer.parseInt(etPerDay.getText().toString().trim()); } catch (Exception e) { tmp.periodsPerDay = 12; }
                if (tmp.name.isEmpty() || tmp.startDate.isEmpty()) { Toast.makeText(this, "请先填写名称和开始日期", Toast.LENGTH_SHORT).show(); return; }
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getDatabase(this);
                    long id = db.semesterConfigDao().insert(tmp);
                    db.semesterConfigDao().deactivateAll();
                    db.semesterConfigDao().setActive((int) id);
                    runOnUiThread(() -> {
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        startActivity(new Intent(this, PeriodSettingsActivity.class).putExtra("semesterId", (int) id));
                    });
                });
            }
        });
        ll.addView(btnAdvanced);

        ScrollView sv = new ScrollView(this); sv.addView(ll);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(sc == null ? "新建学期" : "编辑学期").setView(sv).setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        dialogRef[0] = dialog;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            SemesterConfig cfg = sc != null ? sc : new SemesterConfig();
            cfg.name = etName.getText().toString().trim(); cfg.startDate = tvDate.getText().toString().trim();
            try { cfg.totalWeeks = Integer.parseInt(etWeeks.getText().toString().trim()); } catch (Exception e) { cfg.totalWeeks = 18; }
            try { cfg.periodDuration = Integer.parseInt(etPdDur.getText().toString().trim()); } catch (Exception e) { cfg.periodDuration = 45; }
            try { cfg.periodBreak = Integer.parseInt(etPdBrk.getText().toString().trim()); } catch (Exception e) { cfg.periodBreak = 10; }
            cfg.firstPeriodStart = etFirst.getText().toString().trim();
            try { cfg.periodsPerDay = Integer.parseInt(etPerDay.getText().toString().trim()); } catch (Exception e) { cfg.periodsPerDay = 12; }
            if (cfg.name.isEmpty() || cfg.startDate.isEmpty()) { Toast.makeText(this, "名称和开始日期必填", Toast.LENGTH_SHORT).show(); return; }
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (sc == null) { long id = db.semesterConfigDao().insert(cfg); db.semesterConfigDao().deactivateAll(); db.semesterConfigDao().setActive((int) id); }
                else db.semesterConfigDao().update(cfg);
                runOnUiThread(() -> { loadData(); dialog.dismiss(); });
            });
        });
    }

    private TextView lb(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(13); tv.setTextColor(0xFF666666); tv.setPadding(0, 12, 0, 0); return tv;
    }

    private static android.graphics.drawable.GradientDrawable roundedBtnBg(int color, float radiusDp, float density) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * density);
        gd.setColor(color);
        return gd;
    }

    private class SemesterAdapter extends RecyclerView.Adapter<SemesterAdapter.VH> {
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_semester, parent, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            SemesterConfig sc = semesters.get(pos);

            // Get theme colors
            int primaryColor = Color.rgb(63, 81, 181);
            try {
                TypedValue tv = new TypedValue();
                h.itemView.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
                primaryColor = tv.data;
            } catch (Exception ignored) {}
            // colorOnPrimary: dark text on light themes, white on dark
            int onPrimaryColor = ThemeManager.isDarkMode(h.itemView.getContext())
                ? Color.WHITE : Color.rgb(0x79, 0x55, 0x48); // text_brown

            h.tvName.setText(sc.name);

            // Info with emoji icons
            String info = "📅 " + sc.startDate + " 起  📊 " + sc.totalWeeks + "周 · 每天" + sc.periodsPerDay + "节";
            if (sc.customPeriods != null && !sc.customPeriods.isEmpty()) info += " · ⏱ 自定义时间";
            h.tvInfo.setText(info);

            // Status chip
            h.chipActive.setVisibility(sc.isActive ? View.VISIBLE : View.GONE);
            if (sc.isActive) h.chipActive.setBackgroundTintList(ColorStateList.valueOf(primaryColor));

            // Accent bar for current semester
            h.vAccentBar.setVisibility(sc.isActive ? View.VISIBLE : View.INVISIBLE);
            if (sc.isActive) h.vAccentBar.setBackgroundColor(primaryColor);

            // Action button - filled pill style
            float density = h.itemView.getContext().getResources().getDisplayMetrics().density;
            h.btnSetActive.setVisibility(sc.isActive ? View.GONE : View.VISIBLE);
            h.btnSetActive.setBackground(roundedBtnBg(primaryColor, 6f, density));
            h.btnSetActive.setTextColor(onPrimaryColor);
            h.btnSetActive.setOnClickListener(v -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getDatabase(SemesterManageActivity.this).semesterConfigDao().deactivateAll();
                    AppDatabase.getDatabase(SemesterManageActivity.this).semesterConfigDao().setActive(sc.id);
                    runOnUiThread(() -> { loadData(); Toast.makeText(SemesterManageActivity.this, "已设为当前学期", Toast.LENGTH_SHORT).show(); });
                });
            });
            h.itemView.setOnClickListener(v -> showEditDialog(sc));
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(SemesterManageActivity.this).setMessage("删除学期「" + sc.name + "」及所有课程？")
                        .setPositiveButton("删除", (d, w) -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                AppDatabase.getDatabase(SemesterManageActivity.this).courseEntryDao().deleteBySemester(sc.id);
                                AppDatabase.getDatabase(SemesterManageActivity.this).semesterConfigDao().delete(sc);
                                runOnUiThread(SemesterManageActivity.this::loadData);
                            });
                        }).setNegativeButton("取消", null).show();
                return true;
            });
        }
        @Override public int getItemCount() { return semesters != null ? semesters.size() : 0; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo, btnSetActive, chipActive;
            View vAccentBar;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvInfo = v.findViewById(R.id.tvInfo); btnSetActive = v.findViewById(R.id.btnSetActive); chipActive = v.findViewById(R.id.chipActive); vAccentBar = v.findViewById(R.id.vAccentBar); }
        }
    }
}
