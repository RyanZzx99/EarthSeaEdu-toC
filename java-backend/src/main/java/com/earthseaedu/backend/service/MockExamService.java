package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.mockexam.MockExamRequests;
import java.util.Map;

/**
 * 模考业务服务。
 */
public interface MockExamService {

    /**
     * 读取模考模块可用的考试类别和内容选项。
     *
     * @return 处理后的响应对象。
     */
    Map<String, Object> getOptions();

    /**
     * 按考试类别和内容查询可用模考试卷。
     *
     * @param examCategory 考试类别
     * @param examContent 考试内容
     * @return 处理后的响应对象。
     */
    Map<String, Object> listPapers(String examCategory, String examContent);

    /**
     * 读取指定模考试卷的完整题目结构。
     *
     * @param examPaperId 模考试卷 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getPaper(long examPaperId);

    /**
     * 按考试类别和内容查询模考试卷集。
     *
     * @param examCategory 考试类别
     * @param examContent 考试内容
     * @return 处理后的响应对象。
     */
    Map<String, Object> listPaperSets(String examCategory, String examContent);

    /**
     * 读取指定模考试卷集及其试卷明细。
     *
     * @param paperSetId 模考试卷集 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getPaperSet(long paperSetId);

    /**
     * 提交单套模考试卷作答并生成批改结果。
     *
     * @param userId 用户 ID
     * @param examPaperId 模考试卷 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> submitPaper(String userId, long examPaperId, MockExamRequests.SubmitRequest request);

    /**
     * 提交模考试卷集作答并生成批改结果。
     *
     * @param userId 用户 ID
     * @param paperSetId 模考试卷集 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> submitPaperSet(String userId, long paperSetId, MockExamRequests.SubmitRequest request);

    /**
     * 查询用户模考提交记录。
     *
     * @param userId 用户 ID
     * @param examContent 考试内容
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    Map<String, Object> listSubmissions(String userId, String examContent, Integer limit);

    /**
     * 读取用户指定模考提交详情。
     *
     * @param userId 用户 ID
     * @param submissionId 提交记录 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getSubmission(String userId, long submissionId);

    /**
     * 查询用户未完成的模考进度记录。
     *
     * @param userId 用户 ID
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    Map<String, Object> listProgresses(String userId, Integer limit);

    /**
     * 读取用户指定模考进度详情。
     *
     * @param userId 用户 ID
     * @param progressId 进度记录 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getProgress(String userId, long progressId);

    /**
     * 保存用户单套模考试卷的作答进度。
     *
     * @param userId 用户 ID
     * @param examPaperId 模考试卷 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> savePaperProgress(String userId, long examPaperId, MockExamRequests.ProgressSaveRequest request);

    /**
     * 保存用户模考试卷集的作答进度。
     *
     * @param userId 用户 ID
     * @param paperSetId 模考试卷集 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> savePaperSetProgress(String userId, long paperSetId, MockExamRequests.ProgressSaveRequest request);

    /**
     * 废弃用户指定的模考进度。
     *
     * @param userId 用户 ID
     * @param progressId 进度记录 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> discardProgress(String userId, long progressId);

    /**
     * 查询用户收藏的题目列表。
     *
     * @param userId 用户 ID
     * @param examPaperId 模考试卷 ID
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    Map<String, Object> listQuestionFavorites(String userId, Long examPaperId, Integer limit);

    /**
     * 查询用户收藏的试卷或试卷集列表。
     *
     * @param userId 用户 ID
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    Map<String, Object> listEntityFavorites(String userId, Integer limit);

    /**
     * 翻译用户在模考题目中选中的文本片段。
     *
     * @param userId 用户 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> translateSelection(String userId, MockExamRequests.SelectionTranslateRequest request);

    /**
     * 切换用户对指定题目的收藏状态。
     *
     * @param userId 用户 ID
     * @param examQuestionId 题目 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> toggleQuestionFavorite(String userId, long examQuestionId, MockExamRequests.FavoriteToggleRequest request);

    /**
     * 切换用户对指定试卷的收藏状态。
     *
     * @param userId 用户 ID
     * @param examPaperId 模考试卷 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> togglePaperFavorite(String userId, long examPaperId, MockExamRequests.EntityFavoriteToggleRequest request);

    /**
     * 切换用户对指定试卷集的收藏状态。
     *
     * @param userId 用户 ID
     * @param paperSetId 模考试卷集 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> togglePaperSetFavorite(String userId, long paperSetId, MockExamRequests.EntityFavoriteToggleRequest request);

    /**
     * 查询用户待复盘的错题列表。
     *
     * @param userId 用户 ID
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    Map<String, Object> listWrongQuestions(String userId, Integer limit);

    /**
     * 标记用户错题的复盘处理状态。
     *
     * @param userId 用户 ID
     * @param request 请求体
     * @return 处理后的响应对象。
     */
    Map<String, Object> resolveWrongQuestions(String userId, MockExamRequests.WrongQuestionResolveRequest request);

    /**
     * 读取指定模考题目的详情。
     *
     * @param userId 用户 ID
     * @param examQuestionId 题目 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getQuestionDetail(String userId, long examQuestionId);
}
