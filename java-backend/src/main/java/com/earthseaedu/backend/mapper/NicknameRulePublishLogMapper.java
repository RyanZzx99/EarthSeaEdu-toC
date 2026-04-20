package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameRulePublishLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NicknameRulePublishLogMapper {

    @Select("""
        SELECT id, publish_batch_no, scope, change_summary, published_by, published_time, snapshot_json, rollback_batch_no,
               create_time, update_time, delete_flag
        FROM nickname_rule_publish_logs
        WHERE delete_flag = '1'
        ORDER BY published_time DESC, id DESC
        LIMIT 1
        """)
    NicknameRulePublishLog findLatestActive();
}
