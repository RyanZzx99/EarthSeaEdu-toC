package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessProfilePersistenceMapper {

    int deleteProjectOutputsByStudent(@Param("studentId") String studentId);

    int deleteByStudent(
        @Param("tableName") String tableName,
        @Param("studentId") String studentId
    );

    int insertDynamicRow(Map<String, Object> row);

    List<Map<String, Object>> listColumns(@Param("tableName") String tableName);

    int countTable(@Param("tableName") String tableName);
}
