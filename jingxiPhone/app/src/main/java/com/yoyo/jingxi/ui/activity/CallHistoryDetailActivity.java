package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.ui.adapter.CallMessageAdapter;

import java.util.concurrent.Executors;

public class CallHistoryDetailActivity extends AppCompatActivity {

    private int callRecordId;
    private AppDatabase db;
    private CallMessageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_history_detail);

        callRecordId = getIntent().getIntExtra("call_record_id", -1);
        if (callRecordId == -1) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("通话详情");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rvCallMessages = findViewById(R.id.rvCallMessages);
        rvCallMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CallMessageAdapter();
        rvCallMessages.setAdapter(adapter);

        db = AppDatabase.getDatabase(this);

        Executors.newSingleThreadExecutor().execute(() -> {
            CallRecord record = db.callRecordDao().getRecordByIdSync(callRecordId);
            if (record != null) {
                com.yoyo.jingxi.data.entity.Character character = db.characterDao().getCharacterById(record.characterId);
                if (character != null) {
                    runOnUiThread(() -> {
                        adapter.setCharacterName(character.name);
                        long mins = record.duration / 60;
                        long secs = record.duration % 60;
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setSubtitle(String.format("通话时长: %02d:%02d", mins, secs));
                        }
                    });
                }
            }
        });

        db.callMessageDao().getCallMessagesByCallId(callRecordId).observe(this, messages -> {
            if (messages != null) {
                adapter.setMessages(messages);
                if (!messages.isEmpty()) {
                    rvCallMessages.scrollToPosition(messages.size() - 1);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.releaseMediaPlayer();
        }
    }
}
