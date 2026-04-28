package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelImportBatch;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelImportBatchMapper {

    AlevelImportBatch findActiveById(@Param("batchId") long batchId);

    AlevelImportBatch findActiveByBatchCode(@Param("batchCode") String batchCode);

    int insert(AlevelImportBatch row);

    int updateById(AlevelImportBatch row);

    int deactivateById(
        @Param("batchId") long batchId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
