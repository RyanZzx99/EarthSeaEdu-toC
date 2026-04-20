package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamPaperSetItem;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MockExamPaperSetItemMapper {

    @Select("""
        <script>
        SELECT mockexam_paper_set_item_id, mockexam_paper_set_id, exam_paper_id, sort_order, create_time, update_time, delete_flag
        FROM mockexam_paper_set_item
        WHERE delete_flag = '1'
          AND mockexam_paper_set_id IN
          <foreach collection='paperSetIds' item='paperSetId' open='(' separator=',' close=')'>
            #{paperSetId}
          </foreach>
        ORDER BY mockexam_paper_set_id ASC, sort_order ASC, mockexam_paper_set_item_id ASC
        </script>
        """)
    List<MockExamPaperSetItem> findBySetIds(@Param("paperSetIds") List<Long> paperSetIds);

    @Insert("""
        INSERT INTO mockexam_paper_set_item (
            mockexam_paper_set_id, exam_paper_id, sort_order, create_time, update_time, delete_flag
        ) VALUES (
            #{mockexamPaperSetId}, #{examPaperId}, #{sortOrder}, #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    int insert(MockExamPaperSetItem row);
}
