package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelQuestionMaterialRef;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelQuestionMaterialRefMapper {

    AlevelQuestionMaterialRef findActiveById(@Param("materialRefId") long materialRefId);

    List<AlevelQuestionMaterialRef> findActiveByQuestionId(@Param("questionId") long questionId);

    List<AlevelQuestionMaterialRef> findActiveByQuestionIds(@Param("questionIds") List<Long> questionIds);

    List<AlevelQuestionMaterialRef> findActiveByPdfPageId(@Param("pdfPageId") long pdfPageId);

    int insert(AlevelQuestionMaterialRef row);

    int updateById(AlevelQuestionMaterialRef row);

    int deactivateById(
        @Param("materialRefId") long materialRefId,
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
