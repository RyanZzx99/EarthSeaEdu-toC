package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameRuleGroup;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NicknameRuleGroupMapper {

    @Select({
        "<script>",
        "SELECT id, group_code, group_name, group_type, scope, status, priority, description,",
        "       effective_start_time, effective_end_time, version_no, created_by, updated_by,",
        "       create_time, update_time, delete_flag",
        "FROM nickname_rule_groups",
        "WHERE delete_flag = '1'",
        "<if test='status != null'> AND status = #{status}</if>",
        "<if test='groupType != null'> AND group_type = #{groupType}</if>",
        "ORDER BY priority ASC, id ASC",
        "</script>"
    })
    List<NicknameRuleGroup> list(@Param("status") String status, @Param("groupType") String groupType);

    @Select("""
        SELECT id, group_code, group_name, group_type, scope, status, priority, description,
               effective_start_time, effective_end_time, version_no, created_by, updated_by,
               create_time, update_time, delete_flag
        FROM nickname_rule_groups
        WHERE id = #{id}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameRuleGroup findActiveById(@Param("id") Integer id);

    @Select("""
        SELECT id, group_code, group_name, group_type, scope, status, priority, description,
               effective_start_time, effective_end_time, version_no, created_by, updated_by,
               create_time, update_time, delete_flag
        FROM nickname_rule_groups
        WHERE group_code = #{groupCode}
          AND delete_flag = '1'
        LIMIT 1
        """)
    NicknameRuleGroup findActiveByGroupCode(@Param("groupCode") String groupCode);

    @Insert("""
        INSERT INTO nickname_rule_groups (
            group_code, group_name, group_type, scope, status, priority, description,
            effective_start_time, effective_end_time, version_no, created_by, updated_by,
            create_time, update_time, delete_flag
        ) VALUES (
            #{groupCode}, #{groupName}, #{groupType}, #{scope}, #{status}, #{priority}, #{description},
            #{effectiveStartTime}, #{effectiveEndTime}, #{versionNo}, #{createdBy}, #{updatedBy},
            #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NicknameRuleGroup row);

    @Update("""
        UPDATE nickname_rule_groups
        SET group_code = #{groupCode},
            group_name = #{groupName},
            group_type = #{groupType},
            scope = #{scope},
            status = #{status},
            priority = #{priority},
            description = #{description},
            effective_start_time = #{effectiveStartTime},
            effective_end_time = #{effectiveEndTime},
            version_no = #{versionNo},
            created_by = #{createdBy},
            updated_by = #{updatedBy},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(NicknameRuleGroup row);
}
