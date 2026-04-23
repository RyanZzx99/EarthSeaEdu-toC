package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * A-Level 题库构建服务，负责把已导入的 source file 解析为试卷、题目、答案和 mockexam 引用。
 */
public interface AlevelQuestionBankBuildService {

    /**
     * 解析指定 bundle 并写入正式题库。
     *
     * @param bundleCode source file 分组编码
     * @return 本次构建结果摘要
     */
    BuildResult buildBundle(String bundleCode);

    /**
     * A-Level 题库构建结果摘要。
     *
     * @param bundleCode source file 分组编码
     * @param skipped 是否跳过
     * @param alevelPaperId 生成或复用的 A-Level 试卷 ID
     * @param paperCode 试卷编码
     * @param questionCount 写入题目数
     * @param answerCount 写入答案数
     * @param insertAssetCount 写入 insert 资源数
     * @param questionRefCount 写入题目引用数
     * @param payload mockexam/管理台可直接展示的结果摘要
     */
    record BuildResult(
        String bundleCode,
        boolean skipped,
        Long alevelPaperId,
        String paperCode,
        int questionCount,
        int answerCount,
        int insertAssetCount,
        int questionRefCount,
        Map<String, Object> payload
    ) {
    }
}
