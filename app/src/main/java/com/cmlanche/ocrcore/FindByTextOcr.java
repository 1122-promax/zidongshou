package com.cmlanche.ocrcore;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OCR 的文本查找工具
 * 对标现有的 FindByText（基于 AccessibilityNodeInfo 查找）
 * 当节点树查找不到时，使用 OCR 作为兜底方案
 *
 * 使用方式（在脚本中）：
 *   OcrResult result = FindByTextOcr.find("我知道了");
 *   if (result != null) {
 *       ActionUtils.click(result.getCenterX(), result.getCenterY());
 *   }
 */
public class FindByTextOcr {

    private static final String TAG = "FindByTextOcr";

    /**
     * 在整个屏幕中查找指定文本，返回第一个匹配的 OCR 结果
     * 支持通配符 * 和正则 /pattern/（与 Utils.textMatch 一致）
     *
     * @param pattern 文本模式（支持 * 通配符和 /正�?/）
     * @return 匹配到的第一个 OcrResult，未找到返回 null
     */
    public static OcrResult find(String pattern) {
        long startTime = System.currentTimeMillis();

        // 1. 截图
        Bitmap screenshot = ScreencapUtil.captureScreen();
        if (screenshot == null) {
            Logger.e("截图失败，无法执行 OCR 查找");
            return null;
        }

        // 2. OCR 识别
        List<OcrResult> allResults = OcrEngine.getInstance().recognize(screenshot);
        if (allResults.isEmpty()) {
            Logger.i("OCR 未识别到任何文本");
            screenshot.recycle();
            return null;
        }

        // 3. 文本匹配
        for (OcrResult result : allResults) {
            if (Utils.textMatch(pattern, result.getText())) {
                long cost = System.currentTimeMillis() - startTime;
                Logger.i("OCR 找到文本 [" + pattern + "] -> "
                        + result.getText() + " @ " + result.getCenterX() + "," + result.getCenterY()
                        + " (耗时 " + cost + "ms)");
                screenshot.recycle();
                return result;
            }
        }

        Logger.i("OCR 未找到匹配文本 [" + pattern + "]，共识别 " + allResults.size() + " 个文本块");
        screenshot.recycle();
        return null;
    }

    /**
     * 查找包含指定文本的第一个结果
     */
    public static OcrResult findByContains(String text) {
        return find("*" + text + "*");
    }

    /**
     * 在指定屏幕区域内查找文本（更精准、更快）
     *
     * @param pattern 文本模式
     * @param region  要查找的屏幕区域
     */
    public static OcrResult findInRegion(String pattern, Rect region) {
        long startTime = System.currentTimeMillis();

        Bitmap screenshot = ScreencapUtil.captureScreen();
        if (screenshot == null) return null;

        List<OcrResult> allResults = OcrEngine.getInstance().recognizeRegion(screenshot, region);
        if (allResults.isEmpty()) {
            screenshot.recycle();
            return null;
        }

        for (OcrResult result : allResults) {
            if (Utils.textMatch(pattern, result.getText())) {
                long cost = System.currentTimeMillis() - startTime;
                Logger.i("区域OCR找到文本 [" + pattern + "] -> "
                        + result.getText() + " (耗时 " + cost + "ms)");
                screenshot.recycle();
                return result;
            }
        }

        screenshot.recycle();
        return null;
    }

    /**
     * 查找所有匹配的文本（用于需要选择最优结果的情况）
     */
    public static List<OcrResult> findAll(String pattern) {
        Bitmap screenshot = ScreencapUtil.captureScreen();
        if (screenshot == null) return new ArrayList<>();

        List<OcrResult> allResults = OcrEngine.getInstance().recognize(screenshot);
        List<OcrResult> matched = new ArrayList<>();

        for (OcrResult result : allResults) {
            if (Utils.textMatch(pattern, result.getText())) {
                matched.add(result);
            }
        }

        screenshot.recycle();
        return matched;
    }

    /**
     * 点击找到的文本（一步到位）
     *
     * @return true 如果找到并点击成功
     */
    public static boolean findAndClick(String pattern) {
        OcrResult result = find(pattern);
        if (result == null) return false;

        com.cmlanche.core.utils.ActionUtils.click(result.getCenterX(), result.getCenterY());
        return true;
    }
}
