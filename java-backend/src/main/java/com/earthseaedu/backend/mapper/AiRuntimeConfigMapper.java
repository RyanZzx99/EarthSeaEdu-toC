package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.ai.AiRuntimeConfig;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiRuntimeConfigMapper {

    @Select("""
        SELECT id, config_group, config_key, config_name, config_value, value_type, is_secret, status, sort_order, remark,
               created_by, updated_by, create_time, update_time, delete_flag
        FROM ai_runtime_configs
        WHERE delete_flag = '1'
        ORDER BY config_group ASC, sort_order ASC, id ASC
        """)
    List<AiRuntimeConfig> listActive();

    @Select("""
        SELECT id, config_group, config_key, config_name, config_value, value_type, is_secret, status, sort_order, remark,
               created_by, updated_by, create_time, update_time, delete_flag
        FROM ai_runtime_configs
        WHERE config_key = #{configKey}
          AND delete_flag = '1'
        LIMIT 1
        """)
    AiRuntimeConfig findActiveByConfigKey(@Param("configKey") String configKey);

    @Insert("""
        INSERT INTO ai_runtime_configs (
            config_group, config_key, config_name, config_value, value_type, is_secret, status, sort_order, remark,
            created_by, updated_by, create_time, update_time, delete_flag
        ) VALUES (
            #{configGroup}, #{configKey}, #{configName}, #{configValue}, #{valueType}, #{isSecret}, #{status}, #{sortOrder}, #{remark},
            #{createdBy}, #{updatedBy}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiRuntimeConfig row);

    @Update("""
        UPDATE ai_runtime_configs
        SET config_group = #{configGroup},
            config_name = #{configName},
            config_value = #{configValue},
            value_type = #{valueType},
            is_secret = #{isSecret},
            status = #{status},
            sort_order = #{sortOrder},
            remark = #{remark},
            updated_by = #{updatedBy},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(AiRuntimeConfig row);
}
