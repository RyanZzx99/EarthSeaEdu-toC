package com.earthseaedu.backend.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题库导入服务，负责创建导入任务并查询任务明细。
 */
public interface QuestionBankImportService {

    /**
     * 创建题库导入任务，支持本地路径或上传文件作为来源。
     *
     * @param sourceMode 导入来源模式
     * @param bankName 题库名称
     * @param entryPathsJson 本地入口路径 JSON
     * @param files 上传文件列表
     * @return 处理后的响应对象。
     */
    Map<String, Object> createImportJob(
        String sourceMode,
        String bankName,
        String entryPathsJson,
        List<MultipartFile> files
    );

    /**
     * 读取指定题库导入任务的明细和处理结果。
     *
     * @param jobId 导入任务 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getImportJobDetail(long jobId);
}
