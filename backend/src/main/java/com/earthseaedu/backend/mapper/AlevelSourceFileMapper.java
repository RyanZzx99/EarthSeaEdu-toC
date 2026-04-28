package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelSourceFile;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelSourceFileMapper {

    AlevelSourceFile findActiveById(@Param("sourceFileId") long sourceFileId);

    List<AlevelSourceFile> findActiveByPaperId(@Param("paperId") long paperId);

    List<AlevelSourceFile> findActiveByBundleCode(@Param("bundleCode") String bundleCode);

    int insert(AlevelSourceFile row);

    int deactivateDuplicatesBySourceName(
        @Param("sourceFileName") String sourceFileName,
        @Param("keepSourceFileId") long keepSourceFileId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivateStaleByPaperId(
        @Param("paperId") long paperId,
        @Param("keepSourceFileIds") List<Long> keepSourceFileIds,
        @Param("updateTime") LocalDateTime updateTime
    );

    int bindPaperId(
        @Param("sourceFileId") long sourceFileId,
        @Param("paperId") Long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int updateParseState(
        @Param("sourceFileId") long sourceFileId,
        @Param("parseStatus") String parseStatus,
        @Param("parseResultJson") String parseResultJson,
        @Param("parseWarningJson") String parseWarningJson,
        @Param("errorMessage") String errorMessage,
        @Param("updateTime") LocalDateTime updateTime
    );
}
