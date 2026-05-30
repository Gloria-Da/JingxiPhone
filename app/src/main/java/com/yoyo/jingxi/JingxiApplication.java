package com.yoyo.jingxi;

import android.app.Application;

import com.yoyo.jingxi.utils.SpUtils;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.appcompat.app.AppCompatDelegate;

import com.yoyo.jingxi.utils.ThemeManager;
import com.yoyo.jingxi.worker.AutoMomentWorker;
import com.yoyo.jingxi.worker.WeatherReminderWorker;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class JingxiApplication extends Application {
    
    private static JingxiApplication instance;
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;

    public static JingxiApplication getInstance() {
        return instance;
    }

    public boolean isAppInForeground() {
        return activityReferences > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        SpUtils.init(this);
        com.yoyo.jingxi.utils.ImageGenerationManager.init(this);

        // 恢复夜间模式设置
        int nightMode = ThemeManager.getNightMode(this);
        AppCompatDelegate.setDefaultNightMode(nightMode);
        
        // 启动后台定时任务
        setupAutoMessageWorker();
        setupWeatherReminderWorker();
        
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                ThemeManager.applyTheme(activity);
            }

            @Override
            public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (!(activity instanceof com.yoyo.jingxi.ui.activity.DesktopActivity) &&
                    !(activity instanceof com.yoyo.jingxi.ui.activity.ChatActivity)) {
                    ThemeManager.applyGlobalBackground(activity);
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                    // App enters foreground
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (!(activity instanceof com.yoyo.jingxi.ui.activity.DesktopActivity) &&
                    !(activity instanceof com.yoyo.jingxi.ui.activity.ChatActivity)) {
                    ThemeManager.applyGlobalBackground(activity);
                }

                if (activityReferences == 1 && !isActivityChangingConfigurations) {
                    android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                    if (notificationManager != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            android.service.notification.StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                            for (android.service.notification.StatusBarNotification notification : activeNotifications) {
                                // Keep AiReplyService notification (1001), CallForegroundService notification (1002), etc.
                                // We cancel everything else (new message notifications, moment notifications, etc.)
                                if (notification.getId() != 1001 && notification.getId() != 1002 && notification.getId() != 1003) {
                                    notificationManager.cancel(notification.getId());
                                }
                            }
                        } else {
                            // On older versions we might cancel all, but it might kill foreground services as well. 
                            // It's safer to only cancel known ones if possible, but without getActiveNotifications it's hard.
                            // We can just rely on autoCancel for individual ones on older APIs.
                        }
                    }
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations();
                if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                    // App enters background
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    private void setupAutoMessageWorker() {
        // 定期检查主动消息
        androidx.work.PeriodicWorkRequest autoMessageWorkRequest = new androidx.work.PeriodicWorkRequest.Builder(
                com.yoyo.jingxi.worker.AutoMessageWorker.class, 
                15, java.util.concurrent.TimeUnit.MINUTES // 每15分钟检查一次
        ).build();
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AutoMessageWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                autoMessageWorkRequest
        );

        // 定期检查是否发朋友圈
        androidx.work.PeriodicWorkRequest autoMomentWorkRequest = new androidx.work.PeriodicWorkRequest.Builder(
                com.yoyo.jingxi.worker.AutoMomentWorker.class,
                15, java.util.concurrent.TimeUnit.MINUTES // 每15分钟检查一次是否发动态，具体限制在 Worker 内判断
        ).build();
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AutoMomentWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                autoMomentWorkRequest
        );
    }

    private void setupWeatherReminderWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest weatherReminderRequest =
                new PeriodicWorkRequest.Builder(WeatherReminderWorker.class, 12, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WeatherReminderWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                weatherReminderRequest
        );
    }
}
