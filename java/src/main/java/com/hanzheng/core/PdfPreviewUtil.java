package com.hanzheng.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/**
 * PDF 预览图渲染工具（与 Python 版 PyMuPDF 渲染首页为 PNG 对齐）
 *
 * 用于：
 *  - /preview          单文件预览（150 DPI ≈ 2.08x zoom）
 *  - /config/preview   覆盖区域校准预览（150 DPI）
 *  - /batch/upload     批量文件缩略图（72 DPI）
 */
public class PdfPreviewUtil {

    /** 渲染缩放系数（150 DPI ≈ 2.08） */
    public static final float ZOOM_PREVIEW = 2.08f;
    /** 缩略图缩放系数（72 DPI = 1.0） */
    public static final float ZOOM_THUMB = 1.0f;

    /**
     * 渲染 PDF 首页为 PNG 字节流
     *
     * @param pdfData     PDF 字节数组
     * @param zoom        渲染缩放系数（1.0 = 72 DPI，2.08 = 150 DPI）
     * @return PNG 字节数组；失败返回 null
     */
    public static byte[] renderFirstPagePng(byte[] pdfData, float zoom) {
        try (PDDocument doc = PDDocument.load(pdfData)) {
            if (doc.getNumberOfPages() == 0) return null;
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(0, (int)(72 * zoom), ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("[PdfPreviewUtil] renderFirstPagePng error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从 PDF 字节数组获取首页尺寸（pt）
     *
     * @param pdfData PDF 字节数组
     * @return [pageWidth, pageHeight]（pt）；失败返回 null
     */
    public static float[] getFirstPageSize(byte[] pdfData) {
        try (PDDocument doc = PDDocument.load(pdfData)) {
            if (doc.getNumberOfPages() == 0) return null;
            PDPage page = doc.getPage(0);
            PDRectangle box = page.getMediaBox();
            return new float[]{box.getWidth(), box.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 渲染 PDF 首页为 PNG 并保存到指定目录（如 temp/），返回文件名
     *
     * @param pdfData    PDF 字节数组
     * @param zoom       渲染缩放系数
     * @param tempDir    临时目录
     * @param baseName   文件名前缀（不含扩展名）
     * @return 生成的文件名（如 preview_xxx.png）；失败返回 null
     */
    public static String renderAndSave(byte[] pdfData, float zoom, File tempDir, String baseName) {
        try {
            byte[] png = renderFirstPagePng(pdfData, zoom);
            if (png == null) return null;
            if (!tempDir.exists()) tempDir.mkdirs();
            File out = new File(tempDir, baseName + ".png");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(png);
            }
            return baseName + ".png";
        } catch (Exception e) {
            return null;
        }
    }
}
