package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelContentBlock;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelContentBlockMapper {

    List<AlevelContentBlock> findActiveByOwner(
        @Param("ownerType") String ownerType,
        @Param("ownerId") long ownerId
    );

    int insert(AlevelContentBlock row);
}
