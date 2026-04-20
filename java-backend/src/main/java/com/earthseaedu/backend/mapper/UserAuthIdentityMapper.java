package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.UserAuthIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAuthIdentityMapper {

    @Select("""
        SELECT id, user_id, identity_type, identity_key, identity_extra, create_time, update_time, delete_flag
        FROM user_auth_identities
        WHERE user_id = #{userId}
          AND identity_type = #{identityType}
          AND delete_flag = '1'
        LIMIT 1
        """)
    UserAuthIdentity findActiveByUserIdAndType(
        @Param("userId") String userId,
        @Param("identityType") String identityType
    );

    @Select("""
        SELECT id, user_id, identity_type, identity_key, identity_extra, create_time, update_time, delete_flag
        FROM user_auth_identities
        WHERE identity_type = #{identityType}
          AND identity_key = #{identityKey}
          AND delete_flag = '1'
        LIMIT 1
        """)
    UserAuthIdentity findActiveByTypeAndKey(
        @Param("identityType") String identityType,
        @Param("identityKey") String identityKey
    );

    @Insert("""
        INSERT INTO user_auth_identities (
            user_id, identity_type, identity_key, identity_extra, create_time, update_time, delete_flag
        ) VALUES (
            #{userId}, #{identityType}, #{identityKey}, #{identityExtra}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAuthIdentity identity);

    @Update("""
        UPDATE user_auth_identities
        SET user_id = #{userId},
            identity_type = #{identityType},
            identity_key = #{identityKey},
            identity_extra = #{identityExtra},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(UserAuthIdentity identity);
}
