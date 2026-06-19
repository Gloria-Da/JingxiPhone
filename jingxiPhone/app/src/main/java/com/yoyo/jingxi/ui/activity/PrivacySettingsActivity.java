package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.utils.ThemeManager;
import com.yoyo.jingxi.utils.UserContextSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * AI 数据感知隐私设置页面。
 * 三类数据（日历/课程/经期）分别控制对哪些 AI 角色、在哪些人设下可见。
 */
public class PrivacySettingsActivity extends AppCompatActivity {

    private UserContextSettings settings;

    // Calendar
    private SwitchMaterial switchCalendar;
    private TextView tvCalendarChars, tvCalendarPersona;

    // Course
    private SwitchMaterial switchCourse;
    private TextView tvCourseChars, tvCoursePersona;

    // Period
    private SwitchMaterial switchPeriod;
    private TextView tvPeriodChars, tvPeriodPersona;

    private List<String> allPersonaNames = new ArrayList<>();
    private List<Character> allCharacters = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_settings);

        settings = UserContextSettings.load();

        setupToolbar();
        bindViews();
        loadData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("AI 数据感知设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        // Calendar
        switchCalendar = findViewById(R.id.switchCalendar);
        tvCalendarChars = findViewById(R.id.tvCalendarChars);
        tvCalendarPersona = findViewById(R.id.tvCalendarPersona);
        View btnCalendarChars = findViewById(R.id.btnCalendarChars);
        View btnCalendarPersona = findViewById(R.id.btnCalendarPersona);

        // Course
        switchCourse = findViewById(R.id.switchCourse);
        tvCourseChars = findViewById(R.id.tvCourseChars);
        tvCoursePersona = findViewById(R.id.tvCoursePersona);
        View btnCourseChars = findViewById(R.id.btnCourseChars);
        View btnCoursePersona = findViewById(R.id.btnCoursePersona);

        // Period
        switchPeriod = findViewById(R.id.switchPeriod);
        tvPeriodChars = findViewById(R.id.tvPeriodChars);
        tvPeriodPersona = findViewById(R.id.tvPeriodPersona);
        View btnPeriodChars = findViewById(R.id.btnPeriodChars);
        View btnPeriodPersona = findViewById(R.id.btnPeriodPersona);

        // Switches
        switchCalendar.setOnCheckedChangeListener((btn, checked) -> {
            settings.setEnabled("calendar", checked);
            settings.save();
        });
        switchCourse.setOnCheckedChangeListener((btn, checked) -> {
            settings.setEnabled("course", checked);
            settings.save();
        });
        switchPeriod.setOnCheckedChangeListener((btn, checked) -> {
            settings.setEnabled("period", checked);
            settings.save();
        });

        // Character editors
        btnCalendarChars.setOnClickListener(v -> showCharacterPicker("calendar"));
        btnCourseChars.setOnClickListener(v -> showCharacterPicker("course"));
        btnPeriodChars.setOnClickListener(v -> showCharacterPicker("period"));

        // Persona editors
        btnCalendarPersona.setOnClickListener(v -> showPersonaPicker("calendar"));
        btnCoursePersona.setOnClickListener(v -> showPersonaPicker("course"));
        btnPeriodPersona.setOnClickListener(v -> showPersonaPicker("period"));

        // Apply semester-style button backgrounds
        for (View btn : new View[]{btnCalendarChars, btnCalendarPersona,
                btnCourseChars, btnCoursePersona,
                btnPeriodChars, btnPeriodPersona}) {
            styleActionButton(btn);
        }
    }

    private void loadData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);

            // 加载角色列表
            List<Character> chars = db.characterDao().getAllCharactersSync();
            allCharacters.clear();
            if (chars != null) allCharacters.addAll(chars);

            // 加载人设列表
            List<MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
            allPersonaNames.clear();
            allPersonaNames.add("默认（主人设）");
            if (personas != null) {
                for (MyPersona p : personas) {
                    if (p.name != null && !p.name.isEmpty()) {
                        allPersonaNames.add(p.name);
                    }
                }
            }

            runOnUiThread(this::refreshUI);
        });
    }

    private void refreshUI() {
        // Calendar
        UserContextSettings.CategoryConfig calCfg = settings.getConfig("calendar");
        switchCalendar.setChecked(calCfg.enabled);
        tvCalendarChars.setText(formatCharacterNames(calCfg.characterIds));
        tvCalendarPersona.setText(formatPersonaName(calCfg.persona));

        // Course
        UserContextSettings.CategoryConfig courseCfg = settings.getConfig("course");
        switchCourse.setChecked(courseCfg.enabled);
        tvCourseChars.setText(formatCharacterNames(courseCfg.characterIds));
        tvCoursePersona.setText(formatPersonaName(courseCfg.persona));

        // Period
        UserContextSettings.CategoryConfig periodCfg = settings.getConfig("period");
        switchPeriod.setChecked(periodCfg.enabled);
        tvPeriodChars.setText(formatCharacterNames(periodCfg.characterIds));
        tvPeriodPersona.setText(formatPersonaName(periodCfg.persona));
    }

    private String formatCharacterNames(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "未选择";
        if (allCharacters.isEmpty()) return ids.size() + " 个角色";

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Character c : allCharacters) {
            if (ids.contains(c.id)) {
                if (count > 0) sb.append(", ");
                sb.append(c.name);
                count++;
                if (count >= 3) break; // 最多显示3个
            }
        }
        int remaining = ids.size() - count;
        if (remaining > 0) sb.append(" 等").append(remaining).append("个");
        return sb.toString();
    }

    private String formatPersonaName(String persona) {
        if (persona == null || persona.isEmpty()) return "默认（主人设）";
        return persona;
    }

    private void showCharacterPicker(String category) {
        if (allCharacters.isEmpty()) {
            Toast.makeText(this, "暂无角色数据", Toast.LENGTH_SHORT).show();
            return;
        }

        UserContextSettings.CategoryConfig cfg = settings.getConfig(category);
        Set<Integer> selected = new HashSet<>(cfg.characterIds);

        String[] names = new String[allCharacters.size()];
        boolean[] checked = new boolean[allCharacters.size()];
        int[] charIds = new int[allCharacters.size()];

        for (int i = 0; i < allCharacters.size(); i++) {
            Character c = allCharacters.get(i);
            names[i] = c.name;
            charIds[i] = c.id;
            checked[i] = selected.contains(c.id);
        }

        new AlertDialog.Builder(this)
            .setTitle("选择可见角色")
            .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                if (isChecked) selected.add(charIds[which]);
                else selected.remove(charIds[which]);
            })
            .setPositiveButton("确定", (dialog, which) -> {
                settings.setCharacterIds(category, new ArrayList<>(selected));
                settings.save();
                refreshUI();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showPersonaPicker(String category) {
        UserContextSettings.CategoryConfig cfg = settings.getConfig(category);
        int currentIdx = 0; // 默认=0
        String currentPersona = cfg.persona;
        if (currentPersona != null && !currentPersona.isEmpty()) {
            for (int i = 0; i < allPersonaNames.size(); i++) {
                if (allPersonaNames.get(i).equals(currentPersona)) {
                    currentIdx = i;
                    break;
                }
            }
        }

        String[] items = allPersonaNames.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("选择人设")
            .setSingleChoiceItems(items, currentIdx, (dialog, which) -> {
                String selected = items[which];
                if ("默认（主人设）".equals(selected)) {
                    settings.setPersona(category, "");
                } else {
                    settings.setPersona(category, selected);
                }
                settings.save();
                refreshUI();
                dialog.dismiss();
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    /**
     * 给按钮应用实心圆角背景 + colorOnPrimary 文字，风格与学期管理按钮一致。
     */
    private void styleActionButton(View btn) {
        if (!(btn instanceof TextView)) return;

        // 获取主题 primaryColor
        int primaryColor = Color.rgb(63, 81, 181);
        try {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
            primaryColor = tv.data;
        } catch (Exception ignored) {}

        // colorOnPrimary: light themes → text_brown, dark → white
        int onPrimaryColor = ThemeManager.isDarkMode(this)
            ? Color.WHITE : Color.rgb(0x79, 0x55, 0x48);

        float density = getResources().getDisplayMetrics().density;
        btn.setBackground(roundedBtnBg(primaryColor, 6f, density));
        ((TextView) btn).setTextColor(onPrimaryColor);
    }

    private static GradientDrawable roundedBtnBg(int color, float radiusDp, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * density);
        gd.setColor(color);
        return gd;
    }
}
