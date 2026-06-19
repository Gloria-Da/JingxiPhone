package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.os.Handler;
import android.os.Looper;
import android.app.ProgressDialog;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.DailySchedule;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.ui.adapter.SchedulePagerAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.yoyo.jingxi.ui.widget.BookFlipPageTransformer;
import com.yoyo.jingxi.data.entity.ScheduleEntry;
import java.util.List;
import com.yoyo.jingxi.utils.SpUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScheduleActivity extends AppCompatActivity {

    private int characterId;
    private Character currentCharacter;
    private AppDatabase db;
    private OpenAIManager aiManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();

    private ViewPager2 viewPager;
    private SchedulePagerAdapter pagerAdapter;
    private ProgressDialog progressDialog;
    private boolean isGenerating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        characterId = getIntent().getIntExtra("character_id", -1);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("日程表");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewPager = findViewById(R.id.viewPager);
        pagerAdapter = new SchedulePagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setPageTransformer(new BookFlipPageTransformer());

        db = AppDatabase.getDatabase(this);
        aiManager = new OpenAIManager();

        Executors.newSingleThreadExecutor().execute(() -> {
            currentCharacter = db.characterDao().getCharacterById(characterId);
            if (currentCharacter != null) {
                // 检查并生成日程
                if (!com.yoyo.jingxi.utils.ScheduleManager.isScheduleGeneratedToday(currentCharacter.id) && com.yoyo.jingxi.utils.SpUtils.getBoolean("SCHEDULE_ENABLED_" + currentCharacter.id, false)) {
                    mainHandler.post(() -> {
                        Toast.makeText(ScheduleActivity.this, "正在为您规划今日日程，请稍候...", Toast.LENGTH_SHORT).show();
                    });
                    com.yoyo.jingxi.utils.ScheduleManager.checkAndAutoGenerate(currentCharacter, db, new com.yoyo.jingxi.utils.ScheduleManager.ScheduleGenerateCallback() {
                        @Override
                        public void onSuccess(com.yoyo.jingxi.data.entity.DailySchedule schedule) {
                            mainHandler.post(() -> {
                                Toast.makeText(ScheduleActivity.this, "今日日程规划完成！", Toast.LENGTH_SHORT).show();
                                loadCurrentSchedule();
                            });
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            mainHandler.post(() -> {
                                Toast.makeText(ScheduleActivity.this, "日程规划失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                                loadCurrentSchedule();
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> loadCurrentSchedule());
                }
            } else {
                mainHandler.post(() -> loadCurrentSchedule());
            }
        });
        
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在生成手帐日程，请稍候...");
        progressDialog.setCancelable(false);
    }

    private void loadCurrentSchedule() {
        db.scheduleDao().getAllSchedules(characterId).observe(this, entries -> {
            pagerAdapter.setEntries(entries);
            if (entries != null && !entries.isEmpty()) {
                // Ordered by ASC, so the latest date is the last item
                viewPager.setCurrentItem(entries.size() - 1, false);
            }
            invalidateOptionsMenu();
        });
    }

    private void generateScheduleNow() {
        if (currentCharacter == null) {
            Toast.makeText(this, "正在加载角色信息...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isGenerating = true;
        invalidateOptionsMenu();
        progressDialog.show();

        com.yoyo.jingxi.utils.ScheduleManager.generateSchedule(db, currentCharacter, new com.yoyo.jingxi.utils.ScheduleManager.ScheduleGenerateCallback() {
            @Override
            public void onSuccess(DailySchedule schedule) {
                isGenerating = false;
                progressDialog.dismiss();
                Toast.makeText(ScheduleActivity.this, "手帐日程已生成", Toast.LENGTH_SHORT).show();
                // The new schedule will be automatically loaded via LiveData observer
            }

            @Override
            public void onFailure(String errorMsg) {
                isGenerating = false;
                progressDialog.dismiss();
                invalidateOptionsMenu();
                Toast.makeText(ScheduleActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_schedule, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem itemGenerate = menu.findItem(R.id.action_generate);
        if (itemGenerate != null) {
            itemGenerate.setEnabled(!isGenerating);
            if (pagerAdapter != null && pagerAdapter.getItemCount() > 0) {
                itemGenerate.setTitle("重新生成");
            } else {
                itemGenerate.setTitle("生成");
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_generate) {
            if (pagerAdapter != null && pagerAdapter.getItemCount() > 0) {
                new AlertDialog.Builder(this)
                    .setTitle("重新生成日程")
                    .setMessage("确定要重新生成今日日程吗？这会覆盖当前的日程。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        generateScheduleNow();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            } else {
                generateScheduleNow();
            }
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_schedule_settings, null);
        
        android.widget.CheckBox cbEnableSchedule = view.findViewById(R.id.cbEnableSchedule);
        
        boolean isEnabled = SpUtils.getBoolean("SCHEDULE_ENABLED_" + characterId, false);
        cbEnableSchedule.setChecked(isEnabled);
        
        builder.setView(view)
               .setTitle("日程设置")
               .setPositiveButton("保存", (dialog, which) -> {
                   SpUtils.putBoolean("SCHEDULE_ENABLED_" + characterId, cbEnableSchedule.isChecked());
                   Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
               })
               .setNegativeButton("取消", null)
               .show();
    }
}
