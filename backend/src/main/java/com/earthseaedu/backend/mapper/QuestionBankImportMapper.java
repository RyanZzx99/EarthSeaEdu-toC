package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuestionBankImportMapper {

    int updateImportJobStoragePath(
        @Param("jobId") long jobId,
        @Param("storagePath") String storagePath,
        @Param("progressMessage") String progressMessage
    );

    Map<String, Object> findActiveImportJobById(@Param("jobId") long jobId);

    int insertImportJob(Map<String, Object> row);

    int markImportJobRunning(@Param("jobId") long jobId, @Param("progressMessage") String progressMessage);

    int insertExamPaper(Map<String, Object> row);

    int insertExamSection(Map<String, Object> row);

    int updateExamSectionPrimaryAudioAssetId(@Param("sectionId") long sectionId, @Param("assetId") long assetId);

    int updateExamSectionPrimaryImageAssetId(@Param("sectionId") long sectionId, @Param("assetId") long assetId);

    int insertExamGroup(Map<String, Object> row);

    int updateExamGroupPrimaryImageAssetId(@Param("groupId") long groupId, @Param("assetId") long assetId);

    int insertExamGroupOption(
        @Param("groupId") long groupId,
        @Param("optionKey") String optionKey,
        @Param("optionHtml") String optionHtml,
        @Param("optionText") String optionText,
        @Param("sortOrder") int sortOrder
    );

    int insertExamQuestion(Map<String, Object> row);

    int insertExamQuestionBlank(
        @Param("questionId") long questionId,
        @Param("blankId") String blankId,
        @Param("sortOrder") int sortOrder
    );

    int insertExamQuestionAnswer(
        @Param("questionId") long questionId,
        @Param("answerRaw") String answerRaw,
        @Param("answerJson") String answerJson
    );

    int insertExamAsset(Map<String, Object> row);

    Map<String, Object> findActiveBankByCode(@Param("bankCode") String bankCode);

    int insertExamBank(Map<String, Object> row);

    int updateExamBank(
        @Param("bankId") long bankId,
        @Param("bankName") String bankName,
        @Param("subjectScope") String subjectScope,
        @Param("sourceName") String sourceName
    );

    List<String> findActivePaperCodes(@Param("paperCodes") List<String> paperCodes);

    int updateJobProgress(Map<String, Object> row);

    int updateJobCompleted(Map<String, Object> row);

    int markJobFailed(
        @Param("jobId") long jobId,
        @Param("errorMessage") String errorMessage,
        @Param("progressMessage") String progressMessage
    );
}
