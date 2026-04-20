package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameContactPattern;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NicknameContactPatternMapper {

    @Select({
        "<script>",
        "SELECT id, group_id, pattern_name, pattern_type, pattern_regex, decision, status, priority, risk_level, normalized_hint, note,",
        "       effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag",
        "FROM nickname_contact_patterns",
        "WHERE delete_flag = '1'",
        "<if test='groupId != null'> AND group_id = #{groupId}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='patternType != null'> AND pattern_type = #{patternType}</if>",
        "<if test='keyword != null'>",
        "  AND (pattern_name LIKE CONCAT('%', #{keyword}, '%')",
        "   OR pattern_regex LIKE CONCAT('%', #{keyword}, '%'))",
        "</if>",
        "ORDER BY priority ASC, id ASC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<NicknameContactPattern> list(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("patternType") String patternType,
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );

    @Select({
        "<script>",
        "SELECT COUNT(1)",
        "FROM nickname_contact_patterns",
        "WHERE delete_flag = '1'",
        "<if test='groupId != null'> AND group_id = #{groupId}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='patternType != null'> AND pattern_type = #{patternType}</if>",
        "<if test='keyword != null'>",
        "  AND (pattern_name LIKE CONCAT('%', #{keyword}, '%')",
        "   OR pattern_regex LIKE CONCAT('%', #{keyword}, '%'))",
        "</if>",
        "</script>"
    })
    long count(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("patternType") String patternType,
        @Param("keyword") String keyword
    );

    @Select("""
        SELECT id, group_id, pattern_name, pattern_type, pattern_regex, decision, status, priority, risk_level, normalized_hint, note,
               effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        FROM nickname_contact_patterns
        WHERE id = #{id}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameContactPattern findActiveById(@Param("id") Integer id);

    @Select("""
        SELECT id, group_id, pattern_name, pattern_type, pattern_regex, decision, status, priority, risk_level, normalized_hint, note,
               effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        FROM nickname_contact_patterns
        WHERE pattern_name = #{patternName}
          AND pattern_type = #{patternType}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameContactPattern findDuplicate(
        @Param("patternName") String patternName,
        @Param("patternType") String patternType
    );

    @Insert("""
        INSERT INTO nickname_contact_patterns (
            group_id, pattern_name, pattern_type, pattern_regex, decision, status, priority, risk_level, normalized_hint, note,
            effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        ) VALUES (
            #{groupId}, #{patternName}, #{patternType}, #{patternRegex}, #{decision}, #{status}, #{priority}, #{riskLevel}, #{normalizedHint}, #{note},
            #{effectiveStartTime}, #{effectiveEndTime}, #{versionNo}, #{createdBy}, #{updatedBy}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NicknameContactPattern row);

    @Update("""
        UPDATE nickname_contact_patterns
        SET group_id = #{groupId},
            pattern_name = #{patternName},
            pattern_type = #{patternType},
            pattern_regex = #{patternRegex},
            decision = #{decision},
            status = #{status},
            priority = #{priority},
            risk_level = #{riskLevel},
            normalized_hint = #{normalizedHint},
            note = #{note},
            effective_start_time = #{effectiveStartTime},
            effective_end_time = #{effectiveEndTime},
            version_no = #{versionNo},
            created_by = #{createdBy},
            updated_by = #{updatedBy},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(NicknameContactPattern row);

    @Select("""
        SELECT p.id AS patternId,
               g.id AS groupId,
               g.group_code AS groupCode,
               p.pattern_name AS patternName,
               p.pattern_type AS patternType,
               p.pattern_regex AS patternRegex,
               p.decision AS decision,
               p.priority AS priority,
               p.risk_level AS riskLevel,
               p.status AS patternStatus,
               p.delete_flag AS patternDeleteFlag,
               p.effective_start_time AS patternEffectiveStartTime,
               p.effective_end_time AS patternEffectiveEndTime,
               g.status AS groupStatus,
               g.delete_flag AS groupDeleteFlag,
               g.effective_start_time AS groupEffectiveStartTime,
               g.effective_end_time AS groupEffectiveEndTime
        FROM nickname_contact_patterns p
        LEFT JOIN nickname_rule_groups g ON p.group_id = g.id
        WHERE p.delete_flag = '1'
        ORDER BY p.priority ASC, p.id ASC
        """)
    List<Map<String, Object>> listRuntimePatternMaps();
}
