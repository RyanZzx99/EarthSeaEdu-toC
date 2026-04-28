package com.earthseaedu.backend.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AlevelAssetMapper;
import com.earthseaedu.backend.mapper.AlevelPdfPageMapper;
import com.earthseaedu.backend.model.alevel.AlevelAsset;
import com.earthseaedu.backend.model.alevel.AlevelPdfPage;
import com.earthseaedu.backend.model.alevel.AlevelSourceFile;
import com.earthseaedu.backend.service.AlevelPdfPageRenderService;
import com.earthseaedu.backend.support.AlevelPdfTextNormalizationSupport;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * A-Level PDF 页面渲染服务实现，生成整页兜底图并记录页面文本。
 */
@Service
public class AlevelPdfPageRenderServiceImpl implements AlevelPdfPageRenderService {

    private static final int RENDER_DPI = 144;

    private final AlevelPdfPageMapper alevelPdfPageMapper;
    private final AlevelAssetMapper alevelAssetMapper;
    private final StoragePathSupport storagePathSupport;

    public AlevelPdfPageRenderServiceImpl(
        AlevelPdfPageMapper alevelPdfPageMapper,
        AlevelAssetMapper alevelAssetMapper,
        StoragePathSupport storagePathSupport
    ) {
        this.alevelPdfPageMapper = alevelPdfPageMapper;
        this.alevelAssetMapper = alevelAssetMapper;
        this.storagePathSupport = storagePathSupport;
    }

    @Override
    public RenderResult renderSourceFilePages(AlevelSourceFile sourceFile, byte[] rawBytes, String logicalPath) {
        if (sourceFile == null || sourceFile.getAlevelSourceFileId() == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "渲染 PDF 页面失败：source file 尚未入库");
        }

