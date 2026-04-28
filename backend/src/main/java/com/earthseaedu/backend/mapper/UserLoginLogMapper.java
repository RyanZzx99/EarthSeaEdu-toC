package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.auth.UserLoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLoginLogMapper {

    int insert(UserLoginLog loginLog);
}
