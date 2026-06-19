package com.cmlanche.ocrcore;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.cmlanche.application.MyApplication;
import com.cmlanche.common.SPService;
import com.cmlanche.core.service.MyAccessbilityService;
import com.cmlanche.core.utils.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 截屏工具类
 *
 * 截图策略：
 * - 手机模式：无障碍截图 → shell screencap
 * - 模拟器模式：无障碍截图 → MediaProjection → shell screencap
 *
 * 在 MainActivity 中切换设备模式。
 */
public class ScreencapUtil {

    private static final int PROCESS_TIMEOUT_SECONDS = 2;
    private static long lastCaptureTime = 0;
    private static final long MIN_CAPTURE_INTERVAL_MS = 500; // 强制间隔0.5秒

    // 是否已检查过模式（只需读取一次，减少SP读取）
    private static Boolean isEmulatorMode = null;

    private static boolean isEmulator() {
        if (isEmulatorMode == null) {
            isEmulatorMode = "emulator".equals(SPService.getString(SPService.DEVICE_MODE, "phone"));
        }
        return isEmulatorMode;
    }

    /** 重置模式缓存（切换模式后调用） */
    public static void resetModeCache() {
        isEmulatorMode = null;
    }

    /**
     * 截取当前屏幕
     */
    public static Bitmap captureScreen() {
        // 强制间隔保护
        long now = System.currentTimeMillis();
        long elapsed = now - lastCaptureTime;
        if (elapsed < MIN_CAPTURE_INTERVAL_MS) {
            try { Thread.sleep(MIN_CAPTURE_INTERVAL_MS - elapsed); } catch (InterruptedException ignored) {}
        }
        lastCaptureTime = System.currentTimeMillis();

        // 统一优先无障碍截图（最快最稳）
        Bitmap as = captureViaAccessibility();
        if (as != null) { Logger.i("截图: 无障碍服务 " + as.getWidth() + "x" + as.getHeight()); return as; }

        // 模拟器模式多尝试 MediaProjection，手机模式跳过（未授权时创建 VirtualDisplay 冲突）
        if (isEmulator()) {
            Bitmap mp = captureViaMediaProjection();
            if (mp != null) { Logger.i("截图: MediaProjection " + mp.getWidth() + "x" + mp.getHeight()); return mp; }
        }

        // 通用兜底：shell screencap
        Bitmap shell = captureViaShell();
        if (shell != null) { Logger.i("截图: shell screencap " + shell.getWidth() + "x" + shell.getHeight()); return shell; }

        Logger.e("截图失败：方案均不可用");
        return null;
    }

    private static Bitmap captureViaAccessibility() {
        MyAccessbilityService as = MyApplication.getAppInstance().getAccessbilityService();
        // 先检查无障碍服务是否正常工作，避免卡住
        if (as != null && as.isWrokFine()) {
            return as.takeScreenshot(800);
        }
        return null;
    }

    private static Bitmap captureViaMediaProjection() {
        for (int i = 0; i < 30; i++) {
            if (ScreenCaptureManager.isInitialized()) {
                Bitmap bitmap = ScreenCaptureManager.captureScreen();
                if (bitmap != null) return bitmap;
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        if (!ScreenCaptureManager.isInitialized()) {
            if (ScreenCaptureManager.init()) {
                return ScreenCaptureManager.captureScreen();
            }
        }
        return null;
    }

    private static Bitmap captureViaShell() {
        Process process = null;
        InputStream in = null;
        StreamGobbler gobbler = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/screencap", "-d", "0", "-p"});
            gobbler = new StreamGobbler(process.getErrorStream(), "sc");
            in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
            byte[] buf = new byte[4096];
            // 5秒超时保护
            long deadline = System.currentTimeMillis() + PROCESS_TIMEOUT_SECONDS * 1000;
            while (System.currentTimeMillis() < deadline) {
                while (in.available() > 0) {
                    int n = in.read(buf);
                    if (n == -1) break;
                    out.write(buf, 0, n);
                }
                if (out.size() > 0) {
                    try { int exit = process.exitValue(); } catch (IllegalThreadStateException e) {
                        // 进程还在跑，再等等
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    break;
                }
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            gobbler.join(500);
            byte[] data = out.toByteArray();
            if (data.length >= 100) {
                return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        } catch (Exception e) {
            Logger.e("screencap: " + e.getMessage());
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            if (process != null) process.destroy();
        }
        return null;
    }

    private static class StreamGobbler extends Thread {
        private final InputStream is;
        StreamGobbler(InputStream is, String name) { super(name); this.is = is; setDaemon(true); start(); }
        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                while (br.readLine() != null) {}
            } catch (IOException ignored) {}
        }
    }
}
