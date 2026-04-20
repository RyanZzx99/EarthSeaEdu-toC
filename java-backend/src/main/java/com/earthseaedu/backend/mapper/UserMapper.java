package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
        SELECT id, mobile, mobile_verified, password_hash, nickname, avatar_url, sex, province, city, country,
               status, is_temp_wechat_user, is_teacher, create_time, update_time, delete_flag
        FROM users
        WHERE id = #{userId}
          AND delete_flag = '1'
        LIMIT 1
        """)
    User findActiveById(@Param("userId") String userId);

    @Select("""
        SELECT id, mobile, mobile_verified, password_hash, nickname, avatar_url, sex, province, city, country,
               status, is_temp_wechat_user, is_teacher, create_time, update_time, delete_flag
        FROM users
        WHERE mobile = #{mobile}
          AND delete_flag = '1'
        LIMIT 1
        """)
    User findActiveByMobile(@Param("mobile") String mobile);

    @Select("""
        SELECT COUNT(1)
        FROM users
        WHERE nickname = #{nickname}
          AND delete_flag = '1'
          AND id <> #{userId}
        """)
    long countByNicknameExcludingUserId(@Param("nickname") String nickname, @Param("userId") String userId);

    @Insert("""
        INSERT INTO users (
            id, mobile, mobile_verified, password_hash, nickname, avatar_url, sex, province, city, country,
            status, is_temp_wechat_user, is_teacher, create_time, update_time, delete_flag
        ) VALUES (
            #{id}, #{mobile}, #{mobileVerified}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{sex}, #{province}, #{city}, #{country},
            #{status}, #{isTempWechatUser}, #{isTeacher}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    int insert(User user);

    @Update("""
        UPDATE users
        SET mobile = #{mobile},
            mobile_verified = #{mobileVerified},
            password_hash = #{passwordHash},
            nickname = #{nickname},
            avatar_url = #{avatarUrl},
            sex = #{sex},
            province = #{province},
            city = #{city},
            country = #{country},
            status = #{status},
            is_temp_wechat_user = #{isTempWechatUser},
            is_teacher = #{isTeacher},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(User user);
}
