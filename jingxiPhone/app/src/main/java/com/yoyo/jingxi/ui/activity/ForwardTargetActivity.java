package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.ForwardRecord;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.SharedContent;
import com.yoyo.jingxi.ui.adapter.ForwardTargetAdapter;
import com.yoyo.jingxi.utils.ThemeManager;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 选择转发目标角色/会话（支持多选）。
 * 转发模式（逐条/合并）由 ChatActivity 通过 forward_mode extra 传入。
 */
public class ForwardTargetActivity extends AppCompatActivity {

    private RecyclerView rvTargets;
    private ForwardTargetAdapter adapter;
    private Button btnForward;
    private AppDatabase db;

    private List<Integer> selectedMessageIds;
    private int sourceSessionId;
    private String friendName;
    private int forwardMode; // 0=逐条, 1=合并
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forward_target);

        db = AppDatabase.getDatabase(this);

        // 读取Intent参数
        selectedMessageIds = getIntent().getIntegerArrayListExtra("selected_message_ids");
        sourceSessionId = getIntent().getIntExtra("source_session_id", -1);
        friendName = getIntent().getStringExtra("friend_name");
        forwardMode = getIntent().getIntExtra("forward_mode", 0);

        if (selectedMessageIds == null) selectedMessageIds = new ArrayList<>();

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // RecyclerView
        rvTargets = findViewById(R.id.rvForwardTargets);
        rvTargets.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ForwardTargetAdapter();
        rvTargets.setAdapter(adapter);

        // 底部按钮
        btnForward = findViewById(R.id.btnForward);
        btnForward.setOnClickListener(v -> executeForward());
        adapter.setOnSelectionChangedListener(count -> {
            btnForward.setText("转发到 " + count + " 个会话");
            btnForward.setEnabled(count > 0);
        });

        // 加载可用目标会话
        loadTargetSessions();
    }

    private void loadTargetSessions() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ChatSession> allSessions = db.chatSessionDao().getAllSessionsSync();
            List<ForwardTargetAdapter.SessionInfo> targets = new ArrayList<>();

            if (allSessions != null) {
                for (ChatSession session : allSessions) {
                    if (session.id == sourceSessionId) continue;

                    ForwardTargetAdapter.SessionInfo info = new ForwardTargetAdapter.SessionInfo();
                    info.session = session;

                    Character ch = db.characterDao().getCharacterByIdSync(session.characterId);
                    if (ch != null) {
                        info.characterName = ch.name;
                        info.characterAvatarPath = ch.avatarPath;
                    } else {
                        info.characterName = "角色#" + session.characterId;
                    }

                    targets.add(info);
                }
            }

            runOnUiThread(() -> adapter.setSessions(targets));
        });
    }

    private void executeForward() {
        List<ForwardTargetAdapter.SessionInfo> selected = adapter.getSelectedSessions();
        if (selected.isEmpty()) {
            Toast.makeText(this, "请选择转发目标", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedMessageIds.isEmpty()) {
            Toast.makeText(this, "没有可转发的消息", Toast.LENGTH_SHORT).show();
            return;
        }

        btnForward.setEnabled(false);
        final int totalTargets = selected.size();
        final int expectedMsgCount = selectedMessageIds.size();

        executor.execute(() -> {
            // 按时间戳排序，确保转发后消息顺序为时间正序
            List<Integer> sortedIds = new ArrayList<>(selectedMessageIds);
            java.util.Collections.sort(sortedIds, (a, b) -> {
                Message ma = db.messageDao().getMessageByIdSync(a);
                Message mb = db.messageDao().getMessageByIdSync(b);
                if (ma == null || mb == null) return 0;
                return Long.compare(ma.timestamp, mb.timestamp);
            });

            long now = System.currentTimeMillis();
            String batchId = (forwardMode == 1) ? UUID.randomUUID().toString() : null;
            int successTargets = 0;
            int totalForwarded = 0;
            int totalSkipped = 0;

            for (ForwardTargetAdapter.SessionInfo info : selected) {
                ChatSession targetSession = info.session;
                int sessionForwarded = 0;
                int sessionSkipped = 0;

                if (forwardMode == 0) {
                    // 逐条转发
                    for (int msgId : sortedIds) {
                        try {
                            Message source = db.messageDao().getMessageByIdSync(msgId);
                            if (source == null) {
                                sessionSkipped++;
                                continue;
                            }

                            Message copy = createForwardedMessage(source, targetSession, now);
                            long newMsgId = db.messageDao().insert(copy);

                            // 复制 SharedContent（链接卡片）
                            if (source.type == 7) {
                                try {
                                    SharedContent srcSc = db.sharedContentDao().getByMessageId(source.id);
                                    if (srcSc != null) {
                                        SharedContent newSc = new SharedContent();
                                        newSc.sourceUrl = srcSc.sourceUrl;
                                        newSc.siteName = srcSc.siteName;
                                        newSc.faviconUrl = srcSc.faviconUrl;
                                        newSc.contentTitle = srcSc.contentTitle;
                                        newSc.thumbnailUrl = srcSc.thumbnailUrl;
                                        newSc.description = srcSc.description;
                                        newSc.timestamp = now;
                                        newSc.sessionId = targetSession.id;
                                        newSc.characterId = targetSession.characterId;
                                        newSc.messageId = (int) newMsgId;
                                        db.sharedContentDao().insert(newSc);
                                    }
                                } catch (Exception e) {
                                    // SharedContent 复制失败不影响消息转发
                                }
                            }

                            ForwardRecord record = new ForwardRecord();
                            record.sourceMessageId = source.id;
                            record.targetSessionId = targetSession.id;
                            record.targetCharacterId = targetSession.characterId;
                            record.forwardTimestamp = now;
                            record.forwardedMessageId = (int) newMsgId;
                            db.forwardRecordDao().insert(record);
                            sessionForwarded++;
                        } catch (Exception e) {
                            sessionSkipped++;
                        }
                    }
                } else {
                    // 合并转发 → type=8 卡片
                    Gson gson = new Gson();
                    List<Map<String, String>> msgList = new ArrayList<>();
                    for (int msgId : sortedIds) {
                        Message source = db.messageDao().getMessageByIdSync(msgId);
                        if (source == null) {
                            sessionSkipped++;
                            continue;
                        }
                        String sender = source.isFromUser ? "我" :
                                (friendName != null ? friendName : "对方");
                        String c = source.content != null ? source.content : "";
                        Map<String, String> item = new HashMap<>();
                        item.put("sender", sender);
                        item.put("content", c);
                        msgList.add(item);
                        sessionForwarded++;
                    }

                    if (!msgList.isEmpty()) {
                        try {
                            Map<String, Object> forwardJson = new HashMap<>();
                            forwardJson.put("title", "聊天记录");
                            forwardJson.put("messages", msgList);
                            String jsonContent = gson.toJson(forwardJson);

                            Message mergedMsg = new Message();
                            mergedMsg.sessionId = targetSession.id;
                            mergedMsg.characterId = targetSession.characterId;
                            mergedMsg.content = jsonContent;
                            mergedMsg.type = 8;
                            mergedMsg.isFromUser = true;
                            mergedMsg.timestamp = now;

                            long newMsgId = db.messageDao().insert(mergedMsg);

                            for (Map<String, String> item : msgList) {
                                ForwardRecord record = new ForwardRecord();
                                record.targetSessionId = targetSession.id;
                                record.targetCharacterId = targetSession.characterId;
                                record.forwardTimestamp = now;
                                record.forwardedMessageId = (int) newMsgId;
                                record.forwardBatchId = batchId;
                                db.forwardRecordDao().insert(record);
                            }
                        } catch (Exception e) {
                            sessionSkipped += msgList.size();
                            sessionForwarded -= msgList.size();
                        }
                    }
                }

                targetSession.lastMessageTimestamp = now;
                db.chatSessionDao().update(targetSession);
                totalForwarded += sessionForwarded;
                totalSkipped += sessionSkipped;
                successTargets++;
            }

            final int finalForwarded = totalForwarded;
            final int finalSkipped = totalSkipped;
            final int finalTargets = successTargets;
            runOnUiThread(() -> {
                String msg = "已转发 " + finalForwarded + " 条消息到 " + finalTargets + " 个会话";
                if (finalSkipped > 0) {
                    msg += "（" + finalSkipped + " 条跳过）";
                }
                Toast.makeText(ForwardTargetActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }

    private Message createForwardedMessage(Message source, ChatSession targetSession, long timestamp) {
        Message copy = new Message();
        copy.sessionId = targetSession.id;
        copy.characterId = targetSession.characterId;
        copy.content = source.content;
        copy.type = source.type;
        copy.isFromUser = true; // 转发消息始终显示为用户发送
        copy.timestamp = timestamp;
        copy.voiceUrl = source.voiceUrl;
        copy.imageUrl = source.imageUrl;
        copy.imageDesc = source.imageDesc;
        copy.quoteMessageId = -1;
        return copy;
    }
}
