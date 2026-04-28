package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamPaperRef;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamPaperRefMapper {

    MockExamPaperRef findActiveById(@Param("paperRefId") long paperRefId);

    MockExamPaperRef findActiveBySource(
        @Param("sourceType") String sourceType,
        @Param("sourcePaperId") long sourcePaperId
    );

    MockExamPaperRef findActiveByPaperCode(@Param("paperCode") String paperCode);

    List<Map<String, Object>> listActiveByCategoryAndContent(
        @Param("examCategory") String examCategory,
        @Param("examContent") String examContent
    );

    List<String> listActiveContentsByCategory(@Param("examCategory") String examCategory);

    int insert(MockExamPaperRef row);

    int updateMetadata(MockExamPaperRef row);

    int updateStatus(
        @Param("paperRefId") long paperRefId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
