package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.MockExamPaperSet;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MockExamPaperSetMapper {

    @Select("""
        SELECT mockexam_paper_set_id, set_name, exam_category, exam_content, paper_count, status, created_by, remark,
               create_time, update_time, delete_flag
        FROM mockexam_paper_set
        WHERE created_by = #{createdBy}
          AND delete_flag = '1'
        ORDER BY create_time DESC, mockexam_paper_set_id DESC
        """)
    List<MockExamPaperSet> findByCreatedBy(@Param("createdBy") String createdBy);

    @Select("""
        SELECT mockexam_paper_set_id, set_name, exam_category, exam_content, paper_count, status, created_by, remark,
               create_time, update_time, delete_flag
        FROM mockexam_paper_set
        WHERE mockexam_paper_set_id = #{paperSetId}
          AND delete_flag = '1'
        LIMIT 1
        """)
    MockExamPaperSet findActiveById(@Param("paperSetId") long paperSetId);

    @Insert("""
        INSERT INTO mockexam_paper_set (
            set_name, exam_category, exam_content, paper_count, status, created_by, remark,
            create_time, update_time, delete_flag
        ) VALUES (
            #{setName}, #{examCategory}, #{examContent}, #{paperCount}, #{status}, #{createdBy}, #{remark},
            #{createTime}, #{updateTime}, #{deleteFlag}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "mockexamPaperSetId")
    int insert(MockExamPaperSet row);

    @Update("""
        UPDATE mockexam_paper_set
        SET status = #{status},
            update_time = #{updateTime},
            delete_flag = '1'
        WHERE mockexam_paper_set_id = #{paperSetId}
        """)
    int updateStatus(
        @Param("paperSetId") long paperSetId,
        @Param("status") int status,
        @Param("updateTime") LocalDateTime updateTime
    );
}
