package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.ai.AiPromptConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiPromptConfigMapper {

    @Select({
        "<script>",
        "SELECT id, prompt_key, prompt_name, biz_domain, prompt_role, prompt_stage, prompt_content, prompt_version, status,",
        "       model_name, temperature, top_p, max_tokens, output_format, variables_json, remark,",
        "       created_by, updated_by, create_time, update_time, delete_flag",
        "FROM ai_prompt_configs",
        "WHERE delete_flag = '1'",
        "<if test='bizDomain != null'> AND biz_domain = #{bizDomain}</if>",
        "<if test='promptStage != null'> AND prompt_stage = #{promptStage}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='keyword != null'>",
        "  AND (prompt_key LIKE CONCAT('%', #{keyword}, '%')",
        "   OR prompt_name LIKE CONCAT('%', #{keyword}, '%')",
        "   OR prompt_version LIKE CONCAT('%', #{keyword}, '%')",
        "   OR remark LIKE CONCAT('%', #{keyword}, '%'))",
        "</if>",
        "ORDER BY update_time DESC, id DESC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<AiPromptConfig> list(
        @Param("bizDomain") String bizDomain,
        @Param("promptStage") String promptStage,
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );

    @Select({
        "<script>",
        "SELECT COUNT(1)",
        "FROM ai_prompt_configs",
        "WHERE delete_flag = '1'",
        "<if test='bizDomain != null'> AND biz_domain = #{bizDomain}</if>",
        "<if test='promptStage != null'> AND prompt_stage = #{promptStage}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='keyword != null'>",
        "  AND (prompt_key LIKE CONCAT('%', #{keyword}, '%')",
        "   OR prompt_name LIKE CONCAT('%', #{keyword}, '%')",
        "   OR prompt_version LIKE CONCAT('%', #{keyword}, '%')",
        "   OR remark LIKE CONCAT('%', #{keyword}, '%'))",
        "</if>",
        "</script>"
    })
    long count(
        @Param("bizDomain") String bizDomain,
        @Param("promptStage") String promptStage,
        @Param("status") String status,
        @Param("keyword") String keyword
    );

    @Select("""
        SELECT id, prompt_key, prompt_name, biz_domain, prompt_role, prompt_stage, prompt_content, prompt_version, status,
               model_name, temperature, top_p, max_tokens, output_format, variables_json, remark,
               created_by, updated_by, create_time, update_time, delete_flag
        FROM ai_prompt_configs
        WHERE id = #{id}
          AND delete_flag = '1'
        LIMIT 1
        """)
    AiPromptConfig findActiveById(@Param("id") long id);

    @Update("""
        UPDATE ai_prompt_configs
        SET prompt_name = #{promptName},
            prompt_content = #{promptContent},
            prompt_version = #{promptVersion},
            status = #{status},
            model_name = #{modelName},
            temperature = #{temperature},
            top_p = #{topP},
            max_tokens = #{maxTokens},
            output_format = #{outputFormat},
            variables_json = #{variablesJson},
            remark = #{remark},
            updated_by = #{updatedBy},
            update_time = #{updateTime}
        WHERE id = #{id}
        """)
    int update(AiPromptConfig row);
}
