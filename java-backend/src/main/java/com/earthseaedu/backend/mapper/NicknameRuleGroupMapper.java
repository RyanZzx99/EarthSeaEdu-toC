package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameRuleGroup;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NicknameRuleGroupMapper {

    List<NicknameRuleGroup> list(@Param("status") String status, @Param("groupType") String groupType);

    NicknameRuleGroup findActiveById(@Param("id") Integer id);

    NicknameRuleGroup findActiveByGroupCode(@Param("groupCode") String groupCode);

    int insert(NicknameRuleGroup row);

    int update(NicknameRuleGroup row);
}
