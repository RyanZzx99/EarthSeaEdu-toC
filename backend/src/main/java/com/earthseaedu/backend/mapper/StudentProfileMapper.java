package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentProfileMapper {

    Integer countTable(@Param("tableName") String tableName);

    List<Map<String, Object>> showColumns(@Param("tableName") String tableName);

    List<Map<String, Object>> selectMultiRows(
        @Param("tableName") String tableName,
        @Param("columns") List<String> columns,
        @Param("studentId") String studentId
    );

    List<Map<String, Object>> selectDictionaryOptions(
        @Param("tableName") String tableName,
        @Param("valueColumn") String valueColumn,
        @Param("labelCnColumn") String labelCnColumn,
        @Param("labelEnColumn") String labelEnColumn
    );

    List<Map<String, Object>> listLanguageTestRecordsByStudentId(@Param("studentId") String studentId);

    List<Map<String, Object>> listLanguageTestScoreItemsByStudentId(@Param("studentId") String studentId);

    List<Map<String, Object>> listLanguageTestTypeOptions();

    List<Map<String, Object>> listLanguageTestScoreItemOptions();

    List<Map<String, Object>> listActivityAttachmentsByStudentId(@Param("studentId") String studentId);

    List<Map<String, Object>> listEnterpriseInternshipAttachmentsByStudentId(@Param("studentId") String studentId);

    List<Map<String, Object>> listResearchAttachmentsByStudentId(@Param("studentId") String studentId);

    List<Map<String, Object>> listCompetitionAttachmentsByStudentId(@Param("studentId") String studentId);

    int softDeleteLanguageTestScoreItemsByStudentId(@Param("studentId") String studentId);

    int softDeleteLanguageTestRecordsByStudentId(@Param("studentId") String studentId);

    int softDeleteActivityAttachmentsByStudentId(@Param("studentId") String studentId);

    int softDeleteEnterpriseInternshipAttachmentsByStudentId(@Param("studentId") String studentId);

    int softDeleteResearchAttachmentsByStudentId(@Param("studentId") String studentId);

    int softDeleteCompetitionAttachmentsByStudentId(@Param("studentId") String studentId);

    int softDeleteByStudent(
        @Param("tableName") String tableName,
        @Param("studentId") String studentId
    );

    Integer countActiveLanguageParentsByStudentId(@Param("studentId") String studentId);

    int insertLanguageParent(Map<String, Object> row);

    int insertLanguageTestRecord(Map<String, Object> row);

    int insertLanguageTestScoreItem(Map<String, Object> row);

    int insertDynamicRow(Map<String, Object> row);
}
