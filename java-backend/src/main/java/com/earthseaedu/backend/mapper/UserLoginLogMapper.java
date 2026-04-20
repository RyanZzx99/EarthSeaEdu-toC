package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.UserLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface UserLoginLogMapper {

    @Insert("""
        INSERT INTO user_login_logs (
            user_id, login_type, login_identifier, success, failure_reason, ip, user_agent,
            create_time, update_time, delete_flag
        ) VALUES (
            #{userId}, #{loginType}, #{loginIdentifier}, #{success}, #{failureReason}, #{ip}, #{userAgent},
            #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserLoginLog loginLog);
}
