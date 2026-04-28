package com.earthseaedu.backend.mapper;

import java.util.Map;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActQuestionBankImportMapper {

    int insertImportBatch(Map<String, Object> row);

    int updateImportBatchProgress(Map<String, Object> row);

    int updateImportBatchCompleted(Map<String, Object> row);

    int updateImportBatchFailed(Map<String, Object> row);

    Map<String, Object> findActivePaperByCode(@Param("paperCode") String paperCode);

    Map<String, Object> findActivePaperById(@Param("paperId") long paperId);

    List<String> listActiveSectionNames();

    List<Long> listActivePaperIdsBySection(@Param("section") String section);

    List<Map<String, Object>> listActiveSectionsByPaperId(@Param("paperId") long paperId);

    List<Map<String, Object>> listActivePassagesByPaperId(@Param("paperId") long paperId);

    List<Map<String, Object>> listActiveGroupsByPaperId(@Param("paperId") long paperId);

    List<Map<String, Object>> listActiveQuestionsByPaperId(@Param("paperId") long paperId);

    List<Map<String, Object>> listActiveOptionsByGroupIds(@Param("groupIds") List<Long> groupIds);

    List<Map<String, Object>> listActiveAnswersByQuestionIds(@Param("questionIds") List<Long> questionIds);

    List<Map<String, Object>> listActiveAssetsByPaperId(@Param("paperId") long paperId);

    Map<String, Object> findActiveQuestionById(@Param("questionId") long questionId);

    Map<String, Object> findActiveAnswerByQuestionId(@Param("questionId") long questionId);

    List<Map<String, Object>> listActiveOptionsByGroupId(@Param("groupId") long groupId);

    int insertActPaper(Map<String, Object> row);

    int insertActSourceFile(Map<String, Object> row);

    int insertActSection(Map<String, Object> row);

    int insertActPassage(Map<String, Object> row);

    int insertActGroup(Map<String, Object> row);

    int insertActGroupOption(Map<String, Object> row);

    int insertActQuestion(Map<String, Object> row);

    int insertActQuestionAnswer(Map<String, Object> row);

    int insertActAsset(Map<String, Object> row);
}
