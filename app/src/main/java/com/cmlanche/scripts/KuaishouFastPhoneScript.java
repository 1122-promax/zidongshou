package com.cmlanche.scripts;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.model.AppInfo;
import com.cmlanche.ocrcore.OcrEngine;
import com.cmlanche.ocrcore.OcrResult;
import com.cmlanche.ocrcore.PhoneVideoAnalyzer;
import com.cmlanche.ocrcore.ScreencapUtil;

import java.util.List;

/**
 * 快手极速版 - 手机模式
 */
public class KuaishouFastPhoneScript extends BaseScript {

    public KuaishouFastPhoneScript(AppInfo appInfo) { super(appInfo); }

    @Override
    protected void executeScript() {
        try {
            if (!isTargetPkg()) {
                Logger.i("快手极速版：不在快手页面，等待中...");
                Utils.sleep(2000);
                return;
            }

            if (!com.cmlanche.common.SPService.getBoolean(com.cmlanche.common.SPService.DETECT_ENABLED, true)) {
                Logger.i("识别点赞已关闭，纯刷视频模式");
                swipeToNext();
                Utils.sleep(2000);
                return;
            }

            if (!isLoggedIn) {
                isLoggedIn = true;
                Logger.i("快手极速版[手机]：默认已登录");
            }

            totalVideos++;
            if (totalVideos % 10 == 0) {
                Logger.i("定期重置 OCR 引擎（第" + totalVideos + "轮）");
                OcrEngine.getInstance().reset();
                Utils.sleep(500);
            }

        Logger.i("═══════════ 第" + totalVideos + "轮 ═══════════");
        Logger.i("快手极速版[手机]：开始检测");

        int screenW = MyApplication.getAppInstance().getScreenWidth();
        int screenH = MyApplication.getAppInstance().getScreenHeight();

        boolean directHit = false;
        String hitText = "";
        boolean hasExpandText = false;
        OcrResult expandBtn = null;

        // 先等待页面渲染稳定
        Utils.sleep(600);
        for (int scan = 0; scan < 3 && !directHit && !hasExpandText; scan++) {
            if (scan > 0) Utils.sleep(600);

            sendAppStatus("检测中...");
            Bitmap screenshot = ScreencapUtil.captureScreen();
            if (screenshot == null) {
                if (scan == 2) {
                    Logger.e("截图连续失败3次，直接滑动");
                    swipeToNext();
                    Utils.sleep(2000);
                    return;
                }
                continue;
            }
            Logger.i("截图成功(" + screenshot.getWidth() + "x" + screenshot.getHeight() + ")");

            long ocrStart = System.currentTimeMillis();
            Bitmap grayBitmap = toGrayscale(screenshot);
            List<OcrResult> results = OcrEngine.getInstance().recognizeRegion(
                    grayBitmap, new Rect(0, screenH * 65 / 100, screenW, screenH));
            grayBitmap.recycle();
            long ocrCost = System.currentTimeMillis() - ocrStart;
            Logger.i("第" + (scan+1) + "次OCR(" + ocrCost + "ms): 识别到" + results.size() + "个文本块");

            for (OcrResult r : results) {
                String txt = r.getText();
                if (PhoneVideoAnalyzer.isBeautyKeyword(txt)) {
                    directHit = true;
                    hitText = txt;
                    Logger.i("→ 命中关键词: [" + txt + "] 区域=" + r.getRect().toShortString());
                    break;
                }
                if (!hasExpandText && PhoneVideoAnalyzer.isExpandText(txt)) {
                    hasExpandText = true;
                    expandBtn = r;
                    Logger.i("→ 找到展开文字: [" + txt + "] 区域=" + r.getRect().toShortString());
                }
            }

            if (!directHit && !hasExpandText) {
                StringBuilder sb = new StringBuilder();
                for (OcrResult r : results) sb.append("[").append(r.getText()).append("]");
                Logger.i("→ 未命中关键词和展开，文本: " + (sb.length()>200 ? sb.substring(0,200)+"..." : sb.toString()));
            }
            screenshot.recycle();
        }

        if (directHit) {
            beautyVideos++;
            Logger.i("✅ 直接命中关键词: " + hitText);
            sendAppStatus("✅ 检测到: " + hitText + "，等待1秒后点赞...");
            Utils.sleep(1000);
            clickLikeButton();
            Logger.i("点赞完成，停留0.5秒后滑动");
            Utils.sleep(500);
            swipeToNext();
            Utils.sleep(2000);
            return;
        }

        if (!hasExpandText) {
            Logger.i("❌ 未找到展开文字，直接划走");
            sendAppStatus("未找到展开文字，下一个");
            swipeToNext();
            Utils.sleep(2000);
            return;
        }

        int expandClickX = expandBtn.getRect().right - 5;
        int expandClickY = expandBtn.getCenterY();
        Logger.i("✅ 找到展开文字，点击最右边 @(" + expandClickX + "," + expandClickY + ") 原中心=" + expandBtn.getCenterX());
        sendAppStatus("点击展开...");
        ActionUtils.click(expandClickX, expandClickY);
        Utils.sleep(2500);

        Logger.i("手机模式：执行无障碍后向滚动...");
        try {
            android.accessibilityservice.AccessibilityService as = MyApplication.getAppInstance().getAccessbilityService();
            if (as != null) {
                android.view.accessibility.AccessibilityNodeInfo root = as.getRootInActiveWindow();
                if (root != null) {
                    PhoneVideoAnalyzer.scrollBackward(root);
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Logger.e("无障碍滚动失败: " + e.getMessage());
        }
        Utils.sleep(1000);

        sendAppStatus("展开后识别中...");
        boolean found = false;
        String foundText = "";
        for (int retry = 0; retry < 3 && !found; retry++) {
            if (retry > 0) {
                Logger.i("展开后第" + (retry + 1) + "次重试OCR...");
                Utils.sleep(400);
            }
            Bitmap afterShot = ScreencapUtil.captureScreen();
            if (afterShot == null) continue;
            try {
                Bitmap afterGray = toGrayscale(afterShot);
                List<OcrResult> afterResults = OcrEngine.getInstance().recognizeRegion(
                        afterGray, new Rect(0, screenH * 30 / 100, screenW, screenH * 50 / 100));
                afterGray.recycle();
                Logger.i("展开后识别到 " + afterResults.size() + " 个文本块");

                StringBuilder afterTexts = new StringBuilder();
                for (OcrResult r : afterResults) {
                    if (afterTexts.length() > 0) afterTexts.append(" | ");
                    afterTexts.append("[").append(r.getText()).append("]");
                }
                Logger.i("展开后内容: " + (afterTexts.length() > 300 ? afterTexts.substring(0, 300) + "..." : afterTexts.toString()));

                for (OcrResult r : afterResults) {
                    if (PhoneVideoAnalyzer.isBeautyKeyword(r.getText())) {
                        found = true;
                        foundText = r.getText();
                        Logger.i("✅ 展开后检测到目标！文本: " + foundText);
                        break;
                    }
                }
            } finally {
                afterShot.recycle();
            }
        }

        sendAppStatus("退出评论区...");
        int closeX = PhoneVideoAnalyzer.getCustomCoord("custom_close_x", 319);
        int closeY = PhoneVideoAnalyzer.getCustomCoord("custom_close_y", 352);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);

        if (found) {
            beautyVideos++;
            Logger.i("🎯 第 " + totalVideos + " 个是目标视频！（累计: " + beautyVideos + "/" + totalVideos + "）");
            sendAppStatus("✅ 检测到: " + foundText + "，等待2秒后点赞...");
            Utils.sleep(1500);
            clickLikeButton();
            Logger.i("点赞完成");
            sendAppStatus("点赞完成，停留观看...");
            Utils.sleep(1000);
        } else {
            Logger.i("❌ 展开后仍未检测到目标");
        }

        Logger.i("滑动到下一个视频");
        sendAppStatus("滑动下一个...");
        swipeToNext();
        Utils.sleep(2000);
        sendAppStatus("已滑动，等待加载...");
        Logger.i("════════ 第" + totalVideos + "轮正常结束 ════════");
        } catch (Exception e) {
            Logger.e("executeScript异常: " + e.getMessage(), e);
            sendAppStatus("脚本异常: " + e.getClass().getSimpleName());
            Logger.i("════════ 第" + totalVideos + "轮异常退出 ════════");
        } catch (Throwable t) {
            Logger.e("executeScript严重错误: " + t.getMessage(), new Exception(t));
            sendAppStatus("脚本严重错误");
            Logger.i("════════ 第" + totalVideos + "轮严重错误 ════════");
        }
    }

    @Override
    public boolean isDestinationPage() {
        return isTargetPkg();
    }
}
