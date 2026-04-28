package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.ai.AiPromptConfig;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiPromptConfigMapper {

    List<AiPromptConfig> list(
        @Param("bizDomain") String bizDomain,
        @Param("promptStage") String promptStage,
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );

    long count(
        @Param("bizDomain") String bizDomain,
        @Param("promptStage") String promptStage,
        @Param("status") String status,
        @Param("keyword") String keyword
    );

    AiPromptConfig findActiveById(@Param("id") long id);

    int update(AiPromptConfig row);

    Map<String, Object> findRuntimePromptConfig(
        @Param("promptKey") String promptKey,
        @Param("allowedStatuses") List<String> allowedStatuses
    );
}
