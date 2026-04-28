package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelQuestionAnswer;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelQuestionAnswerMapper {

    AlevelQuestionAnswer findActiveByQuestionId(@Param("questionId") long questionId);

    int insert(AlevelQuestionAnswer row);

    int deactivateByPaperId(
        @Param("paperId") long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int updateByQuestionId(
        @Param("questionId") long questionId,
        @Param("answerRaw") String answerRaw,
        @Param("answerJson") String answerJson,
        @Param("markSchemeJson") String markSchemeJson,
        @Param("markSchemeExcerptText") String markSchemeExcerptText,
        @Param("gradingMode") String gradingMode,
        @Param("updateTime") LocalDateTime updateTime
    );
}
