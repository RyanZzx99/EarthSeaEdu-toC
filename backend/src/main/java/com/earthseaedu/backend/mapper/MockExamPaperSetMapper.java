package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamPaperSet;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamPaperSetMapper {

    List<MockExamPaperSet> findByCreatedBy(@Param("createdBy") String createdBy);

    MockExamPaperSet findActiveById(@Param("paperSetId") long paperSetId);

    int insert(MockExamPaperSet row);

    int updateStatus(
        @Param("paperSetId") long paperSetId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
