package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamMapper {

    List<Map<String, Object>> listPapers(@Param("subjectType") String subjectType);

    Map<String, Object> findPaperRow(@Param("examPaperId") long examPaperId);

    List<Map<String, Object>> listPaperSets(
        @Param("examCategory") String examCategory,
        @Param("examContent") String examContent
    );

    Map<String, Object> findPaperSet(
        @Param("paperSetId") long paperSetId,
        @Param("requireEnabled") boolean requireEnabled
    );

    List<Map<String, Object>> listPaperSetItems(@Param("paperSetIds") List<Long> paperSetIds);

    List<Map<String, Object>> listSubmissions(
        @Param("userId") String userId,
        @Param("examContent") String examContent,
        @Param("limit") int limit
    );

    Map<String, Object> findSubmission(@Param("userId") String userId, @Param("submissionId") long submissionId);

    List<Map<String, Object>> listProgresses(@Param("userId") String userId, @Param("limit") int limit);

    Map<String, Object> findActiveProgress(@Param("userId") String userId, @Param("progressId") long progressId);

    Map<String, Object> findActiveProgressIdByPaper(@Param("userId") String userId, @Param("examPaperId") long examPaperId);

    Map<String, Object> findActiveProgressIdByPaperCode(@Param("userId") String userId, @Param("paperCode") String paperCode);

    List<Map<String, Object>> listQuestionFavorites(
        @Param("userId") String userId,
        @Param("examPaperId") Long examPaperId,
        @Param("limit") int limit
    );

    List<Map<String, Object>> listEntityFavorites(@Param("userId") String userId, @Param("limit") int limit);

    Map<String, Object> findQuestionFavorite(@Param("userId") String userId, @Param("examQuestionId") long examQuestionId);

    Map<String, Object> findActiveQuestionFavorite(@Param("userId") String userId, @Param("examQuestionId") long examQuestionId);

    Map<String, Object> findQuestionWrongCount(@Param("userId") String userId, @Param("examQuestionId") long examQuestionId);

    List<Map<String, Object>> listWrongQuestions(@Param("userId") String userId);

    Map<String, Object> findWrongQuestionSnapshot(@Param("examQuestionId") long examQuestionId);

    Map<String, Object> findSubmissionQuestionStateId(
        @Param("submissionId") long submissionId,
        @Param("examQuestionId") Long examQuestionId,
        @Param("questionId") String questionId
    );

    Map<String, Object> countActiveWrongQuestions(
        @Param("userId") String userId,
        @Param("examQuestionIds") List<Long> examQuestionIds
    );

    Map<String, Object> findQuestionSnapshot(@Param("examQuestionId") long examQuestionId);

    List<Map<String, Object>> listSectionsByPaper(@Param("examPaperId") long examPaperId);

    List<Map<String, Object>> listGroupsBySections(@Param("sectionIds") List<Long> sectionIds);

    List<Map<String, Object>> listQuestionsByGroups(@Param("groupIds") List<Long> groupIds);

    List<Map<String, Object>> listOptionsByGroups(@Param("groupIds") List<Long> groupIds);

    List<Map<String, Object>> listAnswersByQuestions(@Param("questionIds") List<Long> questionIds);

    List<Map<String, Object>> listBlanksByQuestions(@Param("questionIds") List<Long> questionIds);

    List<Map<String, Object>> listAssets(
        @Param("sectionIds") List<Long> sectionIds,
        @Param("groupIds") List<Long> groupIds,
        @Param("questionIds") List<Long> questionIds
    );

    List<Map<String, Object>> listOptionsForGroup(@Param("examGroupId") long examGroupId);

    List<Map<String, Object>> listQuestionAnswers(@Param("examQuestionId") long examQuestionId);

    List<Map<String, Object>> listQuestionBlanks(@Param("examQuestionId") long examQuestionId);

    List<Map<String, Object>> listQuestionAssets(@Param("examQuestionId") long examQuestionId);

    List<Map<String, Object>> listSectionAssets(@Param("sectionId") long sectionId);

    List<Map<String, Object>> listGroupAssets(@Param("groupId") long groupId);

    Map<String, Object> findPromptConfig(@Param("promptKey") String promptKey);

    Map<String, Object> findRuntimeConfig(@Param("configKey") String configKey);

    int markProgressSubmitted(Map<String, Object> row);

    int discardProgress(Map<String, Object> row);

    int deleteQuestionFavorite(@Param("userId") String userId, @Param("examQuestionId") long examQuestionId, @Param("now") Object now);

    int insertQuestionFavorite(Map<String, Object> row);

    int updateQuestionFavorite(Map<String, Object> row);

    int resolveWrongQuestions(@Param("userId") String userId, @Param("examQuestionIds") List<Long> examQuestionIds, @Param("now") Object now);

    int insertSubmission(Map<String, Object> row);

    int insertProgress(Map<String, Object> row);

    int updateProgress(Map<String, Object> row);

    int deleteSubmissionAnswers(@Param("submissionId") long submissionId);

    int deleteSubmissionStates(@Param("submissionId") long submissionId);

    int insertSubmissionAnswer(Map<String, Object> row);

    int insertSubmissionState(Map<String, Object> row);

    int deleteProgressAnswers(@Param("progressId") long progressId);

    int deleteProgressStates(@Param("progressId") long progressId);

    int insertProgressAnswer(Map<String, Object> row);

    int insertProgressState(Map<String, Object> row);

    List<Map<String, Object>> listSubmissionAnswers(@Param("submissionId") long submissionId);

    List<Map<String, Object>> listSubmissionStates(@Param("submissionId") long submissionId);

    List<Map<String, Object>> listProgressAnswers(@Param("progressId") long progressId);

    List<Map<String, Object>> listProgressStates(@Param("progressId") long progressId);

    int upsertWrongQuestionStat(Map<String, Object> row);

    int deleteEntityFavorite(Map<String, Object> row);

    int upsertEntityFavorite(Map<String, Object> row);
}
