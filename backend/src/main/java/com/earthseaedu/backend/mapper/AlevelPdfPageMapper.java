package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelPdfPage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelPdfPageMapper {

    AlevelPdfPage findActiveById(@Param("pdfPageId") long pdfPageId);

    AlevelPdfPage findActiveBySourceFileAndPageNo(
        @Param("sourceFileId") long sourceFileId,
        @Param("pageNo") int pageNo
    );

    List<AlevelPdfPage> findActiveBySourceFileId(@Param("sourceFileId") long sourceFileId);

    List<AlevelPdfPage> findActiveByPaperId(@Param("paperId") long paperId);

    int insert(AlevelPdfPage row);

    int updateById(AlevelPdfPage row);

    int deactivateById(
        @Param("pdfPageId") long pdfPageId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivateBySourceFileId(
        @Param("sourceFileId") long sourceFileId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int bindPaperIdBySourceFileId(
        @Param("sourceFileId") long sourceFileId,
        @Param("paperId") Long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
