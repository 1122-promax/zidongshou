package com.cmlanche.core.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;

import com.cmlanche.activity.MainActivity;
import com.cmlanche.application.MyApplication;
import com.cmlanche.core.bus.BusEvent;
import com.cmlanche.core.bus.BusManager;
import com.cmlanche.core.bus.EventType;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.StringUtil;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.jixieshou.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.cmlanche.core.bus.EventType.accessiblity_connected;

public class MyAccessbilityService extends AccessibilityService {

    private boolean isWork = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Logger.d("MyAccessbilityService event: " + event);
    }

    @Override
    public void onInterrupt() {
        Logger.e("MyAccessbilityService 服务被Interrupt");
    }

    public AccessibilityNodeInfo[] getRoots() {
        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        String activeRootPkg = Utils.getRootPackageName(activeRoot);

        Map<String, AccessibilityNodeInfo> map = new HashMap<>();
        if(activeRoot != null){
            map.put(activeRootPkg, activeRoot);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if(w.getRoot() == null || getPackageName().equals(Utils.getRootPackageName(w.getRoot()))) {
                    continue;
                }
                String rootPkg = Utils.getRootPackageName(w.getRoot());
                if(getPackageName().equals(rootPkg)) {
                    continue;
                }
                if(rootPkg.equals(activeRootPkg)) {
                    continue;
                }
                map.put(rootPkg, w.getRoot());
            }
        }
        if (map.isEmpty()) {
            Logger.i("当前无可用界面节点（map为空）");
            if(isWork) {
                // 只是暂时拿不到节点，不触发暂停
                Logger.i("但服务仍在工作中，继续尝试");
            }
        } else {
            if(!isWork) {
                MainActivity mainActivity = MyApplication.getAppInstance().getMainActivity();
                if (mainActivity != null) {
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BusManager.getBus().post(new BusEvent<>(EventType.roots_ready));
                        }
                    });
                }
            }
            isWork = true;
        }
        return map.values().toArray(new AccessibilityNodeInfo[0]);
    }

    public boolean containsPkg(String pkg) {
        if(StringUtil.isEmpty(pkg)) {
            return false;
        }
        AccessibilityNodeInfo[] roots = getRoots();
        for(AccessibilityNodeInfo root: roots) {
            if(pkg.equals(Utils.getRootPackageName(root))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d("MyAccessbilityService on create");
        sInstance = this;
        BusManager.getBus().post(new BusEvent<>(EventType.set_accessiblity, this));
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Logger.d("MyAccessbilityService on start");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("MyAccessbilityService on start command");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Logger.d("MyAccessbilityService on unbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Logger.d("MyAccessbilityService on rebind");
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Logger.d("MyAccessbilityService on task removed");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Logger.d("MyAccessbilityService connected");
        BusManager.getBus().post(new BusEvent<>(accessiblity_connected));
        isWork = true;
    }

    public boolean isWrokFine() {
        return isWork;
    }

    /**
     * 通过无障碍服务截图（Android 14+ API 34）
     * 无需额外权限，只要用户开启了无障碍服务即可
     * @param timeoutMs 超时毫秒
     * @return Bitmap 或 null
     */
    public Bitmap takeScreenshot(long timeoutMs) {
        if (Build.VERSION.SDK_INT < 34) {
            Logger.i("无障碍截图需要 Android 14+，当前: " + Build.VERSION.SDK_INT);
            return null;
        }
        try {
            final AtomicReference<Bitmap> result = new AtomicReference<>(null);
            final CountDownLatch latch = new CountDownLatch(1);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            // 必须在主线程调用
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        takeScreenshot(Display.DEFAULT_DISPLAY,
                                new Executor() {
                                    @Override
                                    public void execute(Runnable command) {
                                        command.run();
                                    }
                                },
                                new TakeScreenshotCallback() {
                                    @Override
                                    public void onSuccess(ScreenshotResult screenshotResult) {
                                        HardwareBuffer hwBuffer = screenshotResult.getHardwareBuffer();
                                        if (hwBuffer != null) {
                                            Bitmap bm = Bitmap.wrapHardwareBuffer(hwBuffer, null);
                                            if (bm != null) result.set(bm);
                                            hwBuffer.close();
                                        }
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onFailure(int errorCode) {
                                        Logger.e("无障碍截图失败: errorCode=" + errorCode);
                                        latch.countDown();
                                    }
                                });
                    } catch (Exception e) {
                        Logger.e("无障碍截图异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        latch.countDown();
                    }
                }
            });

            boolean done = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                Logger.e("无障碍截图超时");
                return null;
            }
            Bitmap bm = result.get();
            if (bm != null) {
                Logger.i("无障碍截图成功 " + bm.getWidth() + "x" + bm.getHeight());
            }
            return bm;
        } catch (Exception e) {
            Logger.e("无障碍截图异常: " + e.getMessage());
            return null;
        }
    }

    // ========== 稳定的无障碍悬浮窗（TYPE_ACCESSIBILITY_OVERLAY，不会被系统杀掉） ==========

    private static MyAccessbilityService sInstance;
    private View overlayView;
    private WindowManager overlayWm;
    private TextView overlayLogText;
    private TextView overlayStatusText;
    private String cachedOverlayLog = "等待启动...";
    private String cachedOverlayStatus = "";

    /** 显示无障碍悬浮窗（不可被系统杀掉） */
    public void showOverlayWindow() {
        if (overlayView != null) return;
        try {
            overlayWm = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = LayoutInflater.from(this).inflate(R.layout.floatview, null);

            overlayLogText = overlayView.findViewById(R.id.logText);
            overlayStatusText = overlayView.findViewById(R.id.text);

            // 重新初始化文本
            if (cachedOverlayLog != null && overlayLogText != null) {
                overlayLogText.setText(cachedOverlayLog);
            }
            if (overlayStatusText != null) {
                overlayStatusText.setText(cachedOverlayStatus);
            }

            int layoutFlag;
            if (Build.VERSION.SDK_INT >= 26) {
                layoutFlag = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                layoutFlag = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;

            overlayWm.addView(overlayView, params);
            Logger.i("无障碍悬浮窗已显示（不可被杀）");
        } catch (Exception e) {
            Logger.e("显示无障碍悬浮窗失败: " + e.getMessage());
        }
    }

    /** 更新悬浮窗日志 */
    public void appendOverlayLog(String line) {
        if (overlayLogText == null && overlayView == null) return;
        try {
            String current = cachedOverlayLog;
            if (current.equals("等待启动...")) current = "";
            String[] lines = current.split("\n");
            int start = Math.max(0, lines.length - 9);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(java.text.SimpleDateFormat.getTimeInstance(java.text.SimpleDateFormat.SHORT)
                    .format(new java.util.Date())).append(" ").append(line);
            cachedOverlayLog = sb.toString();
            if (overlayLogText != null) {
                overlayLogText.setText(cachedOverlayLog);
            }
        } catch (Exception ignored) {}
    }

    /** 更新悬浮窗状态文本 */
    public void updateOverlayStatus(String text) {
        cachedOverlayStatus = text;
        if (overlayStatusText != null) {
            overlayStatusText.setText(text);
        }
    }

    /** 获取实例 */
    public static MyAccessbilityService getInstance() {
        return sInstance;
    }
}
