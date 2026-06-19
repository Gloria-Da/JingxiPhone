package com.yoyo.jingxi.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.receiver.CallActionReceiver;
import com.yoyo.jingxi.ui.activity.CallActivity;

/**
 * 管理 AI 来电的横幅通知、铃声和震动的生命周期。
 */
public class CallIncomingNotificationHelper {

    private static final String CHANNEL_ID = "call_incoming_channel";
    private static final int NOTIFICATION_ID = 2001;

    private static Ringtone currentRingtone;
    private static Vibrator currentVibrator;

    /**
     * 发送 AI 来电的 Heads-up 通知，并启动铃声和震动。
     */
    public static void sendIncomingCallNotification(Context context, int sessionId, int characterId,
                                                     String characterName, String initialMessage) {
        // Android 13+ 通知权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("CallNotification", "Notification permission not granted, skipping.");
                return;
            }
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建通知渠道（IMPORTANCE_HIGH 以触发 Heads-up 横幅）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "来电通知", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("AI 主动来电通知");
            channel.enableVibration(true);
            channel.setBypassDnd(true); // 免打扰模式下也显示
            notificationManager.createNotificationChannel(channel);
        }

        String name = characterName != null ? characterName : "对方";
        String content = (initialMessage != null && !initialMessage.isEmpty())
                ? initialMessage : "邀请你通话...";

        // "接听" PendingIntent → 启动 CallActivity
        Intent acceptIntent = new Intent(context, CallActivity.class);
        acceptIntent.putExtra("session_id", sessionId);
        acceptIntent.putExtra("is_incoming", true);
        acceptIntent.putExtra("initial_message", initialMessage);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                context, sessionId, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "拒绝" PendingIntent → CallActionReceiver
        Intent rejectIntent = new Intent(context, CallActionReceiver.class);
        rejectIntent.putExtra(CallActionReceiver.EXTRA_SESSION_ID, sessionId);
        rejectIntent.putExtra(CallActionReceiver.EXTRA_CHARACTER_ID, characterId);
        rejectIntent.putExtra(CallActionReceiver.EXTRA_CHARACTER_NAME, name);
        PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(
                context, sessionId, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(name + " 来电")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .addAction(android.R.drawable.ic_menu_call, "接听", acceptPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "拒绝", rejectPendingIntent)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);

        // 开始播放铃声和震动
        startRinging(context);
    }

    /**
     * 返回当前是否正在播放铃声。
     */
    public static boolean isRinging() {
        return currentRingtone != null;
    }

    /**
     * 开始播放铃声（循环）和震动（重复模式）。
     * 供外部（CallActivity 全屏来电）和内部（sendIncomingCallNotification）共同调用。
     */
    public static void startRinging(Context context) {
        // 先停止之前的
        stopRingingInternal();

        // --- 震动 ---
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 400, 400}; // 震400ms，停400ms，重复（模拟电话铃震）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // repeat=0 从第一个元素开始
            } else {
                vibrator.vibrate(pattern, 0);
            }
            currentVibrator = vibrator;
        }

        // --- 铃声 ---
        String uriString = SpUtils.getString("RINGTONE_URI", "");
        Uri ringtoneUri;
        if (!uriString.isEmpty()) {
            ringtoneUri = Uri.parse(uriString);
        } else {
            // 默认使用系统来电铃声
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        Ringtone ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
        if (ringtone != null) {
            // 设置音频属性为铃声类型
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            ringtone.play();
            currentRingtone = ringtone;
        }
    }

    /**
     * 停止铃声和震动，并取消通知。
     * 在用户接听（进入 CallActivity）或拒绝（CallActionReceiver）时调用。
     */
    public static void stopRinging(Context context) {
        stopRingingInternal();

        // 取消通知
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private static void stopRingingInternal() {
        if (currentRingtone != null) {
            currentRingtone.stop();
            currentRingtone = null;
        }
        if (currentVibrator != null) {
            currentVibrator.cancel();
            currentVibrator = null;
        }
    }
}
