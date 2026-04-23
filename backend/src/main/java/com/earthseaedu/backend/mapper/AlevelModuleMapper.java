package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.alevel.AlevelModule;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlevelModuleMapper {

    AlevelModule findActiveById(@Param("moduleId") long moduleId);

    List<AlevelModule> findActiveByPaperId(@Param("paperId") long paperId);

    int insert(AlevelModule row);

    int deactivateByPaperId(
        @Param("paperId") long paperId,
        @Param("updateTime") LocalDateTime updateTime
    );
}
