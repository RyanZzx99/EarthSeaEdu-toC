package com.earthseaedu.backend.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActImportJobMapper {

    int updateImportJobStoragePath(
        @Param("jobId") long jobId,
        @Param("storagePath") String storagePath,
        @Param("progressMessage") String progressMessage
    );

    Map<String, Object> findActiveImportJobById(@Param("jobId") long jobId);

    int insertImportJob(Map<String, Object> row);

    int markImportJobRunning(@Param("jobId") long jobId, @Param("progressMessage") String progressMessage);

    int updateJobProgress(Map<String, Object> row);

    int updateJobCompleted(Map<String, Object> row);

    int markJobFailed(
        @Param("jobId") long jobId,
        @Param("errorMessage") String errorMessage,
        @Param("progressMessage") String progressMessage
    );
}
