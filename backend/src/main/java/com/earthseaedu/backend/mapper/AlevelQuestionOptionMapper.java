package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelQuestionOption;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelQuestionOptionMapper {

    List<AlevelQuestionOption> findActiveByQuestionId(@Param("questionId") long questionId);

    int insert(AlevelQuestionOption row);

    int deactivateByPaperId(
        @Param("paperId") long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
