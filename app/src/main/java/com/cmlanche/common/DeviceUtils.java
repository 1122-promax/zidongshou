package com.cmlanche.common;

import android.os.Build;
import android.provider.Settings;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.utils.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public class DeviceUtils {

    /**
     * 获取设备唯一标识，多策略兜底确保不返回 unknown
     */
    public static String getDeviceSN() {
        String serial;

        // 1. Build.SERIAL (Android 8+ 可能返回 "unknown")
        serial = Build.SERIAL;
        if (isValid(serial)) return serial;

        // 2. 反射 ro.serialno
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            serial = (String) get.invoke(c, "ro.serialno");
            if (isValid(serial)) return serial;
        } catch (Exception ignored) {}

        // 3. Build.getSerial() (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                serial = Build.getSerial();
                if (isValid(serial)) return serial;
            } catch (SecurityException ignored) {}
        }

        // 4. Settings.Secure.ANDROID_ID (可靠兜底)
        try {
            serial = Settings.Secure.getString(
                    MyApplication.getAppInstance().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (isValid(serial)) return serial;
        } catch (Exception ignored) {}

        // 5. UUID 兜底 (每次生成一致，基于 AndroidId)
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(
                    MyApplication.getAppInstance().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {}
        return new UUID(
                androidId.hashCode(),
                Build.MODEL.hashCode() ^ ((long) Build.DEVICE.hashCode() << 32)
        ).toString().substring(0, 12).toUpperCase();
    }

    private static boolean isValid(String s) {
        return s != null && !s.isEmpty()
                && !"unknown".equalsIgnoreCase(s)
                && !"null".equalsIgnoreCase(s)
                && !"000000000000000".equals(s);
    }
}
