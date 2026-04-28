package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameRulePublishLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NicknameRulePublishLogMapper {

    NicknameRulePublishLog findLatestActive();
}
