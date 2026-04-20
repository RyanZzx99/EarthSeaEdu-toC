package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameWordRule;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NicknameWordRuleMapper {

    @Select({
        "<script>",
        "SELECT id, group_id, word, normalized_word, match_type, decision, status, priority, risk_level, source, note,",
        "       effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag",
        "FROM nickname_word_rules",
        "WHERE delete_flag = '1'",
        "<if test='groupId != null'> AND group_id = #{groupId}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='decision != null'> AND decision = #{decision}</if>",
        "<if test='keyword != null'>",
        "  AND (word LIKE CONCAT('%', #{keyword}, '%')",
        "   OR normalized_word LIKE CONCAT('%', #{normalizedKeyword}, '%'))",
        "</if>",
        "ORDER BY priority ASC, id ASC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<NicknameWordRule> list(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("decision") String decision,
        @Param("keyword") String keyword,
        @Param("normalizedKeyword") String normalizedKeyword,
        @Param("limit") int limit
    );

    @Select({
        "<script>",
        "SELECT COUNT(1)",
        "FROM nickname_word_rules",
        "WHERE delete_flag = '1'",
        "<if test='groupId != null'> AND group_id = #{groupId}</if>",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='decision != null'> AND decision = #{decision}</if>",
        "<if test='keyword != null'>",
        "  AND (word LIKE CONCAT('%', #{keyword}, '%')",
        "   OR normalized_word LIKE CONCAT('%', #{normalizedKeyword}, '%'))",
        "</if>",
        "</script>"
    })
    long count(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("decision") String decision,
        @Param("keyword") String keyword,
        @Param("normalizedKeyword") String normalizedKeyword
    );

    @Select("""
        SELECT id, group_id, word, normalized_word, match_type, decision, status, priority, risk_level, source, note,
               effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        FROM nickname_word_rules
        WHERE id = #{id}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameWordRule findActiveById(@Param("id") Integer id);

    @Select("""
        SELECT id, group_id, word, normalized_word, match_type, decision, status, priority, risk_level, source, note,
               effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        FROM nickname_word_rules
        WHERE group_id = #{groupId}
          AND normalized_word = #{normalizedWord}
          AND match_type = #{matchType}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameWordRule findDuplicate(
        @Param("groupId") Integer groupId,
        @Param("normalizedWord") String normalizedWord,
        @Param("matchType") String matchType
    );

    @Insert("""
        INSERT INTO nickname_word_rules (
            group_id, word, normalized_word, match_type, decision, status, priority, risk_level, source, note,
            effective_start_time, effective_end_time, version_no, created_by, updated_by, create_time, update_time, delete_flag
        ) VALUES (
            #{groupId}, #{word}, #{normalizedWord}, #{matchType}, #{decision}, #{status}, #{priority}, #{riskLevel}, #{source}, #{note},
            #{effectiveStartTime}, #{effectiveEndTime}, #{versionNo}, #{createdBy}, #{updatedBy}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NicknameWordRule row);

    @Update("""
        UPDATE nickname_word_rules
        SET group_id = #{groupId},
            word = #{word},
            normalized_word = #{normalizedWord},
            match_type = #{matchType},
            decision = #{decision},
            status = #{status},
            priority = #{priority},
            risk_level = #{riskLevel},
            source = #{source},
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
    int update(NicknameWordRule row);

    @Select("""
        SELECT w.id AS ruleId,
               g.group_code AS groupCode,
               g.group_type AS groupType,
               w.word AS word,
               w.normalized_word AS normalizedWord,
               w.match_type AS matchType,
               w.decision AS decision,
               w.priority AS priority,
               w.risk_level AS riskLevel,
               w.status AS ruleStatus,
               w.delete_flag AS ruleDeleteFlag,
               w.effective_start_time AS ruleEffectiveStartTime,
               w.effective_end_time AS ruleEffectiveEndTime,
               g.status AS groupStatus,
               g.delete_flag AS groupDeleteFlag,
               g.effective_start_time AS groupEffectiveStartTime,
               g.effective_end_time AS groupEffectiveEndTime
        FROM nickname_word_rules w
        JOIN nickname_rule_groups g ON w.group_id = g.id
        WHERE w.delete_flag = '1'
          AND g.delete_flag = '1'
        ORDER BY w.priority ASC, g.priority ASC, w.id ASC
        """)
    List<Map<String, Object>> listRuntimeRuleMaps();
}
