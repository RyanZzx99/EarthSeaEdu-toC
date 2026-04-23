package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.InviteCode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InviteCodeMapper {

    InviteCode findActiveByCode(@Param("code") String code);

    int insert(InviteCode inviteCode);

    int update(InviteCode inviteCode);

    List<InviteCode> list(
        @Param("status") String status,
        @Param("mobile") String mobile,
        @Param("codeKeyword") String codeKeyword,
        @Param("inviteScene") String inviteScene,
        @Param("limit") int limit
    );

    long count(
        @Param("status") String status,
        @Param("mobile") String mobile,
        @Param("codeKeyword") String codeKeyword,
        @Param("inviteScene") String inviteScene
    );
}
