package com.yoyo.jingxi.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.utils.CallIncomingNotificationHelper;

import java.util.concurrent.Executors;

/**
 * 处理来电通知的"拒绝"操作。
 * 记录未接来电、插入系统消息、停止铃声并取消通知。
 */
public class CallActionReceiver extends BroadcastReceiver {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_CHARACTER_NAME = "character_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
        int characterId = intent.getIntExtra(EXTRA_CHARACTER_ID, -1);
        String characterName = intent.getStringExtra(EXTRA_CHARACTER_NAME);

        // 停止铃声、震动和通知
        CallIncomingNotificationHelper.stopRinging(context);

        if (sessionId == -1 || characterId == -1) return;

        // 在后台线程记录未接来电
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);

            // 创建一条未接通的通话记录
            CallRecord record = new CallRecord();
            record.sessionId = sessionId;
            record.characterId = characterId;
            record.startTime = System.currentTimeMillis();
            record.duration = 0; // 未接通
            record.summary = "";
            record.initiator = 1;   // AI主动发起的来电
            record.isMissed = true; // 用户拒绝/未接
            db.callRecordDao().insert(record);

            // 插入系统消息
            String name = characterName != null ? characterName : "对方";
            Message missedMsg = new Message();
            missedMsg.sessionId = sessionId;
            missedMsg.characterId = characterId;
            missedMsg.content = name + " 来电未接";
            missedMsg.isFromUser = false;
            missedMsg.type = 100; // 系统上下文消息（不可见气泡）
            missedMsg.timestamp = System.currentTimeMillis();
            db.messageDao().insert(missedMsg);

            // 通知 UI 刷新
            Intent updateIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
            updateIntent.setPackage(context.getPackageName());
            updateIntent.putExtra("session_id", sessionId);
            context.sendBroadcast(updateIntent);
        });
    }
}
