package com.yoyo.jingxi.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yoyo.jingxi.R;

public class ImageGenForegroundService extends Service {
    private static final String CHANNEL_ID = "ImageGenServiceChannel";
    private static final int NOTIFICATION_ID = 1002;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra("moment_id")) {
            int momentId = intent.getIntExtra("moment_id", -1);
            if (momentId != -1) {
                new Thread(() -> {
                    com.yoyo.jingxi.data.entity.Moment moment = com.yoyo.jingxi.data.AppDatabase.getDatabase(this).momentDao().getMomentByIdSync(momentId);
                    if (moment != null) {
                        com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
                    }
                }).start();
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("图片生成中")
                .setContentText("正在后台为您生成动态图片...")
                .setSmallIcon(R.drawable.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "图片生成服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
