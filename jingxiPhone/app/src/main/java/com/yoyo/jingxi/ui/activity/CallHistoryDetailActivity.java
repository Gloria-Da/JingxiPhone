package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.ui.adapter.CallMessageAdapter;
import com.yoyo.jingxi.utils.VoiceGenerateHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallHistoryDetailActivity extends AppCompatActivity {

    private int callRecordId;
    private AppDatabase db;
    private CallMessageAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

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

        db = AppDatabase.getDatabase(this);

        adapter = new CallMessageAdapter();
        VoiceGenerateHelper vgh = new VoiceGenerateHelper(this, db, dbExecutor, mainHandler);
        adapter.setVoiceGenerateHelper(vgh);
        adapter.setDb(db);
        adapter.setDbExecutor(dbExecutor);
        rvCallMessages.setAdapter(adapter);

        dbExecutor.execute(() -> {
            CallRecord record = db.callRecordDao().getRecordByIdSync(callRecordId);
            if (record != null) {
                com.yoyo.jingxi.data.entity.Character character = db.characterDao().getCharacterById(record.characterId);
                if (character != null) {
                    runOnUiThread(() -> {
                        adapter.setCharacterName(character.name);
                        adapter.setCharacterId(character.id);
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
