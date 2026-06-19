package com.cmlanche.scripts;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.DisplayMetrics;

import com.cmlanche.application.MyApplication;
import com.cmlanche.common.PackageUtils;
import com.cmlanche.core.executor.builder.SwipStepBuilder;
import com.cmlanche.core.search.FindById;
import com.cmlanche.core.search.FindByText;
import com.cmlanche.core.search.node.NodeInfo;
import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.model.AppInfo;
import com.cmlanche.core.bus.BusEvent;
import com.cmlanche.core.bus.BusManager;
import com.cmlanche.ocrcore.FindByTextOcr;
import com.cmlanche.ocrcore.OcrResult;
import com.cmlanche.ocrcore.PhoneVideoAnalyzer;
import com.cmlanche.ocrcore.VideoAnalyzer;

import static com.cmlanche.core.bus.EventType.detect_status;

public abstract class BaseScript implements IScript {

    private AppInfo appInfo;
    private long startTime;
    protected int totalVideos = 0;
    protected int beautyVideos = 0;
    protected boolean isLoggedIn = false;
    protected boolean isCheckingLogin = false;

    public BaseScript(AppInfo appInfo) {
        this.appInfo = appInfo;
    }

    protected long getTimeout() {
        return appInfo.getPeriod() * 60 * 60 * 1000;
    }

    @Override
    public void execute() {
        Logger.i("脚本已就绪，请手动打开目标APP...");
        sendAppStatus("已启动 " + getAppInfo().getName() + "，OCR检测中...");
        resetStartTime();

        // 等待用户进入目标应用
        int waitCount = 0;
        while (!isTargetPkg()) {
            if (isPause()) return;
            waitCount++;
            if (waitCount % 5 == 0) {
                sendAppStatus("等待进入 " + getAppInfo().getName() + "..." + (waitCount * 2) + "秒");
                if (waitCount >= 30) {
                    sendAppStatus("等待超时，任务结束");
                    Logger.i("等待超时，自动停止任务");
                    stopTask();
                    return;
                }
            }
            Utils.sleep(2000);
        }
        sendAppStatus("已进入 " + getAppInfo().getName());

        // 总时间
        while ((System.currentTimeMillis() - startTime < getTimeout())) {
            try {
                if (isPause()) {
                    Utils.sleep(2000);
                    continue;
                }
                // 每次循环检查包名，不在目标页面就等待
                if (!isTargetPkg()) {
                    Logger.i("不在目标页面，等待中（切回目标APP后将自动继续）...");
                    Utils.sleep(2000);
                    continue;
                }
                executeScript();
            } catch (Exception e) {
                Logger.e("执行异常，脚本: " + appInfo.getName(), e);
            } finally {
                int t = getRandomSleepTime(getMinSleepTime(), getMaxSleepTime());
                Logger.i("休眠：" + t);
                Utils.sleep(t);
            }
        }
    }

    /** 停止任务 */
    private void stopTask() {
        TaskExecutor.getInstance().stop(true);
    }

    private boolean isPause() {
        return TaskExecutor.getInstance().isForcePause() ||
                TaskExecutor.getInstance().isPause();
    }

    @Override
    public AppInfo getAppInfo() {
        return appInfo;
    }

    @Override
    public void startApp() {
        PackageUtils.startApp(getAppInfo().getPkgName());
    }

    @Override
    public void resetStartTime() {
        this.startTime = System.currentTimeMillis();
    }

    private int getRandomSleepTime(int min, int max) {
        if (min >= max) return min;
        return min + (int)(Math.random() * (max - min + 1));
    }

    // ========== 无障碍节点查找 ==========

    protected NodeInfo findById(String id) {
        return FindById.find(id);
    }

    protected NodeInfo findByText(String text) {
        return FindByText.find(text);
    }

    protected NodeInfo findByTextContains(String textContains) {
        return FindByText.findByTextContains(textContains);
    }

    // ========== OCR 文本查找 ==========

    protected OcrResult findTextByOcr(String pattern) {
        return FindByTextOcr.find(pattern);
    }

    protected OcrResult findTextByOcrContains(String text) {
        return FindByTextOcr.findByContains(text);
    }

