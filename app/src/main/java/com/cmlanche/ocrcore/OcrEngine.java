package com.cmlanche.ocrcore;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cmlanche.core.utils.Logger;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * OCR 引擎封装
 * 基于 Google ML Kit 中文文本识别
 * 返回每个识别到的文本块及其屏幕坐标
 */
public class OcrEngine {

    private static final String TAG = "OcrEngine";
    private static final long OCR_TIMEOUT_MS = 3000;
    private volatile TextRecognizer recognizer;

    private static volatile OcrEngine instance;

    private OcrEngine() {
        // 使用中文 OCR 识别器（支持中英文混合）
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }

    public static OcrEngine getInstance() {
        if (instance == null) {
            synchronized (OcrEngine.class) {
                if (instance == null) {
                    instance = new OcrEngine();
                }
            }
        }
        return instance;
    }

    /**
     * 重置 OCR 引擎（长时间运行后定期调用，防止识别率下降）
     * 关闭旧的 TextRecognizer 并创建新的，清理 ML Kit 内部累积状态
     */
    public synchronized void reset() {
        try {
            TextRecognizer old = recognizer;
            recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            Logger.i("OCR 引擎已重置");
            // 延迟关闭旧的，避免正在处理中的任务报错
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                try { old.close(); } catch (Exception ignored) {}
            }).start();
        } catch (Exception e) {
            Logger.e("OCR 引擎重置失败: " + e.getMessage());
        }
    }

    /**
     * 对 Bitmap 执行 OCR 识别，返回所有文本块及其坐标
     */
    public List<OcrResult> recognize(Bitmap bitmap) {
        if (bitmap == null) {
            Logger.e("OCR 输入 bitmap 为 null");
            return new ArrayList<>();
        }

        // 确保 bitmap 为软件位图（硬件位图会导致 IllegalArgumentException）
        Bitmap recycledCopy = null;
        if (bitmap.getConfig() == android.graphics.Bitmap.Config.HARDWARE) {
            recycledCopy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
            bitmap = recycledCopy;
        }

        final List<OcrResult> results = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    for (com.google.mlkit.vision.text.Text.TextBlock block : visionText.getTextBlocks()) {
                        for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                            String text = line.getText();
                            if (text == null || text.trim().isEmpty()) {
                                continue;
                            }

                            // 获取行级边界框
                            Rect boundingBox = line.getBoundingBox();
                            if (boundingBox == null) continue;

                            results.add(new OcrResult(
                                    text.trim(),
                                    boundingBox,
                                    line.getConfidence()
                            ));
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    Logger.e("OCR 识别失败: " + e.getMessage());
                    latch.countDown();
                });

        try {
            boolean completed = latch.await(OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!completed) {
                Logger.e("OCR 识别超时 (" + OCR_TIMEOUT_MS + "ms)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.e("OCR 等待被中断");
        }

        Logger.i("OCR 识别完成，共 " + results.size() + " 个文本块");
        // 回收硬件位图副本
        if (recycledCopy != null) recycledCopy.recycle();
        return results;
    }

    /**
     * 对指定的屏幕区域进行 OCR 识别（裁剪 bitmap 后再识别，更快更准）
     * 返回的坐标已自动转换为原始全屏坐标
     */
    public List<OcrResult> recognizeRegion(Bitmap fullScreen, Rect region) {
        if (region == null || region.isEmpty()) {
            return recognize(fullScreen);
        }
        // 安全裁剪
        int left = Math.max(0, region.left);
        int top = Math.max(0, region.top);
        int width = Math.min(region.width(), fullScreen.getWidth() - left);
        int height = Math.min(region.height(), fullScreen.getHeight() - top);

        if (width <= 0 || height <= 0) {
            return new ArrayList<>();
        }
        Bitmap cropped = Bitmap.createBitmap(fullScreen, left, top, width, height);
        List<OcrResult> cropResults = recognize(cropped);
        cropped.recycle();
        // 将裁剪图的坐标转回原始全屏坐标
        if (left != 0 || top != 0) {
            List<OcrResult> translated = new ArrayList<>(cropResults.size());
            for (OcrResult r : cropResults) {
                Rect origRect = new Rect(
                        r.getRect().left + left,
                        r.getRect().top + top,
                        r.getRect().right + left,
                        r.getRect().bottom + top
                );
                translated.add(new OcrResult(r.getText(), origRect, r.getConfidence()));
            }
            return translated;
        }
        return cropResults;
    }

    /**
     * 释放 OCR 引擎资源
     */
    public void close() {
        if (recognizer != null) {
            recognizer.close();
        }
        instance = null;
    }
}
