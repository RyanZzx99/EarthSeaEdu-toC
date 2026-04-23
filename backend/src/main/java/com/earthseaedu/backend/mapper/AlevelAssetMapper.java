package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelAsset;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelAssetMapper {

    List<AlevelAsset> findActiveByOwner(
        @Param("ownerType") String ownerType,
        @Param("ownerId") long ownerId
    );

    int insert(AlevelAsset row);

    int deactivateByOwner(
        @Param("ownerType") String ownerType,
        @Param("ownerId") long ownerId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
