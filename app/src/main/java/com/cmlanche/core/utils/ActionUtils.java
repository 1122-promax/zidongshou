package com.cmlanche.core.utils;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.search.node.NodeInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ActionUtils {

    // 点击间隔参数（毫秒）
    private static final long CLICK_START_DELAY = 0;   // 起始延迟（立即触发）
    private static final long CLICK_DURATION = 60;     // 点击持续时长（模拟正常点击）
    // 滑动参数
    private static final long SWIPE_START_DELAY = 50;
    private static final long SWIPE_DURATION = 800;    // 滑动持续时长（比原来更短）

    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static View clickDotView = null;

    /**
     * 点击某点（带特效红圈，方便查看点击位置）
     */
    public static boolean click(int x, int y) {
        showClickEffect(x, y);
        if (Build.VERSION.SDK_INT >= 24) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gestureDescription = builder
                    .addStroke(new GestureDescription.StrokeDescription(path, CLICK_START_DELAY, CLICK_DURATION))
                    .build();
            return MyApplication.getAppInstance().getAccessbilityService().dispatchGesture(gestureDescription,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                        }
                    }, null);
        }
        return false;
    }

    /**
     * 同步点击：等待手势真正完成才返回，适合双击等需要严格时序的场景
     */
    public static boolean clickSync(int x, int y) {
        showClickEffect(x, y);
        if (Build.VERSION.SDK_INT >= 24) {
            final CountDownLatch latch = new CountDownLatch(1);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gestureDescription = builder
                    .addStroke(new GestureDescription.StrokeDescription(path, CLICK_START_DELAY, CLICK_DURATION))
                    .build();
            boolean dispatched = MyApplication.getAppInstance().getAccessbilityService().dispatchGesture(gestureDescription,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            latch.countDown();
                        }
                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            latch.countDown();
                        }
                    }, null);
            if (dispatched) {
                try { latch.await(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            }
            return dispatched;
        }
        return false;
    }

    /**
     * 在点击位置显示一个红圈（400ms后自动消失）
     */
    private static void showClickEffect(int x, int y) {
        try {
            final android.content.Context ctx = MyApplication.getAppInstance().getApplicationContext();
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 移除之前残留的特效
                        if (clickDotView != null) {
                            try {
                                WindowManager wm = (WindowManager) ctx.getSystemService(ctx.WINDOW_SERVICE);
                                wm.removeView(clickDotView);
                            } catch (Exception ignored) {}
                            clickDotView = null;
                        }

                        // 创建红圈View
                        View dot = new View(ctx) {
                            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            private int radius = 40;
                            @Override
                            protected void onDraw(Canvas canvas) {
                                super.onDraw(canvas);
                                // 外圈 - 红色实线
                                paint.setColor(Color.RED);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(6);
                                canvas.drawCircle(getWidth()/2f, getHeight()/2f, radius, paint);
                                // 中心点 - 红色小圆
                                paint.setStyle(Paint.Style.FILL);
                                paint.setStrokeWidth(2);
                                canvas.drawCircle(getWidth()/2f, getHeight()/2f, 8, paint);
                                // 十字线
                                canvas.drawLine(getWidth()/2f - radius-10, getHeight()/2f, getWidth()/2f + radius+10, getHeight()/2f, paint);
                                canvas.drawLine(getWidth()/2f, getHeight()/2f - radius-10, getWidth()/2f, getHeight()/2f + radius+10, paint);
                            }
                        };

                        WindowManager wm = (WindowManager) ctx.getSystemService(ctx.WINDOW_SERVICE);
                        int size = 120;
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                size, size,
                                Build.VERSION.SDK_INT >= 26
                                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                        : WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                PixelFormat.TRANSLUCENT);
                        params.gravity = Gravity.TOP | Gravity.START;
                        params.x = x - size / 2;
                        params.y = y - size / 2;
                        wm.addView(dot, params);
                        clickDotView = dot;

                        // 400ms后自动移除
                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (clickDotView != null) {
                                        WindowManager wm = (WindowManager) ctx.getSystemService(ctx.WINDOW_SERVICE);
                                        wm.removeView(clickDotView);
                                        clickDotView = null;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }, 400);
                    } catch (Exception e) {
                        Logger.e("点击特效失败: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            // 特效失败不影响点击
        }
    }

    /**
     * 点击某个区域的中间位置
     */
    public static boolean click(NodeInfo rect) {
        return click(rect.getRect().centerX(), rect.getRect().centerY());
    }

    /**
     * 长按某点
     */
    public static boolean longClick(int x, int y, int duration) {
        if (Build.VERSION.SDK_INT >= 24) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            path.lineTo(x, y);
            GestureDescription gestureDescription = builder
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                    .build();
            return MyApplication.getAppInstance().getAccessbilityService().dispatchGesture(gestureDescription,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                        }
                    }, null);
        }
        return false;
    }

    /**
     * 从某点滑动到某点
     */
    public static boolean swipe(int fromX, int fromY, int toX, int toY, int steps) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                AccessibilityService service = MyApplication.getAppInstance().getAccessbilityService();
                if (service == null) return false;

                GestureDescription.Builder builder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(fromX, fromY);
                path.lineTo(toX, toY);
                GestureDescription gestureDescription = builder
                        .addStroke(new GestureDescription.StrokeDescription(path, SWIPE_START_DELAY, SWIPE_DURATION))
                        .build();

                return service.dispatchGesture(gestureDescription, null, null);
            }
            return true;
        } catch (Exception e) {
            Logger.i("手势滑动异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 按一次返回键
     */
    public static boolean pressBack() {
        return MyApplication.getAppInstance().getAccessbilityService()
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }
}
