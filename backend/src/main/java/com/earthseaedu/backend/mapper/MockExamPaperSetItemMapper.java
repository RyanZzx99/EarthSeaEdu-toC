package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamPaperSetItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockExamPaperSetItemMapper {

    List<MockExamPaperSetItem> findBySetIds(@Param("paperSetIds") List<Long> paperSetIds);

    int insert(MockExamPaperSetItem row);
}