    protected boolean findTextAndClick(String pattern) {
        return FindByTextOcr.findAndClick(pattern);
    }

    protected boolean findTextMixedAndClick(String pattern) {
        NodeInfo node = findByText(pattern);
        if (node != null) {
            Logger.i("混合查找：无障碍节点命中 [" + pattern + "]");
            ActionUtils.click(node);
            return true;
        }
        OcrResult ocrResult = FindByTextOcr.find(pattern);
        if (ocrResult != null) {
            Logger.i("混合查找：OCR 命中 [" + pattern + "]");
            ActionUtils.click(ocrResult.getCenterX(), ocrResult.getCenterY());
            return true;
        }
        return false;
    }

    protected boolean findTextContainsMixedAndClick(String text) {
        NodeInfo node = findByTextContains(text);
        if (node != null) {
            ActionUtils.click(node);
            return true;
        }
        OcrResult result = FindByTextOcr.findByContains(text);
        if (result != null) {
            ActionUtils.click(result.getCenterX(), result.getCenterY());
            return true;
        }
        return false;
    }

    // ========== 视频内容分析 ==========

    protected boolean isBeautyVideo() {
        String mode = com.cmlanche.common.SPService.getString(com.cmlanche.common.SPService.DEVICE_MODE, "phone");
        if ("phone".equals(mode)) {
            return PhoneVideoAnalyzer.isCurrentVideoBeauty();
        } else {
            return VideoAnalyzer.isCurrentVideoBeauty();
        }
    }

    protected String getVideoDescription() {
        return VideoAnalyzer.getFullDescription();
    }

    protected boolean hasExpandButton() {
        OcrResult r = FindByTextOcr.findByContains("展开");
        return r != null;
    }

    protected void expandDescription() {
        OcrResult r = FindByTextOcr.findByContains("展开");
        if (r != null) {
            int clickX = r.getRect().right - 5;
            ActionUtils.click(clickX, r.getCenterY());
            Utils.sleep(300);
        }
    }

    // ========== 通用动作 ==========

    protected void clickLikeButton() {
        int likeX = PhoneVideoAnalyzer.getCustomCoord("custom_like_x", 540);
        int likeY = PhoneVideoAnalyzer.getCustomCoord("custom_like_y", 960);
        Logger.i("双击点赞 @(" + likeX + "," + likeY + ")");
        doubleTap(likeX, likeY);
    }

    /** 真正的双击手势：一次 dispatchGesture 内两个 Stroke */
    protected void doubleTap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return;
        android.accessibilityservice.AccessibilityService as =
                MyApplication.getAppInstance().getAccessbilityService();
        if (as == null) return;

        android.graphics.Path p1 = new android.graphics.Path();
        p1.moveTo(x, y);
        android.graphics.Path p2 = new android.graphics.Path();
        p2.moveTo(x, y);

