package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.InviteCode;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InviteCodeMapper {

    @Select("""
        SELECT id, code, invite_scene, status, issued_to_mobile, issued_by_user_id, used_by_user_id,
               issued_time, used_time, expires_time, note, create_time, update_time, delete_flag
        FROM invite_codes
        WHERE code = #{code}
          AND delete_flag = '1'
        LIMIT 1
        """)
    InviteCode findActiveByCode(@Param("code") String code);

    @Insert("""
        INSERT INTO invite_codes (
            code, invite_scene, status, issued_to_mobile, issued_by_user_id, used_by_user_id,
            issued_time, used_time, expires_time, note, create_time, update_time, delete_flag
        ) VALUES (
            #{code}, #{inviteScene}, #{status}, #{issuedToMobile}, #{issuedByUserId}, #{usedByUserId},
            #{issuedTime}, #{usedTime}, #{expiresTime}, #{note}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InviteCode inviteCode);

    @Update("""
        UPDATE invite_codes
        SET invite_scene = #{inviteScene},
            status = #{status},
            issued_to_mobile = #{issuedToMobile},
            issued_by_user_id = #{issuedByUserId},
            used_by_user_id = #{usedByUserId},
            issued_time = #{issuedTime},
            used_time = #{usedTime},
            expires_time = #{expiresTime},
            note = #{note},
            update_time = #{updateTime},
            delete_flag = #{deleteFlag}
        WHERE id = #{id}
        """)
    int update(InviteCode inviteCode);

    @Select({
        "<script>",
        "SELECT id, code, invite_scene, status, issued_to_mobile, issued_by_user_id, used_by_user_id,",
        "       issued_time, used_time, expires_time, note, create_time, update_time, delete_flag",
        "FROM invite_codes",
        "WHERE delete_flag = '1'",
        "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
        "<if test='mobile != null and mobile != \"\"'> AND issued_to_mobile = #{mobile}</if>",
        "<if test='codeKeyword != null and codeKeyword != \"\"'> AND UPPER(code) LIKE CONCAT('%', UPPER(#{codeKeyword}), '%')</if>",
        "<if test='inviteScene != null and inviteScene != \"\"'> AND invite_scene = #{inviteScene}</if>",
        "ORDER BY create_time DESC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<InviteCode> list(
        @Param("status") String status,
        @Param("mobile") String mobile,
        @Param("codeKeyword") String codeKeyword,
        @Param("inviteScene") String inviteScene,
        @Param("limit") int limit
    );

    @Select({
        "<script>",
        "SELECT COUNT(1)",
        "FROM invite_codes",
        "WHERE delete_flag = '1'",
        "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
        "<if test='mobile != null and mobile != \"\"'> AND issued_to_mobile = #{mobile}</if>",
        "<if test='codeKeyword != null and codeKeyword != \"\"'> AND UPPER(code) LIKE CONCAT('%', UPPER(#{codeKeyword}), '%')</if>",
        "<if test='inviteScene != null and inviteScene != \"\"'> AND invite_scene = #{inviteScene}</if>",
        "</script>"
    })
    long count(
        @Param("status") String status,
        @Param("mobile") String mobile,
        @Param("codeKeyword") String codeKeyword,
        @Param("inviteScene") String inviteScene
    );
}
