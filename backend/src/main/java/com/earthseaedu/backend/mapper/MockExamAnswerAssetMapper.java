package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamAnswerAsset;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamAnswerAssetMapper {

    List<MockExamAnswerAsset> findActiveByOwner(
        @Param("ownerType") String ownerType,
        @Param("ownerId") long ownerId
    );

    List<MockExamAnswerAsset> findActiveByQuestionRefId(@Param("questionRefId") long questionRefId);

    int insert(MockExamAnswerAsset row);

    int updateStatus(
        @Param("answerAssetId") long answerAssetId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