        android.accessibilityservice.GestureDescription.Builder builder =
                new android.accessibilityservice.GestureDescription.Builder();
        builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p1, 0, 80));
        builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p2, 160, 80));

        as.dispatchGesture(builder.build(), null, null);
    }

    /** 无障碍按返回键，退出评论区等操作无需填坐标 */
    protected void pressBack() {
        android.accessibilityservice.AccessibilityService as =
                MyApplication.getAppInstance().getAccessbilityService();
        if (as != null) {
            as.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        }
    }

    /** 将Bitmap转为灰度图，提高OCR识别率 */
    protected Bitmap toGrayscale(Bitmap src) {
        try {
            Bitmap compatible = src.copy(Bitmap.Config.ARGB_8888, false);
            Bitmap gray = Bitmap.createBitmap(compatible.getWidth(), compatible.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(gray);
            android.graphics.Paint paint = new android.graphics.Paint();
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0);
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            canvas.drawBitmap(compatible, 0, 0, paint);
            if (compatible != src) compatible.recycle();
            return gray;
        } catch (Exception e) {
            Logger.e("灰度转换失败: " + e.getMessage());
            return src;
        }
    }

    /**
     * OCR 图片预处理（灰度+去噪，不含二值化）
     * 优先使用此方法，识别不到时降级到 toGrayscale
     */
    protected Bitmap preprocessOcr(Bitmap src) {
        try {
            Bitmap safe = src;
            if (src.getConfig() == android.graphics.Bitmap.Config.HARDWARE) {
                safe = src.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
            }
            int w = safe.getWidth(), h = safe.getHeight();
            int[] pixels = new int[w * h];
            safe.getPixels(pixels, 0, w, 0, 0, w, h);
            if (safe != src) safe.recycle();

            // 1. 亮度灰度化
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int gray = (r * 77 + g * 150 + b * 29) >> 8;
                pixels[i] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            }

            // 2. 3x3 中值滤波去噪
            int[] denoised = new int[pixels.length];
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int idx = y * w + x;
                    int[] vals = {
                        pixels[idx - w - 1] & 0xFF, pixels[idx - w] & 0xFF, pixels[idx - w + 1] & 0xFF,
                        pixels[idx - 1] & 0xFF, pixels[idx] & 0xFF, pixels[idx + 1] & 0xFF,
                        pixels[idx + w - 1] & 0xFF, pixels[idx + w] & 0xFF, pixels[idx + w + 1] & 0xFF
                    };
                    java.util.Arrays.sort(vals);
                    int med = vals[4];
                    denoised[idx] = 0xFF000000 | (med << 16) | (med << 8) | med;
                }
            }
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (x == 0 || y == 0 || x == w - 1 || y == h - 1)
                        denoised[y * w + x] = pixels[y * w + x];

            Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            result.setPixels(denoised, 0, w, 0, 0, w, h);
            return result;
        } catch (Exception e) {
            Logger.e("OCR预处理失败: " + e.getMessage());
            return toGrayscale(src);
        }
    }

    protected void swipeToNext() {
        try {
            DisplayMetrics dm = MyApplication.getAppInstance().getResources().getDisplayMetrics();
            int x = dm.widthPixels / 2 + (int)(Math.random() * 40) - 20;
            new SwipStepBuilder()
                    .setPoints(new Point(x, dm.heightPixels * 3 / 4), new Point(x, dm.heightPixels / 4))
                    .get().execute();
        } catch (Exception e) {
            Logger.i("滑动失败：" + e.getMessage());
        }
    }

    protected void handleLogin() {
        NodeInfo loginBtn = findByTextContains("登录");
        if (loginBtn != null) {
            Logger.i("检测到未登录，点击登录按钮");
            ActionUtils.click(loginBtn);
            isCheckingLogin = true;
            Utils.sleep(3000);
            return;
        }
        loginBtn = findById("login_text");
        if (loginBtn != null) {
            Logger.i("检测到未登录，点击登录入口");
            ActionUtils.click(loginBtn);
            isCheckingLogin = true;
            Utils.sleep(3000);
            return;
        }
        loginBtn = findByTextContains("头像");
        if (loginBtn != null) {
            Logger.i("检测到已登录状态");
            isLoggedIn = true;
            isCheckingLogin = false;
            return;
        }
        loginBtn = findById("user_avatar");
        if (loginBtn != null) {
            Logger.i("检测到已登录状态");
            isLoggedIn = true;
            isCheckingLogin = false;
            return;
        }
        Logger.i("未检测到登录相关元素，默认已登录");
        isLoggedIn = true;
        isCheckingLogin = false;
    }

    // ========== UI工具 ==========

    protected void runOnUiThread(Runnable runnable) {
        com.cmlanche.activity.MainActivity activity = MyApplication.getAppInstance().getMainActivity();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }

    protected boolean isTargetPkg() {
        com.cmlanche.core.service.MyAccessbilityService as = MyApplication.getAppInstance().getAccessbilityService();
        if (as == null || !as.isWrokFine()) {
            return false;
        }
        return as.containsPkg(getAppInfo().getPkgName());
    }

    protected void sendAppStatus(final String msg) {
        final com.cmlanche.activity.MainActivity ma =
                MyApplication.getAppInstance().getMainActivity();
        if (ma != null) {
            ma.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BusManager.getBus().post(new BusEvent<>(detect_status, msg));
                }
            });
        }
    }

    /** 默认休眠时间，子类可覆盖 */
    protected int getMinSleepTime() { return 100; }
    protected int getMaxSleepTime() { return 200; }

    /** 执行脚本 */
    protected abstract void executeScript();
}
