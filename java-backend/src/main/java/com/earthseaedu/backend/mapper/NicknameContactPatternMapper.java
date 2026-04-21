package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.nickname.NicknameContactPattern;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NicknameContactPatternMapper {

    List<NicknameContactPattern> list(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("patternType") String patternType,
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );

    long count(
        @Param("groupId") Integer groupId,
        @Param("status") String status,
        @Param("patternType") String patternType,
        @Param("keyword") String keyword
    );

    NicknameContactPattern findActiveById(@Param("id") Integer id);

    NicknameContactPattern findDuplicate(
        @Param("patternName") String patternName,
        @Param("patternType") String patternType
    );

    int insert(NicknameContactPattern row);

    int update(NicknameContactPattern row);

    List<Map<String, Object>> listRuntimePatternMaps();
}
