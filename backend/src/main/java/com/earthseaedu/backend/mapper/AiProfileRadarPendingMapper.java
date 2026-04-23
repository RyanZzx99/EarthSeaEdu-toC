package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiProfileRadarPendingMapper {

    int insertPendingChange(Map<String, Object> row);

    int updatePendingChange(Map<String, Object> row);

    List<Map<String, Object>> listProfileResults(
        @Param("studentId") String studentId,
        @Param("sessionId") String sessionId
    );

    Map<String, Object> findPendingRow(@Param("sessionId") String sessionId);

    List<Map<String, Object>> listImpactRules(@Param("bizDomain") String bizDomain);
}
