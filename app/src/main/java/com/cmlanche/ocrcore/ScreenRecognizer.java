package com.cmlanche.ocrcore;

import android.graphics.Bitmap;

import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 屏幕统一识别器
 */
public class ScreenRecognizer {

    /**
     * 保存参考图到设备
     */
    public static void saveReferenceImage(Bitmap region, String name) {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(),
                "jixieshou_templates");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            region.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Logger.i("参考图已保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            Logger.e("保存参考图失败: " + e.getMessage());
        }
    }

    /**
     * 综合查找：通过 OCR 查找文字并点击
     */
    public static boolean findAnyAndClick(String ocrPattern, Bitmap template, double threshold) {
        if (ocrPattern != null) {
            OcrResult ocr = FindByTextOcr.find(ocrPattern);
            if (ocr != null) {
                ActionUtils.click(ocr.getCenterX(), ocr.getCenterY());
                return true;
            }
        }
        return false;
    }
}
