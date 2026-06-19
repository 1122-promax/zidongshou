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
 * 抖音极速版 - 手机模式
 */
public class DouyinFastPhoneScript extends BaseScript {

    public DouyinFastPhoneScript(AppInfo appInfo) { super(appInfo); }

    @Override
    protected void executeScript() {
        try {
            if (!isTargetPkg()) {
                Logger.i("抖音极速版[手机]：不在抖音页面，等待...");
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
                Logger.i("抖音极速版[手机]：默认已登录");
            }

            totalVideos++;
            if (totalVideos % 5 == 0) {
                Logger.i("定期重置 OCR 引擎（第" + totalVideos + "轮）");
                OcrEngine.getInstance().reset();
                Utils.sleep(500);
            }

        Logger.i("═══════════ 第" + totalVideos + "轮 ═══════════");
        Logger.i("抖音极速版[手机]：开始检测");

        int screenW = MyApplication.getAppInstance().getScreenWidth();
        int screenH = MyApplication.getAppInstance().getScreenHeight();

        boolean directHit = false;
        String hitText = "";
        boolean hasExpandText = false;
        OcrResult expandBtn = null;

        Utils.sleep(800);
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
            // 优先 preprocessOcr
            Bitmap grayBitmap = preprocessOcr(screenshot);
            List<OcrResult> results = OcrEngine.getInstance().recognizeRegion(
                    grayBitmap, new Rect(0, screenH * 50 / 100, screenW, screenH));
            grayBitmap.recycle();
            long ocrCost = System.currentTimeMillis() - ocrStart;
            Logger.i("第" + (scan+1) + "次OCR(" + ocrCost + "ms): preprocessOcr识别到" + results.size() + "个文本块");

            boolean hasHit = false;
            for (OcrResult r : results) {
                String txt = r.getText();
                if (PhoneVideoAnalyzer.isBeautyKeyword(txt)) {
                    directHit = true;
                    hitText = txt;
                    hasHit = true;
                    Logger.i("→ 命中关键词: [" + txt + "] 区域=" + r.getRect().toShortString());
                    break;
                }
                if (!hasExpandText && PhoneVideoAnalyzer.isExpandText(txt)) {
                    hasExpandText = true;
                    expandBtn = r;
                    hasHit = true;
                    Logger.i("→ 找到展开文字: [" + txt + "] 区域=" + r.getRect().toShortString());
                }
            }

            // 没识别到 → 降级 toGrayscale 重试
            if (!hasHit) {
                Logger.i("→ preprocessOcr未命中，降级toGrayscale重试...");
                grayBitmap = toGrayscale(screenshot);
                results = OcrEngine.getInstance().recognizeRegion(
                        grayBitmap, new Rect(0, screenH * 50 / 100, screenW, screenH));
                grayBitmap.recycle();
                Logger.i("  toGrayscale识别到" + results.size() + "个文本块");
                for (OcrResult r : results) {
                    String txt = r.getText();
                    if (!directHit && PhoneVideoAnalyzer.isBeautyKeyword(txt)) {
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
                // 优先 preprocessOcr
                Bitmap afterGray = preprocessOcr(afterShot);
                List<OcrResult> afterResults = OcrEngine.getInstance().recognizeRegion(
                        afterGray, new Rect(0, screenH * 45 / 100, screenW, screenH));
                afterGray.recycle();
                Logger.i("展开后preprocessOcr识别到 " + afterResults.size() + " 个文本块");

                boolean afterHit = false;
                for (OcrResult r : afterResults) {
                    if (PhoneVideoAnalyzer.isBeautyKeyword(r.getText())) {
                        found = true;
                        foundText = r.getText();
                        afterHit = true;
                        Logger.i("✅ 展开后检测到目标！文本: " + foundText);
                        break;
                    }
                }

                // 没识别到 → 降级 toGrayscale
                if (!afterHit) {
                    Logger.i("→ 展开后preprocessOcr未命中，降级toGrayscale...");
                    afterGray = toGrayscale(afterShot);
                    afterResults = OcrEngine.getInstance().recognizeRegion(
                            afterGray, new Rect(0, screenH * 45 / 100, screenW, screenH));
                    afterGray.recycle();
                    Logger.i("  展开后toGrayscale识别到 " + afterResults.size() + " 个文本块");
                    for (OcrResult r : afterResults) {
                        if (PhoneVideoAnalyzer.isBeautyKeyword(r.getText())) {
                            found = true;
                            foundText = r.getText();
                            Logger.i("✅ 展开后检测到目标！文本: " + foundText);
                            break;
                        }
                    }
                }
            } finally {
                afterShot.recycle();
            }
        }

        sendAppStatus("退出评论区...");
        pressBack();
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
        return isTargetPkg() && isLoggedIn;
    }
}
