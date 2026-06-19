package com.cmlanche.ocrcore;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.cmlanche.application.MyApplication;
import com.cmlanche.common.SPService;
import com.cmlanche.core.utils.Logger;

import java.nio.ByteBuffer;

/**
 * 屏幕截图管理器
 * 基于 MediaProjection API（官方标准，无需 root，模拟器和真机通用）
 * 用户只需首次授权一次，后续自动复用
 */
public class ScreenCaptureManager {

    private static final String TAG = "ScreenCapture";
    private static final String SP_INTENT_URI = "mp_intent_uri";
    private static final String SP_RESULT_CODE = "mp_result_code";

    private static final Object lock = new Object();
    private static MediaProjection mediaProjection;
    private static ImageReader imageReader;
    private static VirtualDisplay virtualDisplay;
    private static HandlerThread captureThread;
    private static Handler captureHandler;
    private static boolean initialized = false;

    /**
     * 初始化 MediaProjection
     * 优先从上次保存的数据恢复，失败时返回 false（需要用户授权）
     */
    public static boolean init() {
        if (initialized) return true;

        String intentUri = SPService.getString(SP_INTENT_URI, null);
        int resultCode = SPService.getInt(SP_RESULT_CODE, 0);

        if (intentUri == null || resultCode == 0) {
            Logger.i("未找到已保存的截图权限，需要用户授权");
            return false;
        }

        try {
            Intent intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
            return createMediaProjection(resultCode, intent);
        } catch (Exception e) {
            Logger.e("恢复截图权限失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建 MediaProjection（授权后调用）
     */
    /**
     * 创建 MediaProjection，返回失败原因字符串
     * @return null 表示成功，非null 表示失败原因
     */
    public static String createMediaProjectionWithResult(int resultCode, Intent data) {
        if (mediaProjection != null) {
            release();
        }

        try {
            Logger.i("开始创建 MediaProjection...");
            MediaProjectionManager mgr = (MediaProjectionManager)
                    MyApplication.getAppInstance().getSystemService(Service.MEDIA_PROJECTION_SERVICE);
            if (mgr == null) {
                return "设备不支持 MediaProjection";
            }

            Logger.i("获取 MediaProjection 实例...");
            mediaProjection = mgr.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                return "getMediaProjection 返回 null";
            }
            Logger.i("MediaProjection 实例获取成功");

            // 保存授权数据
            SPService.putString(SP_INTENT_URI, data.toUri(Intent.URI_INTENT_SCHEME));
            SPService.putInt(SP_RESULT_CODE, resultCode);

            // 创建 ImageReader
            int w = MyApplication.getAppInstance().getScreenWidth();
            int h = MyApplication.getAppInstance().getScreenHeight();
            int dpi = MyApplication.getAppInstance().getResources().getDisplayMetrics().densityDpi;
            Logger.i("屏幕参数: " + w + "x" + h + " @ " + dpi + "dpi");

            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);

            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenshotDisplay",
                    w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null
            );

            if (virtualDisplay == null) {
                release();
                return "createVirtualDisplay 返回 null";
            }

            initialized = true;
            Logger.i("MediaProjection 初始化成功");
            return null;
        } catch (SecurityException e) {
            String msg = "权限不足: " + e.getMessage();
            Logger.e(msg);
            release();
            return msg;
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            Logger.e("MediaProjection 初始化失败: " + msg);
            release();
            return msg;
        }
    }

    /**
     * 创建 MediaProjection（返回 boolean，保持向后兼容）
     */
    public static boolean createMediaProjection(int resultCode, Intent data) {
        if (mediaProjection != null) {
            release();
        }

        try {
            Logger.i("开始创建 MediaProjection...");
            MediaProjectionManager mgr = (MediaProjectionManager)
                    MyApplication.getAppInstance().getSystemService(Service.MEDIA_PROJECTION_SERVICE);
            if (mgr == null) {
                Logger.e("设备不支持 MediaProjection");
                return false;
            }
            
            Logger.i("获取 MediaProjection 实例...");
            mediaProjection = mgr.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Logger.e("getMediaProjection 返回 null");
                return false;
            }
            Logger.i("MediaProjection 实例获取成功");

            // 保存授权数据，下次自动恢复
            SPService.putString(SP_INTENT_URI, data.toUri(Intent.URI_INTENT_SCHEME));
            SPService.putInt(SP_RESULT_CODE, resultCode);

            // 初始化截图线程
            if (captureThread == null) {
                captureThread = new HandlerThread("ScreenCapture");
                captureThread.start();
                captureHandler = new Handler(captureThread.getLooper());
                Logger.i("截图线程已创建");
            }

            // 创建 ImageReader
            int w = MyApplication.getAppInstance().getScreenWidth();
            int h = MyApplication.getAppInstance().getScreenHeight();
            int dpi = MyApplication.getAppInstance().getResources().getDisplayMetrics().densityDpi;
            Logger.i("屏幕参数: " + w + "x" + h + " @ " + dpi + "dpi");
            
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            Logger.i("ImageReader 创建成功");

            // 创建 VirtualDisplay
            Logger.i("创建 VirtualDisplay...");
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenshotDisplay",
                    w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null
            );
            
            if (virtualDisplay == null) {
                Logger.e("createVirtualDisplay 返回 null");
                release();
                return false;
            }
            Logger.i("VirtualDisplay 创建成功");

            initialized = true;
            Logger.i("MediaProjection 初始化成功");
            return true;
        } catch (Exception e) {
            Logger.e("MediaProjection 初始化失败: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            release();
            return false;
        }
    }

    /**
     * 截取当前屏幕
     * 使用 acquireNextImage() 阻塞等待帧就绪，而非 acquireLatestImage()（可能返回null）
     */
    public static Bitmap captureScreen() {
        if (!initialized || imageReader == null) {
            return null;
        }

        synchronized (lock) {
            try {
                // 使用 acquireNextImage() 阻塞等待下一帧就绪（最多等 1 秒）
                Image image = acquireNextImageWithTimeout(imageReader, 1000);
                if (image == null) {
                    Logger.e("截图超时：未获取到帧");
                    return null;
                }

                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) {
                    image.close();
                    return null;
                }

                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * image.getWidth();

                int w = image.getWidth();
                int h = image.getHeight();

                Bitmap bitmap = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                if (rowPadding > 0) {
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h);
                }

                image.close();
                Logger.i("MediaProjection 截图成功: " + w + "x" + h);
                return bitmap;
            } catch (Exception e) {
                Logger.e("MediaProjection 截图失败: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * 使用 acquireNextImage() 阻塞等待新帧，带超时
     */
    private static Image acquireNextImageWithTimeout(ImageReader reader, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Image image = reader.acquireNextImage();
            if (image != null) return image;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        return null;
    }

    /**
     * 是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 释放资源
     */
    public static void release() {
        initialized = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
