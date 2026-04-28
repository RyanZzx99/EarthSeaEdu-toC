package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelQuestionMarkScheme;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelQuestionMarkSchemeMapper {

    AlevelQuestionMarkScheme findActiveById(@Param("markSchemeId") long markSchemeId);

    List<AlevelQuestionMarkScheme> findActiveByQuestionId(@Param("questionId") long questionId);

    List<AlevelQuestionMarkScheme> findActiveByQuestionIds(@Param("questionIds") List<Long> questionIds);

    List<AlevelQuestionMarkScheme> findActiveBySourceFileId(@Param("sourceFileId") long sourceFileId);

    int insert(AlevelQuestionMarkScheme row);

    int updateById(AlevelQuestionMarkScheme row);

    int deactivateById(
        @Param("markSchemeId") long markSchemeId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivateByQuestionId(
        @Param("questionId") long questionId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivateByPaperId(
        @Param("paperId") long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
