package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamQuestionRef;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamQuestionRefMapper {

    MockExamQuestionRef findActiveById(@Param("questionRefId") long questionRefId);

    MockExamQuestionRef findActiveBySource(
        @Param("sourceType") String sourceType,
        @Param("sourceQuestionId") long sourceQuestionId
    );

    List<MockExamQuestionRef> findActiveByPaperRefId(@Param("paperRefId") long paperRefId);

    int insert(MockExamQuestionRef row);

    int updateMetadata(MockExamQuestionRef row);

    int updateStatus(
        @Param("questionRefId") long questionRefId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
