package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.ExamPaperSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExamPaperQueryMapper {

    List<ExamPaperSummary> findByIds(
        @Param("examPaperIds") List<Long> examPaperIds,
        @Param("requireEnabled") boolean requireEnabled
    );
}
