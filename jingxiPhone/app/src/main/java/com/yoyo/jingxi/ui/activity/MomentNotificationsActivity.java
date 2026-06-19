package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.ui.adapter.MomentNotificationAdapter;
import com.yoyo.jingxi.utils.SpUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MomentNotificationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MomentNotificationAdapter adapter;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moment_notifications);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("消息");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MomentNotificationAdapter();
        recyclerView.setAdapter(adapter);

        db = AppDatabase.getDatabase(this);

        String currentPersonaId = SpUtils.getString("current_persona_id", "");
        if (currentPersonaId.isEmpty()) {
            currentPersonaId = SpUtils.getString("MY_NAME", "我");
        }
        if (!currentPersonaId.isEmpty()) {
            db.momentNotificationDao().getNotificationsForReceiver(currentPersonaId, 0).observe(this, notifications -> {
                adapter.setNotifications(notifications);
            });
            
            // Mark all as read
            final String fCurrentPersonaId = currentPersonaId;
            executorService.execute(() -> {
                db.momentNotificationDao().markAllAsRead(fCurrentPersonaId, 0);
            });
        }

        adapter.setOnItemClickListener(notification -> {
            android.content.Intent intent = new android.content.Intent(MomentNotificationsActivity.this, MomentDetailActivity.class);
            intent.putExtra("moment_id", notification.momentId);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