        LocalDateTime now = LocalDateTime.now();
        long sourceFileId = sourceFile.getAlevelSourceFileId();
        alevelPdfPageMapper.deactivateBySourceFileId(sourceFileId, now);
        alevelAssetMapper.deactivateByOwner("SOURCE_FILE", sourceFileId, now);

        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            int renderedCount = 0;
            int failedCount = 0;

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                int pageNo = pageIndex + 1;
                try {
                    renderedCount += renderSinglePage(sourceFile, logicalPath, document, renderer, pageIndex, pageNo);
                } catch (Exception exception) {
                    failedCount++;
                    insertFailedPage(sourceFile, document, pageIndex, pageNo, exception);
                }
            }
            return new RenderResult(pageCount, renderedCount, failedCount);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "渲染 PDF 页面失败：" + sourceFile.getSourceFileName());
        }
    }

    private int renderSinglePage(
        AlevelSourceFile sourceFile,
        String logicalPath,
        PDDocument document,
        PDFRenderer renderer,
        int pageIndex,
        int pageNo
    ) throws IOException {
        String rawText = extractPageText(document, pageNo);
        String normalizedText = AlevelPdfTextNormalizationSupport.normalizeTextBlock(rawText);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
        byte[] imageBytes = encodePng(image);
        String imageHash = DigestUtil.sha256Hex(imageBytes);
        String storagePath = buildPageImageStoragePath(sourceFile, pageNo, imageHash);
        String assetUrl = writePageImage(storagePath, imageBytes);

        AlevelAsset asset = new AlevelAsset();
        asset.setOwnerType("SOURCE_FILE");
        asset.setOwnerId(sourceFile.getAlevelSourceFileId());
        asset.setAssetType("IMAGE");
        asset.setAssetRole("PAGE_RENDER");
        asset.setAssetName(buildPageAssetName(sourceFile, pageNo));
        asset.setSourcePath(buildSourcePath(logicalPath, pageNo));
        asset.setStoragePath(storagePath);
        asset.setAssetUrl(assetUrl);
        asset.setFileHash(imageHash);
        asset.setMimeType("image/png");
        asset.setSourcePageNo(pageNo);
        asset.setSourceBboxJson(null);
        asset.setSortOrder(pageNo);
        asset.setStatus(1);
        asset.setRemark("source_file_id=" + sourceFile.getAlevelSourceFileId());
        asset.setCreateTime(LocalDateTime.now());
        asset.setUpdateTime(LocalDateTime.now());
        asset.setDeleteFlag("1");
        alevelAssetMapper.insert(asset);

        PDPage page = document.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        AlevelPdfPage pdfPage = buildBasePdfPage(sourceFile, page, mediaBox, pageNo);
        pdfPage.setTextContent(rawText);
        pdfPage.setTextNormalized(normalizedText);
        pdfPage.setRenderAssetId(asset.getAlevelAssetId());
        pdfPage.setRenderStoragePath(storagePath);
        pdfPage.setRenderAssetUrl(assetUrl);
        pdfPage.setThumbnailAssetUrl(null);
        pdfPage.setImageWidthPx(image.getWidth());
        pdfPage.setImageHeightPx(image.getHeight());
        pdfPage.setRenderDpi(RENDER_DPI);
        pdfPage.setContentHash(DigestUtil.sha256Hex(CharSequenceUtil.nullToDefault(normalizedText, "")));
        pdfPage.setExtractionJson(buildExtractionJson(sourceFile, logicalPath, pageNo, image.getWidth(), image.getHeight()));
        pdfPage.setRenderStatus("RENDERED");
        pdfPage.setErrorMessage(null);
        alevelPdfPageMapper.insert(pdfPage);
        return 1;
    }

    private void insertFailedPage(
        AlevelSourceFile sourceFile,
        PDDocument document,
        int pageIndex,
        int pageNo,
        Exception exception
    ) {
        PDPage page = document.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        AlevelPdfPage pdfPage = buildBasePdfPage(sourceFile, page, mediaBox, pageNo);
        pdfPage.setTextContent(null);
        pdfPage.setTextNormalized(null);
        pdfPage.setRenderAssetId(null);
        pdfPage.setRenderStoragePath(null);
        pdfPage.setRenderAssetUrl(null);
        pdfPage.setThumbnailAssetUrl(null);
        pdfPage.setImageWidthPx(null);
        pdfPage.setImageHeightPx(null);
        pdfPage.setRenderDpi(RENDER_DPI);
        pdfPage.setContentHash(null);
        pdfPage.setExtractionJson(null);
        pdfPage.setRenderStatus("FAILED");
        pdfPage.setErrorMessage(exception.getMessage());
        alevelPdfPageMapper.insert(pdfPage);
    }

    private AlevelPdfPage buildBasePdfPage(AlevelSourceFile sourceFile, PDPage page, PDRectangle mediaBox, int pageNo) {
        AlevelPdfPage pdfPage = new AlevelPdfPage();
        pdfPage.setAlevelSourceFileId(sourceFile.getAlevelSourceFileId());
        pdfPage.setAlevelPaperId(sourceFile.getAlevelPaperId());
        pdfPage.setPageNo(pageNo);
        pdfPage.setPageLabel(String.valueOf(pageNo));
        pdfPage.setPageWidthPt(BigDecimal.valueOf(mediaBox.getWidth()));
        pdfPage.setPageHeightPt(BigDecimal.valueOf(mediaBox.getHeight()));
        pdfPage.setRotation(page.getRotation());
        pdfPage.setStatus(1);
        pdfPage.setRemark(null);
        pdfPage.setCreateTime(LocalDateTime.now());
        pdfPage.setUpdateTime(LocalDateTime.now());
        pdfPage.setDeleteFlag("1");
        return pdfPage;
    }

    private String extractPageText(PDDocument document, int pageNo) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        return stripper.getText(document);
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", outputStream)) {
            throw new IOException("当前运行环境不支持 PNG 编码");
        }
        return outputStream.toByteArray();
    }

    private String writePageImage(String storagePath, byte[] imageBytes) throws IOException {
        Path targetPath = storagePathSupport.ensureExamAssetRoot().resolve(storagePath);
        FileUtil.mkdir(targetPath.getParent().toFile());
        Files.write(targetPath, imageBytes);
        return "/exam-assets/" + storagePath.replace("\\", "/");
    }

    private String buildPageImageStoragePath(AlevelSourceFile sourceFile, int pageNo, String imageHash) {
        String safeBundleCode = shorten(normalizePathPart(sourceFile.getBundleCode()), 120);
        String safeFileStem = shorten(normalizePathPart(stripExtension(sourceFile.getSourceFileName())), 80);
        return normalizeImportPath(
            "alevel/rendered-pages/"
                + safeBundleCode
                + "/source-"
                + sourceFile.getAlevelSourceFileId()
                + "/"
                + safeFileStem
                + "_page-"
                + "%04d".formatted(pageNo)
                + "_"
                + imageHash.substring(0, 12)
                + ".png"
        );
    }

    private String buildPageAssetName(AlevelSourceFile sourceFile, int pageNo) {
        return sourceFile.getSourceFileName() + " page " + pageNo;
    }

    private String buildSourcePath(String logicalPath, int pageNo) {
        String path = CharSequenceUtil.blankToDefault(logicalPath, "");
        return path + "#page=" + pageNo;
    }

    private String buildExtractionJson(
        AlevelSourceFile sourceFile,
        String logicalPath,
        int pageNo,
        int imageWidth,
        int imageHeight
    ) {
        JSONObject object = new JSONObject();
        object.set("source_file_id", sourceFile.getAlevelSourceFileId());
        object.set("logical_path", logicalPath);
        object.set("page_no", pageNo);
        object.set("render_dpi", RENDER_DPI);
        object.set("image_width_px", imageWidth);
        object.set("image_height_px", imageHeight);
        object.set("extraction_version", 1);
        return object.toString();
    }

    private String normalizeImportPath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\\", "/").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.replaceAll("/+", "/");
    }

    private String normalizePathPart(String value) {
        String normalized = CharSequenceUtil.nullToDefault(value, "").toLowerCase(Locale.ROOT);
        normalized = normalized.replace("\\", "/");
        normalized = normalized.replaceAll("[^a-z0-9/]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("(^-|-$)", "");
        return CharSequenceUtil.blankToDefault(normalized, "unknown");
    }

    private String stripExtension(String value) {
        String text = CharSequenceUtil.nullToDefault(value, "");
        int index = text.lastIndexOf('.');
        return index < 0 ? text : text.substring(0, index);
    }

    private String shorten(String value, int maxLength) {
        String text = CharSequenceUtil.nullToDefault(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 9)) + "_" + DigestUtil.md5Hex(text).substring(0, 8);
    }
}
