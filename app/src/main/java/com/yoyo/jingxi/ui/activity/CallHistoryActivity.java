package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.ui.adapter.CallHistoryAdapter;

import java.util.concurrent.Executors;

public class CallHistoryActivity extends AppCompatActivity {

    private int sessionId;
    private AppDatabase db;
    private CallHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_history);

        sessionId = getIntent().getIntExtra("session_id", -1);
        if (sessionId == -1) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("历史通话");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rvCallHistory = findViewById(R.id.rvCallHistory);
        rvCallHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CallHistoryAdapter();
        rvCallHistory.setAdapter(adapter);

        db = AppDatabase.getDatabase(this);

        db.callRecordDao().getRecordsBySessionId(sessionId).observe(this, records -> {
            adapter.setRecords(records);
        });
    }
}
