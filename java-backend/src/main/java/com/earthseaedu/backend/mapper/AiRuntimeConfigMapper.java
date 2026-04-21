package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.ai.AiRuntimeConfig;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRuntimeConfigMapper {

    List<AiRuntimeConfig> listActive();

    AiRuntimeConfig findActiveByConfigKey(@Param("configKey") String configKey);

    int insert(AiRuntimeConfig row);

    int update(AiRuntimeConfig row);

    Map<String, Object> findLatestRuntimeConfig(@Param("configKey") String configKey);
}
