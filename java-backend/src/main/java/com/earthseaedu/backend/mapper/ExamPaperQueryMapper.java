package com.earthseaedu.backend.mapper;

import com.earthseaedu.backend.model.mockexam.ExamPaperSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamPaperQueryMapper {

    @Select("""
        <script>
        SELECT p.exam_paper_id, p.paper_code, p.paper_name, p.subject_type, p.status, b.bank_name, b.status AS bank_status
        FROM exam_paper p
        JOIN exam_bank b ON b.exam_bank_id = p.exam_bank_id
        WHERE p.delete_flag = '1'
          AND b.delete_flag = '1'
          AND b.exam_type = 'IELTS'
          AND p.exam_paper_id IN
          <foreach collection='examPaperIds' item='examPaperId' open='(' separator=',' close=')'>
            #{examPaperId}
          </foreach>
          <if test='requireEnabled'>
            AND p.status = 1
            AND b.status = 1
          </if>
        </script>
        """)
    List<ExamPaperSummary> findByIds(
        @Param("examPaperIds") List<Long> examPaperIds,
        @Param("requireEnabled") boolean requireEnabled
    );
}
