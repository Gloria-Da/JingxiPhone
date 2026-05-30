package com.yoyo.jingxi.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.ui.activity.ChatActivity;
import com.yoyo.jingxi.ui.activity.ChatMainActivity;
import com.yoyo.jingxi.utils.SpUtils;

import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiReplyService extends Service {

    public static final String ACTION_START_REPLY = "com.yoyo.jingxi.action.START_REPLY";
    public static final String ACTION_UPDATE_NOTIFICATION = "com.yoyo.jingxi.action.UPDATE_NOTIFICATION";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_NOTIFICATION_TEXT = "notification_text";

    private static final String CHANNEL_ID = "ai_reply_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ExecutorService executorService;
    private com.yoyo.jingxi.network.OpenAIManager aiManager;
    private com.yoyo.jingxi.data.AppDatabase db;
    private PowerManager.WakeLock wakeLock;

    private android.content.BroadcastReceiver updateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
                String text = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT);
                if (text != null) {
                    Notification notification = createNotification(text);
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, notification);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jingxi:AiReplyWakeLock");
        }
        aiManager = new com.yoyo.jingxi.network.OpenAIManager();
        db = com.yoyo.jingxi.data.AppDatabase.getDatabase(this);
        createNotificationChannel();
        
        android.content.IntentFilter filter = new android.content.IntentFilter(ACTION_UPDATE_NOTIFICATION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            // ignore
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        releaseWakeLock();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_REPLY.equals(intent.getAction())) {
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            int characterId = intent.getIntExtra(EXTRA_CHARACTER_ID, -1);

            if (sessionId != -1 && characterId != -1) {
                // 获取 WakeLock，防止休眠
                acquireWakeLock();
                // 将服务置于前台，防止系统杀掉网络请求
                startForeground(NOTIFICATION_ID, createNotification("AI 正在思考回复..."));
                requestAiReply(sessionId, characterId);
            }
        }
        return START_NOT_STICKY; // 不自动重启，除非有新请求
    }

    private void requestAiReply(int sessionId, int characterId) {
        executorService.execute(() -> {
            try {
                com.yoyo.jingxi.utils.AiReplyHelper.requestAiReplySynchronous(this, sessionId, characterId, null);
            } finally {
                // 不立刻移除前台通知，而是降级为普通通知，让新消息通知起作用，再结束服务
                // 这样避免在网络请求完成后由于切后台立刻被杀，导致本地通知发不出
                stopForeground(false);
                stopSelf();
                releaseWakeLock();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "AI Reply Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, com.yoyo.jingxi.ui.activity.DesktopActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
                
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Jingxi")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
