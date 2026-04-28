package com.earthseaedu.backend.service;

import com.earthseaedu.backend.model.alevel.AlevelSourceFile;

/**
 * A-Level PDF 页面渲染服务，负责把原始 PDF 每页渲染成图片并写入页面索引。
 */
public interface AlevelPdfPageRenderService {

    /**
     * 渲染来源 PDF 的全部页面。
     *
     * @param sourceFile 已入库的来源 PDF
     * @param rawBytes PDF 原始字节
     * @param logicalPath 导入逻辑路径，用于记录来源
     * @return 渲染结果统计
     */
    RenderResult renderSourceFilePages(AlevelSourceFile sourceFile, byte[] rawBytes, String logicalPath);

    record RenderResult(int pageCount, int renderedCount, int failedCount) {
    }
}
