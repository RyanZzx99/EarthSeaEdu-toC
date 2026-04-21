package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.UserAuthIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAuthIdentityMapper {

    UserAuthIdentity findActiveByUserIdAndType(
        @Param("userId") String userId,
        @Param("identityType") String identityType
    );

    UserAuthIdentity findActiveByTypeAndKey(
        @Param("identityType") String identityType,
        @Param("identityKey") String identityKey
    );

    int insert(UserAuthIdentity identity);

    int update(UserAuthIdentity identity);
}
