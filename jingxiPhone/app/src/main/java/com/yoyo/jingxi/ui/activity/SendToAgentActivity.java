package com.yoyo.jingxi.ui.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.SharedContent;
import com.yoyo.jingxi.utils.LinkMetadataExtractor;
import com.yoyo.jingxi.utils.ThemeManager;

import java.util.List;

/**
 * 接收外部App（B站、小红书、抖音等）分享的链接。
 * 显示角色选择对话框，提取链接元数据，创建聊天消息并跳转到ChatActivity。
 *
 * Intent filter: ACTION_SEND + text/plain
 */
public class SendToAgentActivity extends AppCompatActivity {

    private AppDatabase db;
    private List<Character> characters;
    private List<MyPersona> personas;
    private String sharedUrl;
    private String sharedText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        db = AppDatabase.getDatabase(this);

        // 提取分享内容
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                sharedText = sharedText.trim();
            }
        }

        if (sharedText == null || sharedText.isEmpty()) {
            Toast.makeText(this, "未获取到分享内容", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 提取URL（纯文本也允许分享）
        sharedUrl = extractUrl(sharedText);

        // 加载角色列表
        new Thread(() -> {
            characters = db.characterDao().getAllCharactersSync();
            personas = db.myPersonaDao().getAllPersonasSync();

            if (characters == null || characters.isEmpty()) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "请先创建角色", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            runOnUiThread(this::showCharacterPicker);
        }).start();
    }

    /**
     * 从分享文本中提取URL。如果文本本身就是URL则返回它；
     * 如果文本中包含URL则提取第一个；否则返回null（纯文本分享）。
     */
    private String extractUrl(String text) {
        if (text.startsWith("http://") || text.startsWith("https://")) {
            // 提取到第一个空格为止
            int spaceIdx = text.indexOf(' ');
            if (spaceIdx > 0) {
                return text.substring(0, spaceIdx);
            }
            return text;
        }
        // 尝试从文本中查找URL
        int httpIdx = text.indexOf("http://");
        int httpsIdx = text.indexOf("https://");
        int startIdx = -1;
        if (httpsIdx >= 0) {
            startIdx = httpsIdx;
        } else if (httpIdx >= 0) {
            startIdx = httpIdx;
        }
        if (startIdx >= 0) {
            String candidate = text.substring(startIdx);
            int spaceIdx = candidate.indexOf(' ');
            if (spaceIdx > 0) {
                return candidate.substring(0, spaceIdx);
            }
            return candidate;
        }
        return null;
    }

    /**
     * 显示角色选择对话框
     */
    private void showCharacterPicker() {
        String[] names = new String[characters.size()];
        for (int i = 0; i < characters.size(); i++) {
            names[i] = characters.get(i).name;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择分享对象")
                .setItems(names, (dialog, which) -> {
                    Character selectedChar = characters.get(which);
                    showPersonaPicker(selectedChar);
                })
                .setNegativeButton("取消", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * 显示人设选择对话框（如果有多个人设）
     */
    private void showPersonaPicker(Character selectedChar) {
        if (personas == null || personas.isEmpty()) {
            processShare(selectedChar, null);
            return;
        }

        if (personas.size() == 1) {
            processShare(selectedChar, personas.get(0));
            return;
        }

        String[] personaNames = new String[personas.size()];
        for (int i = 0; i < personas.size(); i++) {
            personaNames[i] = personas.get(i).name;
            if (personas.get(i).isMainPersona) {
                personaNames[i] += " (主)";
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择人设")
                .setItems(personaNames, (dialog, which) -> {
                    processShare(selectedChar, personas.get(which));
                })
                .setNegativeButton("取消", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * 执行分享流程：后台提取元数据 → 创建Session/Message/SharedContent → 跳转ChatActivity
     */
    private void processShare(Character character, MyPersona persona) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在获取链接信息…");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                String personaName = persona != null ? persona.name : "我";

                // 1. 查找或创建ChatSession
                ChatSession session = db.chatSessionDao()
                        .getSessionByCharacterAndPersona(character.id, personaName);
                if (session == null) {
                    session = new ChatSession();
                    session.characterId = character.id;
                    session.myPersonaName = personaName;
                    long sessionId = db.chatSessionDao().insert(session);
                    session.id = (int) sessionId;
                }

                // 2. 提取链接元数据（如果是URL）
                SharedContent sharedContent = null;
                String messageContent;

                if (sharedUrl != null) {
                    LinkMetadataExtractor.LinkMetadata meta = LinkMetadataExtractor.extract(sharedUrl);
                    sharedContent = new SharedContent();
                    sharedContent.sourceUrl = sharedUrl;
                    sharedContent.siteName = meta.siteName;
                    sharedContent.faviconUrl = meta.faviconUrl;
                    sharedContent.contentTitle = meta.title;
                    sharedContent.thumbnailUrl = meta.imageUrl;
                    sharedContent.description = meta.description;
                    sharedContent.timestamp = System.currentTimeMillis();
                    sharedContent.sessionId = session.id;
                    sharedContent.characterId = character.id;

                    messageContent = "[分享] " +
                            (meta.title != null ? meta.title : sharedUrl);
                } else {
                    // 纯文本分享
                    messageContent = sharedText;
                }

                // 3. 创建Message (type=7 表示分享内容)
                Message message = new Message();
                message.sessionId = session.id;
                message.characterId = character.id;
                message.content = messageContent;
                message.type = (sharedUrl != null) ? 7 : 0; // type 7 = 分享内容
                message.isFromUser = true;
                message.timestamp = System.currentTimeMillis();

                long messageId = db.messageDao().insert(message);

                // 4. 关联 SharedContent 到 Message
                if (sharedContent != null) {
                    sharedContent.messageId = (int) messageId;
                    db.sharedContentDao().insert(sharedContent);
                }

                // 5. 更新会话时间戳
                session.lastMessageTimestamp = System.currentTimeMillis();
                db.chatSessionDao().update(session);

                // 6. 跳转到ChatActivity
                final int finalSessionId = session.id;
                final int finalSharedContentId = sharedContent != null ? sharedContent.id : 0;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Intent chatIntent = new Intent(SendToAgentActivity.this, ChatActivity.class);
                    chatIntent.putExtra("session_id", finalSessionId);
                    chatIntent.putExtra("friend_name", character.name);
                    chatIntent.putExtra("shared_content_id", finalSharedContentId);
                    chatIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(chatIntent);
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(SendToAgentActivity.this,
                            "分享失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }
}
