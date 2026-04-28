package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelSourceBundle;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelSourceBundleMapper {

    AlevelSourceBundle findActiveById(@Param("bundleId") long bundleId);

    AlevelSourceBundle findActiveByBundleCode(@Param("bundleCode") String bundleCode);

    List<AlevelSourceBundle> findActiveByImportBatchId(@Param("batchId") long batchId);

    List<AlevelSourceBundle> findActiveByPaperId(@Param("paperId") long paperId);

    int insert(AlevelSourceBundle row);

    int updateById(AlevelSourceBundle row);

    int deactivateById(
        @Param("bundleId") long bundleId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int refreshSummary(
        @Param("bundleId") long bundleId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
