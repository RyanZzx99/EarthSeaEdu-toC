package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findActiveById(@Param("userId") String userId);

    User findActiveByMobile(@Param("mobile") String mobile);

    long countByNicknameExcludingUserId(@Param("nickname") String nickname, @Param("userId") String userId);

    int insert(User user);

    int update(User user);
}
