package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * A-Level 模拟考试业务入口，隔离 A-Level 专属查询接口契约。
 */
public interface MockExamAlevelService {

    /**
     * 读取 A-Level 练习可用筛选项。
     *
     * @return A-Level 考试类型和科目选项。
     */
    Map<String, Object> getOptions();

    /**
     * 查询 A-Level 可用试卷列表。
     *
     * @param examContent A-Level 科目，可为空。
     * @return A-Level 试卷列表。
     */
    Map<String, Object> listPapers(String examContent);

    /**
     * 读取 A-Level 单张试卷详情和答题载荷。
     *
     * @param examPaperId 前端使用的 A-Level 试卷 ID。
     * @return A-Level 试卷详情。
     */
    Map<String, Object> getPaper(long examPaperId);
}
