package com.cmlanche.ocrcore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.cmlanche.core.utils.Logger;

/**
 * 屏幕截图前台服务
 * Android 14+ 要求 MediaProjection 必须由前台服务持有
 * 仅为了满足此要求，实际截图由 ScreenCaptureManager 完成
 */
public class ScreenCaptureService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "screen_capture";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("自动手")
                .setContentText("屏幕识别服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true);

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        Logger.i("ScreenCaptureService 已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("result_code", 0);
            Intent data = intent.getParcelableExtra("data");
            if (data != null) {
                ScreenCaptureManager.createMediaProjection(resultCode, data);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ScreenCaptureManager.release();
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕识别", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
