package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelSourceBundleFile;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelSourceBundleFileMapper {

    AlevelSourceBundleFile findActiveById(@Param("bundleFileId") long bundleFileId);

    List<AlevelSourceBundleFile> findActiveByBundleId(@Param("bundleId") long bundleId);

    List<AlevelSourceBundleFile> findActiveBySourceFileId(@Param("sourceFileId") long sourceFileId);

    int insert(AlevelSourceBundleFile row);

    int updateById(AlevelSourceBundleFile row);

    int deactivateById(
        @Param("bundleFileId") long bundleFileId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivateByBundleId(
        @Param("bundleId") long bundleId,
        @Param("updateTime") LocalDateTime updateTime
    );

    int deactivatePrimaryByBundleIdAndRole(
        @Param("bundleId") long bundleId,
        @Param("fileRole") String fileRole,
        @Param("updateTime") LocalDateTime updateTime
    );
}
