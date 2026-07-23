package com.yoyo.jingxi.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Looper;
import android.view.View;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.dao.CalendarEventDao;
import com.yoyo.jingxi.data.dao.CycleRecordDao;
import com.yoyo.jingxi.data.dao.HolidayCacheDao;
import com.yoyo.jingxi.data.entity.CalendarEvent;
import com.yoyo.jingxi.data.entity.CourseEntry;
import com.yoyo.jingxi.data.entity.CycleRecord;
import com.yoyo.jingxi.data.entity.HolidayCache;
import com.yoyo.jingxi.data.entity.SemesterConfig;
import com.yoyo.jingxi.network.OpenAiApi;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.ui.adapter.CalendarGridAdapter;
import com.yoyo.jingxi.ui.adapter.CalendarDayDetailAdapter;
import com.yoyo.jingxi.ui.adapter.CalendarMonthPagerAdapter;
import com.yoyo.jingxi.ui.model.CalendarDayItem;
import com.yoyo.jingxi.ui.widget.MonthCalendarView;
import com.yoyo.jingxi.utils.CyclePredictor;
import com.yoyo.jingxi.utils.HolidayFetcher;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.ThemeManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CalendarActivity extends AppCompatActivity {

    private static final int MODE_ALL = 0;
    private static final int MODE_CYCLE = 1;
    private static final int MODE_EVENTS = 2;

    // Tab
    private View tabBar, calendarContent, courseContent;
    private TextView tabCalendar, tabCourse;
    private boolean isCourseTab = false;

    // Calendar
    private ViewPager2 monthViewPager;
    private CalendarMonthPagerAdapter pagerAdapter;
    private TextView tvMonthTitle;
    private TextView tvSelectedDate;
    private View selectedDateHeader;
    private RecyclerView rvDayDetail;
    private ChipGroup chipGroup;
    private FloatingActionButton fabAdd;

    // Course schedule
    private Spinner spinnerSemester;
    private TextView tvWeekInfo;
    private GridLayout tableContainer;
    private List<SemesterConfig> semesters = new ArrayList<>();
    private SemesterConfig currentSemester;
    private int currentWeek = 1;
    private List<CourseEntry> allCourses = new ArrayList<>();
    private static final int[] COURSE_COLORS = {0xFFBBDEFB,0xFFC8E6C9,0xFFFFCDD2,0xFFFFF9C4,0xFFE1BEE7,0xFFFFE0B2};

    // Image import
    private File pendingCameraFile;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private CalendarEventDao eventDao;
    private CycleRecordDao cycleDao;
    private HolidayCacheDao holidayDao;
    private CalendarDayDetailAdapter detailAdapter;

    private int currentYear;
    private int currentMonth;
    private int currentMode = MODE_ALL;
    private String selectedDateStr;
    private boolean isPageChanging = false; // 防止循环触发

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy年 M月", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        AppDatabase db = AppDatabase.getDatabase(this);
        eventDao = db.calendarEventDao();
        cycleDao = db.cycleRecordDao();
        holidayDao = db.holidayCacheDao();

        setupToolbar();
        setupViews();

        Calendar today = Calendar.getInstance();
        currentYear = today.get(Calendar.YEAR);
        currentMonth = today.get(Calendar.MONTH);

        // ViewPager2 setup
        pagerAdapter = new CalendarMonthPagerAdapter();
        monthViewPager.setAdapter(pagerAdapter);
        monthViewPager.setOffscreenPageLimit(2);
        int startPos = CalendarMonthPagerAdapter.yearMonthToPosition(currentYear, currentMonth);
        monthViewPager.setCurrentItem(startPos, false);

        updateMonthTitle();
        setupListeners();
        setupImageImportLaunchers();

        // Page change callback
        monthViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (isPageChanging) return;
                int[] ym = CalendarMonthPagerAdapter.positionToYearMonth(position);
                currentYear = ym[0];
                currentMonth = ym[1];
                updateMonthTitle();
                selectedDateStr = null;
                selectedDateHeader.setVisibility(View.GONE);
                detailAdapter.clear();
                refreshDayMarkers();
                HolidayFetcher.fetchIfNeeded(currentYear, AppDatabase.getDatabase(CalendarActivity.this),
                        CalendarActivity.this::refreshDayMarkers);
                // Select today if in this month
                selectTodayInCurrentMonth();
            }
        });

        // 初次进入时自动选中今天（post 等待 ViewPager 布局完成）
        monthViewPager.post(() -> selectTodayInCurrentMonth());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDayMarkers();
        loadDayDetail();
        checkPeriodDailyPopup();
    }

    /**
     * 经期期间每天首次进入日历时弹窗询问是否记录经期详情。
     */
    private void checkPeriodDailyPopup() {
        String todayStr = dateFormat.format(new java.util.Date());
        String shownDate = SpUtils.getString("PERIOD_POPUP_SHOWN_DATE", "");
        if (todayStr.equals(shownDate)) return; // 今天已弹过

        Executors.newSingleThreadExecutor().execute(() -> {
            CycleRecord cr = cycleDao.getPeriodOnDate(todayStr);
            if (cr == null) return;
            // 计算是第几天
            int dayNum = 1;
            try {
                java.util.Date start = dateFormat.parse(cr.startDate);
                java.util.Date today = dateFormat.parse(todayStr);
                long diff = today.getTime() - start.getTime();
                dayNum = (int) (diff / (1000 * 60 * 60 * 24)) + 1;
            } catch (Exception ignored) {}

            final int finalDay = dayNum;
            android.os.Handler mainHandler = new android.os.Handler(getMainLooper());
            mainHandler.post(() -> {
                SpUtils.putString("PERIOD_POPUP_SHOWN_DATE", todayStr);
                new AlertDialog.Builder(CalendarActivity.this)
                    .setTitle("经期关怀")
                    .setMessage("今天是经期第" + finalDay + "天，要记录今天的经期状况吗？")
                    .setPositiveButton("去记录", (dialog, which) -> {
                        Intent intent = new Intent(CalendarActivity.this, CycleEditActivity.class);
                        intent.putExtra("date", todayStr);
                        if (cr.id > 0) intent.putExtra("recordId", cr.id);
                        startActivity(intent);
                    })
                    .setNegativeButton("不需要", null)
                    .show();
            });
        });
    }

    private MonthCalendarView getCurrentMonthView() {
        // ViewPager2 uses a RecyclerView internally, find the current month view
        RecyclerView rv = (RecyclerView) monthViewPager.getChildAt(0);
        if (rv != null) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                if (child instanceof MonthCalendarView) {
                    int[] ym = CalendarMonthPagerAdapter.positionToYearMonth(monthViewPager.getCurrentItem());
                    MonthCalendarView mv = (MonthCalendarView) child;
                    if (mv.getCurrentYear() == ym[0] && mv.getCurrentMonth() == ym[1]) {
                        return mv;
                    }
                }
            }
        }
        return null;
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_calendar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        tvMonthTitle = findViewById(R.id.tvMonthTitle);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        selectedDateHeader = findViewById(R.id.selectedDateHeader);
        rvDayDetail = findViewById(R.id.rvDayDetail);
        chipGroup = findViewById(R.id.chipGroup);
        fabAdd = findViewById(R.id.fabAdd);
        monthViewPager = findViewById(R.id.monthViewPager);

        // Tab + course views
        tabCalendar = findViewById(R.id.tabCalendar);
        tabCourse = findViewById(R.id.tabCourse);
        calendarContent = findViewById(R.id.calendarContent);
        courseContent = findViewById(R.id.courseContent);
        spinnerSemester = findViewById(R.id.spinnerSemester);
        tvWeekInfo = findViewById(R.id.tvWeekInfo);
        tableContainer = findViewById(R.id.tableContainer);

        rvDayDetail.setLayoutManager(new LinearLayoutManager(this));
        detailAdapter = new CalendarDayDetailAdapter();
        detailAdapter.setOnItemClickListener(new CalendarDayDetailAdapter.OnItemClickListener() {
            @Override
            public void onEventClick(CalendarEvent event) {
                Intent intent = new Intent(CalendarActivity.this, EventEditActivity.class);
                intent.putExtra("eventId", event.id);
                startActivity(intent);
            }
            @Override
            public void onCycleClick(CycleRecord record) {
                Intent intent = new Intent(CalendarActivity.this, CycleEditActivity.class);
                intent.putExtra("recordId", record.id);
                startActivity(intent);
            }
        });
        rvDayDetail.setAdapter(detailAdapter);
    }

    private void setupListeners() {
        TextView btnPrev = findViewById(R.id.btnPrevMonth);
        TextView btnNext = findViewById(R.id.btnNextMonth);
        btnPrev.setOnClickListener(v -> {
            int pos = monthViewPager.getCurrentItem() - 1;
            smoothSetCurrentItem(pos, 500);
        });
        btnNext.setOnClickListener(v -> {
            int pos = monthViewPager.getCurrentItem() + 1;
            smoothSetCurrentItem(pos, 500);
        });

        tvMonthTitle.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            int pos = CalendarMonthPagerAdapter.yearMonthToPosition(today.get(Calendar.YEAR), today.get(Calendar.MONTH));
            smoothSetCurrentItem(pos, 500);
        });

        // Day click / long-press / overflow are set on each month view via adapter
        pagerAdapter.setOnDayClickListener(item -> {
            if (item.isCurrentMonth) selectDay(item);
        });
        pagerAdapter.setOnDayLongClickListener(item -> {
            if (item.isCurrentMonth) {
                selectDay(item);
                showDayLongPressMenu(item);
            }
        });
        pagerAdapter.setOnOverflowDayClickListener(item -> {
            int pos = CalendarMonthPagerAdapter.yearMonthToPosition(item.year, item.month);
            isPageChanging = true;
            smoothSetCurrentItem(pos, 500);
            // Select the clicked day after transition
            monthViewPager.postDelayed(() -> {
                isPageChanging = false;
                MonthCalendarView mv = getCurrentMonthView();
                if (mv != null) {
                    for (CalendarDayItem d : mv.getDayItems()) {
                        if (d.year == item.year && d.month == item.month && d.dayOfMonth == item.dayOfMonth && d.isCurrentMonth) {
                            selectDay(d);
                            break;
                        }
                    }
                }
            }, 400);
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipCycle) currentMode = MODE_CYCLE;
            else if (id == R.id.chipEvents) currentMode = MODE_EVENTS;
            else currentMode = MODE_ALL;
            refreshDayMarkers();
            loadDayDetail();
        });

        // Tab switching
        tabCalendar.setOnClickListener(v -> switchTab(false));
        tabCourse.setOnClickListener(v -> switchTab(true));

        // Course week nav
        findViewById(R.id.btnPrevWeek).setOnClickListener(v -> { if (currentWeek > 1) { currentWeek--; refreshCourseTable(); } });
        findViewById(R.id.btnNextWeek).setOnClickListener(v -> { if (currentSemester != null && currentWeek < currentSemester.totalWeeks) { currentWeek++; refreshCourseTable(); } });

        findViewById(R.id.btnManageSemesters).setOnClickListener(v -> startActivity(new Intent(this, SemesterManageActivity.class)));

        spinnerSemester.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < semesters.size()) { currentSemester = semesters.get(pos); currentWeek = getCurrentWeekNumber(); loadCourseEntries(); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        fabAdd.setOnClickListener(v -> { if (!isCourseTab) showAddDialog(); else showBatchImportDialog(); });
        fabAdd.setOnLongClickListener(v -> {
            showQuickMarkDialog(selectedDateStr);
            return true;
        });
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calendar, menu);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_ai_privacy) {
            startActivity(new Intent(this, PrivacySettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMonthTitle() {
        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth, 1);
        tvMonthTitle.setText(monthTitleFormat.format(cal.getTime()));
    }

    private void selectTodayInCurrentMonth() {
        MonthCalendarView mv = getCurrentMonthView();
        if (mv == null) return;
        Calendar today = Calendar.getInstance();
        if (today.get(Calendar.YEAR) == currentYear && today.get(Calendar.MONTH) == currentMonth) {
            for (CalendarDayItem item : mv.getDayItems()) {
                if (item.isToday && item.isCurrentMonth) { selectDay(item); return; }
            }
        }
        for (CalendarDayItem item : mv.getDayItems()) {
            if (item.isCurrentMonth && item.dayOfMonth == 1) { selectDay(item); return; }
        }
    }

    private void selectDay(CalendarDayItem item) {
        selectedDateStr = item.getDateString();
        tvSelectedDate.setText(formatSelectedDate(item));
        selectedDateHeader.setVisibility(View.VISIBLE);
        // Mark selected in the current month view
        MonthCalendarView mv = getCurrentMonthView();
        if (mv != null) mv.selectDay(item);
        loadDayDetail();
    }

    // === Tab switching ===

    private void switchTab(boolean toCourse) {
        isCourseTab = toCourse;
        if (toCourse) {
            // Switch to course tab
            tabCalendar.setTextColor(0xFF888888); tabCalendar.setTypeface(null, Typeface.NORMAL);
            tabCourse.setTextColor(Color.rgb(63, 81, 181)); tabCourse.setTypeface(null, Typeface.BOLD);
            calendarContent.setVisibility(View.GONE);
            courseContent.setVisibility(View.VISIBLE);
            chipGroup.setVisibility(View.GONE);
            fabAdd.setImageResource(android.R.drawable.ic_input_add);
            loadSemesters();
        } else {
            tabCalendar.setTextColor(Color.rgb(63, 81, 181)); tabCalendar.setTypeface(null, Typeface.BOLD);
            tabCourse.setTextColor(0xFF888888); tabCourse.setTypeface(null, Typeface.NORMAL);
            calendarContent.setVisibility(View.VISIBLE);
            courseContent.setVisibility(View.GONE);
            chipGroup.setVisibility(View.VISIBLE);
            fabAdd.setImageResource(android.R.drawable.ic_input_add);
        }
    }

    // === Course Schedule Logic ===

    private void loadSemesters() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<SemesterConfig> list = AppDatabase.getDatabase(this).semesterConfigDao().getAll();
            SemesterConfig active = AppDatabase.getDatabase(this).semesterConfigDao().getActive();
            runOnUiThread(() -> {
                semesters = list;
                if (semesters.isEmpty()) {
                    currentSemester = null;
                    spinnerSemester.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"(无学期)"}));
                    showNoSemesterDialog();
                } else {
                    String[] names = new String[semesters.size()];
                    int selIdx = 0;
                    for (int i = 0; i < semesters.size(); i++) {
                        names[i] = semesters.get(i).name;
                        if (semesters.get(i).id == (active != null ? active.id : -1)) selIdx = i;
                    }
                    ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
                    aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerSemester.setAdapter(aa);
                    spinnerSemester.setSelection(selIdx);
                }
            });
        });
    }

    private void showNoSemesterDialog() {
        new AlertDialog.Builder(this).setTitle("还没有学期").setMessage("请先创建一个学期")
                .setPositiveButton("创建学期", (d,w) -> showSemesterEditDialog(null))
                .setNegativeButton("取消", null).show();
    }

    private void loadCourseEntries() {
        if (currentSemester == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CourseEntry> courses = AppDatabase.getDatabase(this).courseEntryDao().getAllBySemester(currentSemester.id);
            runOnUiThread(() -> { allCourses = courses; refreshCourseTable(); });
        });
    }

    private int getCurrentWeekNumber() {
        if (currentSemester == null) return 1;
        try { java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long start = sdf.parse(currentSemester.startDate).getTime(); long now = System.currentTimeMillis();
            int w = (int)((now - start) / (7L*24*60*60*1000)) + 1;
            if (w < 1) w = 1; if (w > currentSemester.totalWeeks) w = currentSemester.totalWeeks; return w;
        } catch (Exception e) { return 1; }
    }

    private void refreshCourseTable() {
        if (currentSemester == null) return;

        // --- Compute date range for current week ---
        String dateRange = "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date semStart = sdf.parse(currentSemester.startDate);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(semStart);
            cal.add(java.util.Calendar.DAY_OF_YEAR, (currentWeek - 1) * 7);
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("M/d", java.util.Locale.US);
            String mon = outFmt.format(cal.getTime());
            cal.add(java.util.Calendar.DAY_OF_YEAR, 6);
            String sun = outFmt.format(cal.getTime());
            dateRange = "  " + mon + "-" + sun;
        } catch (Exception ignored) {}
        tvWeekInfo.setText("第 " + currentWeek + " 周" + dateRange + " / 共 " + currentSemester.totalWeeks + " 周");

        tableContainer.removeAllViews();
        int periods = currentSemester.periodsPerDay;
        float density = getResources().getDisplayMetrics().density;
        int rowH = (int)(58 * density);

        // --- Today highlight setup ---
        java.util.Calendar today = java.util.Calendar.getInstance();
        // Map Calendar.DAY_OF_WEEK to 0=Mon…6=Sun
        int todayDow = (today.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7;
        // Calculate raw (unclamped) week: only highlight if today actually falls within the semester
        boolean isCurrentWeek = false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long start = sdf.parse(currentSemester.startDate).getTime();
            int rawWeek = (int)((System.currentTimeMillis() - start) / (7L*24*60*60*1000)) + 1;
            isCurrentWeek = (rawWeek >= 1 && rawWeek <= currentSemester.totalWeeks && currentWeek == rawWeek);
        } catch (Exception ignored) {}

        // Get theme primary color for today highlight
        int primaryColor = 0xFFBBDEFB; // fallback
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
            primaryColor = tv.data;
        } catch (Exception ignored) {}
        int todayHeaderBg = (primaryColor & 0x00FFFFFF) | 0x99000000; // ~60% alpha
        int todayCellTint = (primaryColor & 0x00FFFFFF) | 0x20000000; // ~12% alpha

        // Parse period times
        java.util.List<String[]> periodTimes = new java.util.ArrayList<>();
        if (currentSemester.customPeriods != null && !currentSemester.customPeriods.isEmpty()) {
            for (String seg : currentSemester.customPeriods.split(",")) {
                String[] parts = seg.trim().split("-");
                if (parts.length == 2) periodTimes.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        } else {
            java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
            try { java.util.Date base = tf.parse(currentSemester.firstPeriodStart); java.util.Calendar cal = java.util.Calendar.getInstance(); cal.setTime(base); int dur = currentSemester.periodDuration, brk = currentSemester.periodBreak;
                for (int p = 0; p < periods; p++) { String s = tf.format(cal.getTime()); cal.add(java.util.Calendar.MINUTE, dur); String e = tf.format(cal.getTime()); cal.add(java.util.Calendar.MINUTE, brk); periodTimes.add(new String[]{s, e}); }
            } catch (Exception ignored) {}
        }
        int actualPeriods = periodTimes.size();

        // Column specs: col 0 = time (weight 0.7), col 1-7 = days (weight 1.0)
        GridLayout.Spec timeColSpec = GridLayout.spec(0, 1, 0.7f);

        // Styling colors
        int timeColBg = (primaryColor & 0x00FFFFFF) | 0x18000000; // ~9% alpha for time column
        int headerBg = 0xFFFAFAFA;
        int cardPad = (int)(4 * density);
        float cardRadius = 8f;

        // --- Header row (row 0) ---
        {
            TextView th = weightCell("", 0, 10);
            th.setLayoutParams(makeGridLp(0, 1, 0, 1, 0.7f, rowH));
            th.setBackgroundColor(timeColBg);
            tableContainer.addView(th);
            String[] dayNames = {"周一","周二","周三","周四","周五","周六","周日"};
            for (int d = 0; d < 7; d++) {
                boolean isTodayCol = isCurrentWeek && d == todayDow;
                TextView tv = weightCellPd(dayNames[d], isTodayCol ? todayHeaderBg : headerBg, 13, 2);
                tv.setTypeface(null, Typeface.BOLD);
                if (isTodayCol) { tv.setTextColor(0xFFFFFFFF); }
                else { tv.setTextColor(0xFF555555); }
                tv.setLayoutParams(makeGridLp(0, 1, d + 1, 1, 1f, rowH));
                tableContainer.addView(tv);
            }
        }

        // Track cells already covered by a rowSpan from above
        boolean[][] occupied = new boolean[actualPeriods][7];

        // --- Data rows ---
        for (int p = 0; p < actualPeriods; p++) {
            int gridRow = p + 1;
            String[] times = periodTimes.get(p);

            // Time label (column 0, rowSpan=1)
            TextView timeLbl = weightCellPd("第"+(p+1)+"节 "+times[0]+"~"+times[1], 0, 10, 2);
            timeLbl.setTextColor(0xFF666666); timeLbl.setGravity(Gravity.CENTER);
            timeLbl.setBackgroundColor(timeColBg);
            timeLbl.setLayoutParams(makeGridLp(gridRow, 1, 0, 1, 0.7f, rowH));
            tableContainer.addView(timeLbl);

            // Day columns
            for (int d = 0; d < 7; d++) {
                if (occupied[p][d]) continue; // Already covered by rowSpan above

                CourseEntry match = null;
                for (CourseEntry ce : allCourses) {
                    if (ce.dayOfWeek == d && ce.startPeriod <= (p+1) && (p+1) < ce.startPeriod + ce.periodCount && isCourseActiveWeek(ce)) { match = ce; break; }
                }

                if (match != null) {
                    int span = Math.min(match.periodCount, actualPeriods - p);
                    for (int r = p; r < p + span; r++) occupied[r][d] = true;

                    // Course card with rounded corners + elevation
                    TextView card = weightCellPd(formatCourseText(match), 0, 10, 4);
                    card.setTextColor(0xFF333333); card.setGravity(Gravity.CENTER);
                    card.setBackground(roundedBg(match.color, cardRadius));
                    card.setElevation(2 * density);
                    GridLayout.LayoutParams cardLp = new GridLayout.LayoutParams(
                        GridLayout.spec(gridRow, span), GridLayout.spec(d + 1, 1, 1f));
                    cardLp.width = 0; cardLp.height = 0;
                    cardLp.setGravity(Gravity.FILL);
                    cardLp.setMargins(2, 2, 2, 2); // small gap between cards
                    card.setLayoutParams(cardLp);
                    final CourseEntry cc = match;
                    card.setOnClickListener(v -> showCourseOptions(cc));
                    tableContainer.addView(card);
                } else {
                    boolean isTodayCol = isCurrentWeek && d == todayDow;
                    FrameLayout cell = new FrameLayout(this);
                    cell.setLayoutParams(makeGridLp(gridRow, 1, d + 1, 1, 1f, rowH));
                    cell.setBackground(cellBorder());
                    if (isTodayCol) cell.setBackgroundColor(todayCellTint);
                    final int dy = d, pr = p + 1;
                    cell.setOnClickListener(vv -> onCourseCellClick(dy, pr));
                    tableContainer.addView(cell);
                }
            }
        }
    }

    /** Create GridLayout.LayoutParams with explicit row/col specs and pixel height */
    private GridLayout.LayoutParams makeGridLp(int row, int rowSpan, int col, int colSpan, float colWeight, int heightPx) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
            GridLayout.spec(row, rowSpan), GridLayout.spec(col, colSpan, colWeight));
        lp.width = 0; lp.height = heightPx;
        lp.setGravity(Gravity.FILL);
        return lp;
    }

    private String formatCourseText(CourseEntry ce) {
        StringBuilder sb = new StringBuilder(ce.name);
        if (ce.location != null && !ce.location.isEmpty()) sb.append("\n").append(ce.location);
        if (ce.teacher != null && !ce.teacher.isEmpty()) sb.append("\n").append(ce.teacher);
        return sb.toString();
    }

    private void showCourseOptions(CourseEntry ce) {
        new AlertDialog.Builder(this).setTitle(ce.name).setItems(new String[]{"编辑","删除"}, (d,w) -> {
            if (w == 0) startActivity(new Intent(this, CourseEditActivity.class).putExtra("courseId", ce.id));
            else new AlertDialog.Builder(this).setMessage("删除课程「"+ce.name+"」？").setPositiveButton("删除", (dd,ww) -> {
                Executors.newSingleThreadExecutor().execute(() -> { AppDatabase.getDatabase(this).courseEntryDao().delete(ce); runOnUiThread(this::loadCourseEntries); });
            }).setNegativeButton("取消", null).show();
        }).setNegativeButton("关闭", null).show();
    }

    private TextView weightCell(String text, int bgColor, int textSize) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(textSize); tv.setGravity(Gravity.CENTER); tv.setPadding(2, 2, 2, 2);
        if (bgColor != 0) tv.setBackgroundColor(bgColor); else tv.setTextColor(0xFF333333);
        return tv;
    }

    private TextView weightCellPd(String text, int bgColor, int textSize, int padDp) {
        TextView tv = weightCell(text, bgColor, textSize);
        float d = getResources().getDisplayMetrics().density;
        int p = (int)(padDp * d);
        tv.setPadding(p, p, p, p);
        return tv;
    }

    /** Create a rounded rectangle drawable for course cards */
    private android.graphics.drawable.GradientDrawable roundedBg(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        gd.setColor(color);
        return gd;
    }

    /** Create a cell border drawable for empty grid cells */
    private android.graphics.drawable.GradientDrawable cellBorder() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setStroke(1, 0xFFE8E8E8);
        gd.setColor(android.graphics.Color.TRANSPARENT);
        return gd;
    }

    private boolean isCourseActiveWeek(CourseEntry ce) {
        if ("EVERY".equals(ce.weekPattern)) return true; if ("ODD".equals(ce.weekPattern)) return currentWeek % 2 == 1; if ("EVEN".equals(ce.weekPattern)) return currentWeek % 2 == 0;
        if (ce.weekPattern == null || ce.weekPattern.isEmpty()) return true;
        try { java.util.Set<Integer> set = new java.util.HashSet<>();
            for (String part : ce.weekPattern.split(",")) { part = part.trim(); if (part.contains("-")) { String[] r = part.split("-"); for (int i = Integer.parseInt(r[0].trim()); i <= Integer.parseInt(r[1].trim()); i++) set.add(i); } else set.add(Integer.parseInt(part)); }
            return set.contains(currentWeek); } catch (Exception e) { return true; }
    }

    private void onCourseCellClick(int dayOfWeek, int period) {
        if (currentSemester == null) return;
        startActivity(new Intent(this, CourseEditActivity.class).putExtra("semesterId", currentSemester.id).putExtra("dayOfWeek", dayOfWeek).putExtra("startPeriod", period));
    }

    private void addCourseFromCurrent() {
        if (currentSemester == null) { Toast.makeText(this, "请先创建学期", Toast.LENGTH_SHORT).show(); return; }
        startActivity(new Intent(this, CourseEditActivity.class).putExtra("semesterId", currentSemester.id));
    }

    private void showBatchImportDialog() {
        if (currentSemester == null) { Toast.makeText(this, "请先创建学期", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("批量导入课程")
            .setItems(new String[]{"📝 文字描述", "📷 识图导入", "➕ 手动添加"}, (d, w) -> {
                if (w == 0) showTextImportDialog();
                else if (w == 1) startImageImport();
                else addCourseFromCurrent();
            }).setNegativeButton("取消", null).show();
    }

    private void showTextImportDialog() {
        EditText et = new EditText(this); et.setHint("描述你的课程安排，如：\n周一第1-2节高等数学 张老师 教学楼A201\n周一第3-4节大学英语 李老师 外语楼305\n周三第1-2节高等数学...\n\n也可以写：\n每周一上午1-2节高数，3-4节英语..."); et.setTextColor(0xFF333333); et.setMinLines(6); et.setGravity(Gravity.TOP);
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(40, 16, 40, 0); ll.addView(et);
        new AlertDialog.Builder(this).setTitle("文字描述导入").setView(ll)
            .setPositiveButton("开始识别", (d, w) -> {
                String text = et.getText().toString().trim();
                if (text.isEmpty()) { Toast.makeText(this, "请输入课程描述", Toast.LENGTH_SHORT).show(); return; }
                doTextImport(text);
            }).setNegativeButton("取消", null).show();
    }

    private void doTextImport(String text) {
        ProgressDialog pd = new ProgressDialog(this); pd.setMessage("AI 正在分析..."); pd.setCancelable(false); pd.show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                if (!endpoint.endsWith("/")) endpoint += "/";
                String key = SpUtils.getString("OPENAI_API_KEY", "");
                String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
                OpenAiRequest req = new OpenAiRequest(); req.model = model; req.temperature = 0.3f;
                req.messages = new ArrayList<>();

                // System prompt
                req.messages.add(new OpenAiRequest.Message("system",
                    "你是一个专业的课程表解析助手。用户会用自然语言描述课程安排，你提取结构化JSON。\n\n" +
                    "规则:\n" +
                    "- dayOfWeek: 周一=0, 周二=1, 周三=2, 周四=3, 周五=4, 周六=5, 周日=6\n" +
                    "- startPeriod: 第几节开始(1-based), 如「第1-2节」表示 startPeriod=1, periodCount=2\n" +
                    "- 如果用户说「上午第1-2节」或只写「1-2节」, 都表示 startPeriod=1, periodCount=2\n" +
                    "- weekPattern: 没提单双周用 EVERY, 明确写了单周用 ODD, 明确写了双周用 EVEN\n" +
                    "- 如果有多个课程在同一天不同节次, 每个单独列出"));

                // User message with description and example
                req.messages.add(new OpenAiRequest.Message("user",
                    "请根据以下课程描述提取课程信息。返回JSON格式:\n" +
                    "{\"courses\":[{\"name\":\"课程名\",\"teacher\":\"老师\",\"location\":\"教室\",\"dayOfWeek\":0,\"startPeriod\":1,\"periodCount\":2,\"startTime\":\"08:00\",\"endTime\":\"09:50\",\"weekPattern\":\"EVERY\"}]}\n\n" +
                    "示例输入: 周一第1-2节高等数学 张老师 教学楼A201, 第3-4节大学英语\n" +
                    "示例输出: [{\"name\":\"高等数学\",\"teacher\":\"张老师\",\"location\":\"教学楼A201\",\"dayOfWeek\":0,\"startPeriod\":1,\"periodCount\":2,\"weekPattern\":\"EVERY\"},{\"name\":\"大学英语\",\"teacher\":\"\",\"location\":\"\",\"dayOfWeek\":0,\"startPeriod\":3,\"periodCount\":2,\"weekPattern\":\"EVERY\"}]\n\n" +
                    "只返回JSON, 不要其他文字。\n\n课程描述:\n" + text));

                Response<OpenAiResponse> resp = getImportApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + key, req).execute();
                if (!resp.isSuccessful() || resp.body() == null || resp.body().choices == null || resp.body().choices.isEmpty()) {
                    runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "API请求失败: " + resp.code(), Toast.LENGTH_SHORT).show(); });
                    return;
                }
                String json = resp.body().choices.get(0).message.content;
                if (json.contains("```json")) json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) json = json.substring(0, json.lastIndexOf("```")).trim();
                if (!json.startsWith("{")) json = json.substring(json.indexOf("{"));
                if (!json.endsWith("}")) json = json.substring(0, json.lastIndexOf("}") + 1);

                Gson gson = new Gson();
                Map<String, Object> root = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                List<Map<String, Object>> courseList = (List<Map<String, Object>>) root.get("courses");
                if (courseList == null || courseList.isEmpty()) { runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "未识别到课程", Toast.LENGTH_SHORT).show(); }); return; }

                // Collect times for auto-matching
                java.util.Set<String> timeSet = new java.util.LinkedHashSet<>();
                int count = 0;
                for (Map<String, Object> c : courseList) {
                    CourseEntry ce = new CourseEntry(); ce.semesterId = currentSemester.id;
                    ce.name = getStr(c, "name"); ce.teacher = getStr(c, "teacher"); ce.location = getStr(c, "location");
                    ce.dayOfWeek = getInt(c, "dayOfWeek", 0); ce.startPeriod = getInt(c, "startPeriod", 1);
                    ce.periodCount = getInt(c, "periodCount", 1); ce.weekPattern = getStr(c, "weekPattern");
                    if (ce.weekPattern == null || ce.weekPattern.isEmpty()) ce.weekPattern = "EVERY";
                    ce.color = COURSE_COLORS[count % COURSE_COLORS.length];
                    if (ce.name.isEmpty()) continue;
                    String st = getStr(c, "startTime"), et = getStr(c, "endTime");
                    if (!st.isEmpty() && !et.isEmpty()) timeSet.add(st + "-" + et);
                    AppDatabase.getDatabase(this).courseEntryDao().insert(ce); count++;
                }

                // Auto-match periods
                boolean pu = false;
                if (!timeSet.isEmpty()) {
                    java.util.List<String> sorted = new java.util.ArrayList<>(timeSet); java.util.Collections.sort(sorted);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < sorted.size(); i++) { if (i > 0) sb.append(","); sb.append(sorted.get(i)); }
                    if (!sb.toString().equals(currentSemester.customPeriods != null ? currentSemester.customPeriods : "")) {
                        currentSemester.customPeriods = sb.toString();
                        AppDatabase.getDatabase(this).semesterConfigDao().update(currentSemester); pu = true;
                    }
                }

                final int fc = count; final boolean fpu = pu;
                runOnUiThread(() -> { pd.dismiss(); loadCourseEntries();
                    String m = "成功导入 " + fc + " 门课程"; if (fpu) m += "，已调整课时"; Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) { runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }


    private void showSemesterEditDialog(SemesterConfig sc) {
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(40, 16, 40, 0);

        // --- 学期名称 ---
        TextView lbName = new TextView(this); lbName.setText("学期名称"); lbName.setTextSize(13); lbName.setTextColor(0xFF666666); ll.addView(lbName);
        EditText etName = new EditText(this); etName.setTextColor(Color.BLACK); etName.setHint("如：2026春季学期"); if (sc != null) etName.setText(sc.name); ll.addView(etName);

        // --- 开始日期 (日历选择) ---
        TextView lbDate = new TextView(this); lbDate.setText("开始日期"); lbDate.setTextSize(13); lbDate.setTextColor(0xFF666666); lbDate.setPadding(0, 12, 0, 0); ll.addView(lbDate);
        TextView tvDate = new TextView(this); tvDate.setTextSize(15); tvDate.setTextColor(0xFF333333); tvDate.setPadding(12, 14, 12, 14);
        tvDate.setBackgroundColor(0xFFF5F5F5);
        String initDate = (sc != null && sc.startDate != null) ? sc.startDate : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        tvDate.setText(initDate); tvDate.setClickable(true);
        tvDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            try { java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(tvDate.getText().toString()); cal.setTime(d); } catch (Exception ignored) {}
            new android.app.DatePickerDialog(this, (view, y, m, d) -> tvDate.setText(String.format("%04d-%02d-%02d", y, m+1, d)), cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        ll.addView(tvDate);

        // --- 总周数 ---
        TextView lbWeeks = new TextView(this); lbWeeks.setText("总周数"); lbWeeks.setTextSize(13); lbWeeks.setTextColor(0xFF666666); lbWeeks.setPadding(0, 12, 0, 0); ll.addView(lbWeeks);
        EditText etWeeks = new EditText(this); etWeeks.setTextColor(Color.BLACK); etWeeks.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etWeeks.setText(sc != null ? String.valueOf(sc.totalWeeks) : "18"); ll.addView(etWeeks);

        // --- 每节课时长 ---
        TextView lbDur = new TextView(this); lbDur.setText("每节课时长（分钟）"); lbDur.setTextSize(13); lbDur.setTextColor(0xFF666666); lbDur.setPadding(0, 12, 0, 0); ll.addView(lbDur);
        EditText etPdDur = new EditText(this); etPdDur.setTextColor(Color.BLACK); etPdDur.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdDur.setText(sc != null ? String.valueOf(sc.periodDuration) : "45"); ll.addView(etPdDur);

        // --- 课间休息 ---
        TextView lbBrk = new TextView(this); lbBrk.setText("课间休息（分钟）"); lbBrk.setTextSize(13); lbBrk.setTextColor(0xFF666666); lbBrk.setPadding(0, 12, 0, 0); ll.addView(lbBrk);
        EditText etPdBrk = new EditText(this); etPdBrk.setTextColor(Color.BLACK); etPdBrk.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPdBrk.setText(sc != null ? String.valueOf(sc.periodBreak) : "10"); ll.addView(etPdBrk);

        // --- 第一节时间 ---
        TextView lbFirst = new TextView(this); lbFirst.setText("第一节开始时间"); lbFirst.setTextSize(13); lbFirst.setTextColor(0xFF666666); lbFirst.setPadding(0, 12, 0, 0); ll.addView(lbFirst);
        EditText etFirst = new EditText(this); etFirst.setTextColor(Color.BLACK); etFirst.setHint("如：08:00"); etFirst.setText(sc != null ? sc.firstPeriodStart : "08:00"); ll.addView(etFirst);

        // --- 每天节数 ---
        TextView lbPerDay = new TextView(this); lbPerDay.setText("每天节数"); lbPerDay.setTextSize(13); lbPerDay.setTextColor(0xFF666666); lbPerDay.setPadding(0, 12, 0, 0); ll.addView(lbPerDay);
        EditText etPerDay = new EditText(this); etPerDay.setTextColor(Color.BLACK); etPerDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etPerDay.setText(sc != null ? String.valueOf(sc.periodsPerDay) : "12"); ll.addView(etPerDay);

        // --- 高级设置按钮 (needs dialog ref later, so declare first) ---
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
                if (sc == null) { long id = db.semesterConfigDao().insert(cfg); db.semesterConfigDao().deactivateAll(); db.semesterConfigDao().setActive((int)id); }
                else { db.semesterConfigDao().update(cfg); }
                runOnUiThread(() -> { loadSemesters(); dialog.dismiss(); });
            });
        });
    }

    private String formatSelectedDate(CalendarDayItem item) {
        Calendar cal = Calendar.getInstance();
        cal.set(item.year, item.month, item.dayOfMonth);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        String[] wd = {"周日","周一","周二","周三","周四","周五","周六"};
        return item.year + "年" + (item.month + 1) + "月" + item.dayOfMonth + "日 " + wd[dow - 1];
    }

    private void refreshDayMarkers() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String startDate = String.format("%04d-%02d-01", currentYear, currentMonth + 1);
            Calendar endCal = Calendar.getInstance();
            endCal.set(currentYear, currentMonth, 1);
            int dim = endCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            String endDate = String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, dim);

            List<CalendarEvent> events = eventDao.getEventsInRangeSync(startDate, endDate);
            List<CycleRecord> allCycles = cycleDao.getAllRecordsSync();
            List<HolidayCache> holidays = holidayDao.getHolidaysInRange(startDate, endDate);
            CyclePredictor.CyclePrediction prediction = CyclePredictor.predict(allCycles);
            List<CalendarEvent> expanded = expandRecurringEvents(events, startDate, endDate);

            mainHandler.post(() -> {
                MonthCalendarView mv = getCurrentMonthView();
                if (mv != null) applyMarkers(expanded, allCycles, prediction, holidays, mv);
            });
        });
    }

    private List<CalendarEvent> expandRecurringEvents(List<CalendarEvent> events, String sd, String ed) {
        List<CalendarEvent> r = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            long rs = sdf.parse(sd).getTime(), re = sdf.parse(ed).getTime();
            for (CalendarEvent e : events) {
                r.add(e);
                if ("NONE".equals(e.recurrence) || e.recurrence == null) continue;
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(sdf.parse(e.eventDate).getTime());
                int sf;
                switch (e.recurrence) {
                    case "DAILY": sf = Calendar.DAY_OF_MONTH; break;
                    case "WEEKLY": sf = Calendar.WEEK_OF_YEAR; break;
                    case "MONTHLY": sf = Calendar.MONTH; break;
                    case "YEARLY": sf = Calendar.YEAR; break;
                    default: continue;
                }
                int max = 50;
                while (max-- > 0) {
                    cal.add(sf, 1);
                    long t = cal.getTimeInMillis();
                    if (t > re) break;
                    if (t >= rs) {
                        CalendarEvent ve = new CalendarEvent();
                        ve.id = e.id; ve.title = e.title; ve.notes = e.notes;
                        ve.eventDate = sdf.format(cal.getTime());
                        ve.allDay = e.allDay; ve.recurrence = e.recurrence;
                        r.add(ve);
                    }
                }
            }
        } catch (Exception ignored) {}
        return r;
    }

    private void applyMarkers(List<CalendarEvent> events, List<CycleRecord> cycles,
                               CyclePredictor.CyclePrediction prediction, List<HolidayCache> holidays,
                               MonthCalendarView mv) {
        List<CalendarDayItem> items = mv.getDayItems();
        java.util.Map<String, Integer> emap = new java.util.HashMap<>();
        for (CalendarEvent e : events) if (e.eventDate != null) emap.merge(e.eventDate, 1, Integer::sum);

        java.util.Set<String> pSet = new java.util.HashSet<>();
        java.util.Set<String> ppSet = new java.util.HashSet<>();
        java.util.Set<String> ovSet = new java.util.HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (CycleRecord cr : cycles) {
            try {
                java.util.Date s = sdf.parse(cr.startDate), e = sdf.parse(cr.endDate);
                // 对于未结束的经期（endDate == startDate），扩展到今天以便连续显示
                if (cr.endDate != null && cr.endDate.equals(cr.startDate)) {
                    String todayStr = sdf.format(new java.util.Date());
                    java.util.Date today = sdf.parse(todayStr);
                    if (today.after(e)) e = today;
                }
                Calendar cal = Calendar.getInstance(); cal.setTime(s);
                while (!cal.getTime().after(e)) { pSet.add(sdf.format(cal.getTime())); cal.add(Calendar.DAY_OF_MONTH, 1); }
            } catch (Exception ignored) {}
        }
        if (prediction != null) {
            try {
                Calendar cal = Calendar.getInstance();
                // 预测经期显示：从最早可能开始到最早可能结束（最可能区间），而非整个不确定性窗口
                java.util.Date es = sdf.parse(prediction.earliestStart), ee = sdf.parse(prediction.earliestEnd);
                cal.setTime(es);
                while (!cal.getTime().after(ee)) { String ds = sdf.format(cal.getTime()); if (!pSet.contains(ds)) ppSet.add(ds); cal.add(Calendar.DAY_OF_MONTH, 1); }
                java.util.Date os = sdf.parse(prediction.ovulationStart), oe = sdf.parse(prediction.ovulationEnd);
                cal.setTime(os);
                while (!cal.getTime().after(oe)) { ovSet.add(sdf.format(cal.getTime())); cal.add(Calendar.DAY_OF_MONTH, 1); }
            } catch (Exception ignored) {}
        }
        java.util.Map<String, HolidayCache> hmap = new java.util.HashMap<>();
        for (HolidayCache h : holidays) hmap.put(h.date, h);

        for (CalendarDayItem item : items) {
            String ds = item.getDateString();
            item.hasEvent = item.hasPeriod = item.isPredictedPeriod = item.isOvulation = item.isHoliday = item.isWorkday = false;
            item.holidayName = null; item.eventCount = 0;
            Integer c = emap.get(ds);
            if (c != null && c > 0) { item.eventCount = c; if (currentMode == MODE_ALL || currentMode == MODE_EVENTS) item.hasEvent = true; }
            if (currentMode == MODE_ALL || currentMode == MODE_CYCLE) {
                if (pSet.contains(ds)) item.hasPeriod = true;
                if (ppSet.contains(ds)) item.isPredictedPeriod = true;
                if (ovSet.contains(ds)) item.isOvulation = true;
            }
            HolidayCache hc = hmap.get(ds);
            if (hc != null) { item.isHoliday = hc.isOffDay; item.isWorkday = !hc.isOffDay; item.holidayName = hc.name; }
        }
        mv.notifyGridChanged();
    }

    private void loadDayDetail() {
        if (selectedDateStr == null) { detailAdapter.clear(); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CalendarEvent> events = eventDao.getEventsByDate(selectedDateStr);
            CycleRecord cr = cycleDao.getRecordByStartDate(selectedDateStr);
            if (cr == null) cr = cycleDao.getPeriodOnDate(selectedDateStr);
            events = expandRecurringForDate(events, selectedDateStr);
            HolidayCache hc = holidayDao.getHolidayByDate(selectedDateStr);
            List<CalendarEvent> fe = new ArrayList<>();
            if (currentMode == MODE_ALL || currentMode == MODE_EVENTS) fe.addAll(events);
            CycleRecord fc = (currentMode == MODE_ALL || currentMode == MODE_CYCLE) ? cr : null;
            mainHandler.post(() -> detailAdapter.setData(fe, fc, hc));
        });
    }

    private List<CalendarEvent> expandRecurringForDate(List<CalendarEvent> events, String td) {
        List<CalendarEvent> r = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            long tt = sdf.parse(td).getTime();
            for (CalendarEvent e : events) {
                if (td.equals(e.eventDate)) { r.add(e); continue; }
                if ("NONE".equals(e.recurrence) || e.recurrence == null) continue;
                long et = sdf.parse(e.eventDate).getTime();
                if (et >= tt) continue;
                int sf;
                switch (e.recurrence) {
                    case "DAILY": sf = Calendar.DAY_OF_MONTH; break;
                    case "WEEKLY": sf = Calendar.WEEK_OF_YEAR; break;
                    case "MONTHLY": sf = Calendar.MONTH; break;
                    case "YEARLY": sf = Calendar.YEAR; break;
                    default: continue;
                }
                Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(et);
                int max = 200; boolean found = false;
                while (max-- > 0 && !found) { cal.add(sf, 1); if (cal.getTimeInMillis() > tt) break; if (td.equals(sdf.format(cal.getTime()))) {
                    CalendarEvent ve = new CalendarEvent(); ve.id = e.id; ve.title = e.title; ve.notes = e.notes;
                    ve.eventDate = td; ve.allDay = e.allDay; ve.recurrence = e.recurrence; r.add(ve); found = true;
                }}
            }
        } catch (Exception ignored) {}
        return r;
    }

    // --- Dialogs & quick actions ---

    private void showAddDialog() {
        if (selectedDateStr == null) { Toast.makeText(this, "请先选择一个日期", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("新建")
                .setItems(new String[]{"添加日程", "记录经期"}, (d, w) -> {
                    if (w == 0) { startActivity(new Intent(this, EventEditActivity.class).putExtra("date", selectedDateStr)); }
                    else { startActivity(new Intent(this, CycleEditActivity.class).putExtra("date", selectedDateStr)); }
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void showDayLongPressMenu(CalendarDayItem item) {
        String ds = item.getDateString();
        boolean future = isFutureDate(ds);
        String[] opts = future ? new String[]{"📝 添加日程"} : new String[]{"🩸 标记为经期开始", "🩹 标记为经期结束", "📝 添加日程"};
        new AlertDialog.Builder(this).setTitle(ds).setItems(opts, (d, w) -> {
            if (future) { startActivity(new Intent(this, EventEditActivity.class).putExtra("date", ds)); }
            else if (w == 0) markPeriodStart(ds);
            else if (w == 1) markPeriodEnd(ds);
            else { startActivity(new Intent(this, EventEditActivity.class).putExtra("date", ds)); }
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void showQuickMarkDialog(String ds) {
        if (ds == null) { Toast.makeText(this, "请先选择一个日期", Toast.LENGTH_SHORT).show(); return; }
        if (isFutureDate(ds)) { Toast.makeText(this, "不能在未来日期记录经期", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("快速标记")
                .setItems(new String[]{"🩸 经期开始了", "🩹 经期结束了"}, (d, w) -> {
                    if (w == 0) markPeriodStart(ds); else markPeriodEnd(ds);
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private boolean isFutureDate(String ds) { return ds.compareTo(dateFormat.format(new java.util.Date())) > 0; }

    private void markPeriodStart(String ds) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CycleRecord r = new CycleRecord(); r.startDate = ds; r.endDate = ds;
            cycleDao.insert(r);
            runOnUiThread(() -> { Toast.makeText(this, "已标记经期开始: " + ds, Toast.LENGTH_SHORT).show(); refreshDayMarkers(); loadDayDetail(); });
        });
    }

    /** 用 fakeDragBy 模拟慢速滑动导航（不依赖反射，安全） */
    private void smoothSetCurrentItem(int targetPos, int durationMs) {
        int currentPos = monthViewPager.getCurrentItem();
        if (currentPos == targetPos) return;
        int pageWidth = monthViewPager.getWidth();
        if (pageWidth <= 0) { monthViewPager.setCurrentItem(targetPos, true); return; }
        float dragDistance = (currentPos - targetPos) * (float) pageWidth;

        monthViewPager.beginFakeDrag();
        ValueAnimator anim = ValueAnimator.ofFloat(0f, dragDistance);
        anim.setDuration(durationMs);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        float[] prev = {0f};
        anim.addUpdateListener(a -> {
            float cur = (float) a.getAnimatedValue();
            float delta = cur - prev[0];
            prev[0] = cur;
            if (Math.abs(delta) > 0.5f) monthViewPager.fakeDragBy(delta);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { monthViewPager.endFakeDrag(); }
        });
        anim.start();
    }

    // === 识图导入（多图+时间识别+自动匹配） ===

    private final List<String> importImageBase64List = new ArrayList<>();
    private int importImageCount = 0;

    private void setupImageImportLaunchers() {
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    onImagePicked(result.getData().getData());
                }
            }
        );
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && pendingCameraFile != null && pendingCameraFile.exists()) {
                    onImagePicked(Uri.fromFile(pendingCameraFile));
                }
            }
        );
    }

    private void startImageImport() {
        if (currentSemester == null) { Toast.makeText(this, "请先选择学期", Toast.LENGTH_SHORT).show(); return; }
        importImageBase64List.clear(); importImageCount = 0;
        pickOneImage();
    }

    private void pickOneImage() {
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add("从相册选择"); opts.add("拍照");
        if (importImageCount > 0) opts.add("开始识别（共" + importImageCount + "张）");
        new AlertDialog.Builder(this)
            .setTitle(importImageCount == 0 ? "识图导入课程" : "已选 " + importImageCount + " 张")
            .setItems(opts.toArray(new String[0]), (d, w) -> {
                if (w == 0) {
                    pickImageLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                } else if (w == 1) {
                    pendingCameraFile = new File(getCacheDir(), "course_import_" + System.currentTimeMillis() + ".jpg");
                    Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingCameraFile);
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    cameraLauncher.launch(intent);
                } else {
                    doImport();
                }
            }).setNegativeButton("取消", (d, w) -> importImageBase64List.clear()).show();
    }

    private void onImagePicked(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                if (bmp == null) return;
                int max = 1024, w = bmp.getWidth(), h = bmp.getHeight();
                if (w > max || h > max) { float ratio = Math.min((float)max/w, (float)max/h); bmp = Bitmap.createScaledBitmap(bmp, (int)(w*ratio), (int)(h*ratio), true); }
                ByteArrayOutputStream baos = new ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                importImageBase64List.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));
                importImageCount++;
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this).setTitle("已添加第 " + importImageCount + " 张图片")
                        .setMessage("继续添加还是开始识别？")
                        .setPositiveButton("继续添加", (dd, ww) -> pickOneImage())
                        .setNegativeButton("开始识别", (dd, ww) -> doImport()).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void doImport() {
        if (importImageBase64List.isEmpty()) return;
        final int total = importImageBase64List.size();
        ProgressDialog pd = new ProgressDialog(this); pd.setMessage("正在识别 " + total + " 张图片..."); pd.setCancelable(false); pd.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                if (!endpoint.endsWith("/")) endpoint += "/";
                String key = SpUtils.getString("OPENAI_API_KEY", "");
                String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

                OpenAiRequest req = new OpenAiRequest(); req.model = model; req.temperature = 0.3f;
                req.messages = new ArrayList<>();

                // System prompt with domain knowledge
                String systemPrompt = "你是一个专业的中国大学课程表识别助手。你会收到课程表图片，需要准确提取所有课程信息。\n\n" +
                    "【课程表结构】\n" +
                    "- 横轴是星期(周一~周日)，纵轴是节次(第1节、第2节...)\n" +
                    "- 一个格子可能跨多节(如「第1-2节」表示 periodCount=2, startPeriod=1)\n" +
                    "- 部分课程表左侧或右侧有「节次时间列」，列出每节课的起止时间(如 08:00-08:45)\n\n" +
                    "【dayOfWeek 映射】严格按图片列顺序:\n" +
                    "  最左数据列 = 周一 = 0, 第二列 = 周二 = 1, ... 最右 = 周日 = 6\n" +
                    "  不要把周一识别成 dayOfWeek=1! 周一永远是 0\n\n" +
                    "【startPeriod 映射】严格按图片行顺序:\n" +
                    "  第1节 = startPeriod=1, 第2节 = 2, ... 第12节 = 12\n" +
                    "  不要从0开始! 第一行数据就是第1节\n\n" +
                    "【weekPattern 判断规则】\n" +
                    "- 格子里标注了「单」或在单周列 -> \"ODD\"\n" +
                    "- 格子里标注了「双」或在双周列 -> \"EVEN\"\n" +
                    "- 没有特殊标记 -> \"EVERY\"\n" +
                    "- 特定周次如「第1,3,5,7周」-> \"1,3,5,7\"\n" +
                    "- 周次范围如「第2-8周」-> \"2-8\"\n\n" +
                    "【其他要点】\n" +
                    "- 教室和老师通常在课程名称下方或旁边用小字标注, 仔细辨认\n" +
                    "- 如果图中某节课表格为空(空白格子), 不要编造课程\n" +
                    "- 不要遗漏图片边缘的课程\n" +
                    "- 每门课的 startTime/endTime 取该课第一节起始和最后一节结束的时钟时间\n" +
                    "- 如果图中有时节次时间列, 务必提取到 periodTimes 数组";
                req.messages.add(new OpenAiRequest.Message("system", systemPrompt));

                // User message with images
                List<OpenAiRequest.ContentPart> parts = new ArrayList<>();
                String prompt = "请识别以下" + total + "张课程表图片，提取所有课程信息。\n\n" +
                    "返回JSON格式：\n" +
                    "{\n" +
                    "  \"periodTimes\": [\"08:00-08:45\", \"08:50-09:35\", ...],  // 每节独立时间，从图中节次时间列读取\n" +
                    "  \"courses\": [{\n" +
                    "    \"name\": \"高等数学\",\n" +
                    "    \"teacher\": \"张老师\",\n" +
                    "    \"location\": \"教学楼A201\",\n" +
                    "    \"dayOfWeek\": 0,\n" +
                    "    \"startPeriod\": 1,\n" +
                    "    \"periodCount\": 2,\n" +
                    "    \"startTime\": \"08:00\",\n" +
                    "    \"endTime\": \"09:50\",\n" +
                    "    \"weekPattern\": \"EVERY\"\n" +
                    "  }]\n" +
                    "}\n\n" +
                    "示例：图中「周一」列、「第1-2节」行有「高等数学 张老师 A201」，输出：\n" +
                    "{\"name\":\"高等数学\",\"teacher\":\"张老师\",\"location\":\"A201\",\"dayOfWeek\":0,\"startPeriod\":1,\"periodCount\":2,\"startTime\":\"08:00\",\"endTime\":\"09:50\",\"weekPattern\":\"EVERY\"}\n\n" +
                    "只返回JSON，不要其他文字。";
                parts.add(OpenAiRequest.ContentPart.text(prompt));
                for (String b64 : importImageBase64List) parts.add(OpenAiRequest.ContentPart.imageUrl(b64));
                req.messages.add(new OpenAiRequest.Message("user", parts));

                Response<OpenAiResponse> resp = getImportApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + key, req).execute();
                if (!resp.isSuccessful() || resp.body() == null || resp.body().choices == null || resp.body().choices.isEmpty()) {
                    runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "API请求失败: " + resp.code(), Toast.LENGTH_SHORT).show(); });
                    return;
                }

                String json = resp.body().choices.get(0).message.content;
                if (json.contains("```json")) json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) json = json.substring(0, json.lastIndexOf("```")).trim();
                if (!json.startsWith("{")) json = json.substring(json.indexOf("{"));
                if (!json.endsWith("}")) json = json.substring(0, json.lastIndexOf("}") + 1);

                Gson gson = new Gson();
                Map<String, Object> root = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                List<Map<String, Object>> courseList = (List<Map<String, Object>>) root.get("courses");
                if (courseList == null || courseList.isEmpty()) {
                    runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "未识别到课程", Toast.LENGTH_SHORT).show(); });
                    return;
                }

                // Extract individual period times if AI provided them
                List<String> aiPeriodTimes = null;
                try {
                    List<Object> pt = (List<Object>) root.get("periodTimes");
                    if (pt != null && !pt.isEmpty()) {
                        aiPeriodTimes = new ArrayList<>();
                        for (Object o : pt) { String s = o == null ? "" : o.toString().trim(); if (!s.isEmpty()) aiPeriodTimes.add(s); }
                        if (aiPeriodTimes.isEmpty()) aiPeriodTimes = null;
                    }
                } catch (Exception ignored) {}

                // Collect unique time slots from AI response (fallback)
                java.util.Set<String> timeSet = new java.util.LinkedHashSet<>();
                int count = 0;
                for (Map<String, Object> c : courseList) {
                    CourseEntry ce = new CourseEntry();
                    ce.semesterId = currentSemester.id;
                    ce.name = getStr(c, "name");
                    ce.teacher = getStr(c, "teacher");
                    ce.location = getStr(c, "location");
                    ce.dayOfWeek = getInt(c, "dayOfWeek", 0);
                    ce.startPeriod = getInt(c, "startPeriod", 1);
                    ce.periodCount = getInt(c, "periodCount", 1);
                    ce.weekPattern = getStr(c, "weekPattern");
                    if (ce.weekPattern == null || ce.weekPattern.isEmpty()) ce.weekPattern = "EVERY";
                    ce.color = COURSE_COLORS[count % COURSE_COLORS.length];
                    if (ce.name.isEmpty()) continue;

                    String st = getStr(c, "startTime"), et = getStr(c, "endTime");
                    if (!st.isEmpty() && !et.isEmpty()) timeSet.add(st + "-" + et);

                    AppDatabase.getDatabase(this).courseEntryDao().insert(ce);
                    count++;
                }

                // Auto-match period times — prefer AI-extracted individual periods over combined course spans
                boolean periodsUpdated = false;
                List<String> periodSource = (aiPeriodTimes != null) ? aiPeriodTimes : new ArrayList<>(timeSet);
                if (!periodSource.isEmpty()) {
                    java.util.Collections.sort(periodSource);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < periodSource.size(); i++) { if (i > 0) sb.append(","); sb.append(periodSource.get(i)); }
                    String newPeriods = sb.toString();
                    String oldPeriods = currentSemester.customPeriods;
                    if (oldPeriods == null || oldPeriods.isEmpty()) {
                        // Generate default periods for comparison
                        StringBuilder defSb = new StringBuilder();
                        try { java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                            java.util.Date base = tf.parse(currentSemester.firstPeriodStart); java.util.Calendar cal = java.util.Calendar.getInstance(); cal.setTime(base);
                            for (int i = 0; i < currentSemester.periodsPerDay; i++) { if (i > 0) defSb.append(","); defSb.append(tf.format(cal.getTime())); cal.add(java.util.Calendar.MINUTE, currentSemester.periodDuration); defSb.append("-").append(tf.format(cal.getTime())); cal.add(java.util.Calendar.MINUTE, currentSemester.periodBreak); }
                        } catch (Exception ignored) {}
                        oldPeriods = defSb.toString();
                    }
                    if (!newPeriods.equals(oldPeriods)) {
                        currentSemester.customPeriods = newPeriods;
                        AppDatabase.getDatabase(this).semesterConfigDao().update(currentSemester);
                        periodsUpdated = true;
                    }
                }

                final int finalCount = count;
                final boolean pu = periodsUpdated;
                runOnUiThread(() -> {
                    pd.dismiss(); loadCourseEntries(); importImageBase64List.clear();
                    String msg2 = "成功导入 " + finalCount + " 门课程";
                    if (pu) msg2 += "，已自动调整课时设置";
                    Toast.makeText(this, msg2, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key); return v != null ? v.toString() : "";
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        try { Object v = m.get(key); return v != null ? ((Number)v).intValue() : def; } catch (Exception e) { return def; }
    }

    private OpenAiApi getImportApi() {
        String ep = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        if (!ep.endsWith("/")) ep += "/";
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build();
        return new Retrofit.Builder().baseUrl(ep).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(OpenAiApi.class);
    }

    private void markPeriodEnd(String ds) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CycleRecord open = cycleDao.getOpenEndedRecord();
            if (open == null) { runOnUiThread(() -> Toast.makeText(this, "没有找到未结束的经期记录", Toast.LENGTH_SHORT).show()); return; }
            if (ds.compareTo(open.startDate) < 0) { runOnUiThread(() -> Toast.makeText(this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()); return; }
            open.endDate = ds; cycleDao.update(open);
            runOnUiThread(() -> { Toast.makeText(this, "已标记经期结束: " + open.startDate + " ~ " + ds, Toast.LENGTH_SHORT).show(); refreshDayMarkers(); loadDayDetail(); });
        });
    }
}
