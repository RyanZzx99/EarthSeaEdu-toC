package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessProfileFormMapper {

    Integer countTable(@Param("tableName") String tableName);

    List<Map<String, Object>> showColumns(@Param("tableName") String tableName);

    List<Map<String, Object>> selectSingleRow(
        @Param("tableName") String tableName,
        @Param("columns") List<String> columns,
        @Param("studentId") String studentId
    );

    List<Map<String, Object>> selectMultiRows(
        @Param("tableName") String tableName,
        @Param("columns") List<String> columns,
        @Param("studentId") String studentId
    );

    List<Map<String, Object>> selectProjectOutputRows(
        @Param("columns") List<String> columns,
        @Param("studentId") String studentId
    );

    List<Map<String, Object>> selectGuidedTargetCountryEntries(@Param("studentId") String studentId);

    List<Map<String, Object>> selectGuidedTargetMajorEntries(@Param("studentId") String studentId);

    List<Map<String, Object>> selectDictionaryOptions(
        @Param("tableName") String tableName,
        @Param("valueColumn") String valueColumn,
        @Param("labelCnColumn") String labelCnColumn,
        @Param("labelEnColumn") String labelEnColumn
    );
}
