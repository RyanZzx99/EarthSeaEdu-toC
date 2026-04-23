package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameWordRule;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NicknameWordRuleMapper {

    List<NicknameWordRule> list(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("decision") String decision,
        @Param("keyword") String keyword,
        @Param("normalizedKeyword") String normalizedKeyword,
        @Param("limit") int limit
    );

    long count(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("decision") String decision,
        @Param("keyword") String keyword,
        @Param("normalizedKeyword") String normalizedKeyword
    );

    NicknameWordRule findActiveById(@Param("id") Integer id);

    NicknameWordRule findDuplicate(
        @Param("groupId") Integer groupId,
        @Param("normalizedWord") String normalizedWord,
        @Param("matchType") String matchType
    );

    int insert(NicknameWordRule row);

    int update(NicknameWordRule row);

    List<Map<String, Object>> listRuntimeRuleMaps();
}
