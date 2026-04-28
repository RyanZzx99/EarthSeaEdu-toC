package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameAuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NicknameAuditLogMapper {

    List<NicknameAuditLog> list(
        @Param("decision") String decision,
        @Param("scene") String scene,
        @Param("hitGroupCode") String hitGroupCode,
        @Param("limit") int limit
    );

    long count(
        @Param("decision") String decision,
        @Param("scene") String scene,
        @Param("hitGroupCode") String hitGroupCode
    );

    int insert(NicknameAuditLog row);
}
