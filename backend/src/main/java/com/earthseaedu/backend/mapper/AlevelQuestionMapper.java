package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelQuestion;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelQuestionMapper {

    AlevelQuestion findActiveById(@Param("questionId") long questionId);

    AlevelQuestion findActiveByQuestionCode(@Param("questionCode") String questionCode);

    List<AlevelQuestion> findActiveByPaperId(@Param("paperId") long paperId);

    List<AlevelQuestion> findActiveByModuleId(@Param("moduleId") long moduleId);

    List<AlevelQuestion> findActiveByParentQuestionId(@Param("parentQuestionId") long parentQuestionId);

    int insert(AlevelQuestion row);

    int deactivateByPaperId(
        @Param("paperId") long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int updateStatus(
        @Param("questionId") long questionId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
