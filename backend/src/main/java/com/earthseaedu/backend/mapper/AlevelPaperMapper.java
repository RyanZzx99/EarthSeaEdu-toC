package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelPaper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelPaperMapper {

    AlevelPaper findActiveById(@Param("paperId") long paperId);

    AlevelPaper findActiveByPaperCode(@Param("paperCode") String paperCode);

    List<AlevelPaper> findActiveBySubjectAndSession(
        @Param("subjectCode") String subjectCode,
        @Param("examSession") String examSession
    );

    int insert(AlevelPaper row);

    int updateMetadata(AlevelPaper row);

    int updateStatus(
        @Param("paperId") long paperId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
