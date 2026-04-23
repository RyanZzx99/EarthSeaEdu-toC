package com.earthseaedu.backend.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * A-Level 原始 PDF 导入服务，负责创建异步导入任务、保存原文件并写入 alevel_source_file。
 */
public interface AlevelSourceFileImportService {

    /**
     * 创建 A-Level PDF 原始文件导入任务。
     *
     * @param sourceMode 上传来源模式，支持 zip / directory / files
     * @param batchName 导入批次名，可为空
     * @param entryPathsJson 浏览器携带的相对路径数组 JSON
     * @param files 上传文件列表
     * @return 导入任务详情
     */
    Map<String, Object> createImportJob(
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<MultipartFile> files
    );

    /**
     * 查询指定 A-Level PDF 原始文件导入任务详情。
     *
     * @param jobId 任务 ID
     * @return 导入任务详情
     */
    Map<String, Object> getImportJobDetail(long jobId);
}
