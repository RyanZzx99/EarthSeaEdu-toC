package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameAuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NicknameAuditLogMapper {

    @Select({
        "<script>",
        "SELECT id, trace_id, user_id, scene, raw_nickname, normalized_nickname, decision, hit_source, hit_rule_id, hit_pattern_id,",
        "       hit_group_code, hit_content, message, client_ip, user_agent, app_version, rule_version_batch, extra_json, create_time, update_time, delete_flag",
        "FROM nickname_audit_logs",
        "WHERE delete_flag = '1'",
        "<if test='decision != null'> AND decision = #{decision}</if>",
        "<if test='scene != null'> AND scene = #{scene}</if>",
        "<if test='hitGroupCode != null'> AND hit_group_code = #{hitGroupCode}</if>",
        "ORDER BY create_time DESC, id DESC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<NicknameAuditLog> list(
        @Param("decision") String decision,
        @Param("scene") String scene,
        @Param("hitGroupCode") String hitGroupCode,
        @Param("limit") int limit
    );

    @Select({
        "<script>",
        "SELECT COUNT(1)",
        "FROM nickname_audit_logs",
        "WHERE delete_flag = '1'",
        "<if test='decision != null'> AND decision = #{decision}</if>",
        "<if test='scene != null'> AND scene = #{scene}</if>",
        "<if test='hitGroupCode != null'> AND hit_group_code = #{hitGroupCode}</if>",
        "</script>"
    })
    long count(
        @Param("decision") String decision,
        @Param("scene") String scene,
        @Param("hitGroupCode") String hitGroupCode
    );

    @Insert("""
        INSERT INTO nickname_audit_logs (
            trace_id, user_id, scene, raw_nickname, normalized_nickname, decision, hit_source, hit_rule_id, hit_pattern_id,
            hit_group_code, hit_content, message, client_ip, user_agent, app_version, rule_version_batch, extra_json,
            create_time, update_time, delete_flag
        ) VALUES (
            #{traceId}, #{userId}, #{scene}, #{rawNickname}, #{normalizedNickname}, #{decision}, #{hitSource}, #{hitRuleId}, #{hitPatternId},
            #{hitGroupCode}, #{hitContent}, #{message}, #{clientIp}, #{userAgent}, #{appVersion}, #{ruleVersionBatch}, #{extraJson},
            #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NicknameAuditLog row);
}
