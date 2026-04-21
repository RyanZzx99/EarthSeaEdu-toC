package com.earthseaedu.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.earthseaedu.backend.mapper.BusinessProfileFormMapper;
import com.earthseaedu.backend.service.impl.BusinessProfileFormServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessProfileFormServiceTest {

    private BusinessProfileFormService service;

    @BeforeEach
    void setUp() {
        BusinessProfileFormMapper mapper = mock(BusinessProfileFormMapper.class);
        service = new BusinessProfileFormServiceImpl(mapper, new ObjectMapper());

        Set<String> existingTables = Set.of("student_basic_info", "student_academic");

        given(mapper.countTable(anyString())).willAnswer(invocation -> {
            String tableName = invocation.getArgument(0, String.class);
            return existingTables.contains(tableName) ? 1 : 0;
        });

        given(mapper.showColumns("student_basic_info")).willReturn(List.of(
            column("current_grade", "varchar(32)"),
            column("MAJ_INTEREST_TEXT", "text"),
            column("curriculum_system_notes", "text")
        ));
        given(mapper.showColumns("student_academic")).willReturn(List.of(
            column("school_type_code", "varchar(32)")
        ));
    }

    @Test
    void buildBusinessProfileFormMetaUsesChineseMetadata() {
        Map<String, Object> formMeta = service.buildBusinessProfileFormMeta();

        Map<String, Object> tables = castMap(formMeta.get("tables"));
        Map<String, Object> basicInfo = castMap(tables.get("student_basic_info"));
        Map<String, Object> academic = castMap(tables.get("student_academic"));
        Map<String, Object> curriculumSystem = castMap(tables.get("student_basic_info_curriculum_system"));

        assertThat(basicInfo.get("label")).isEqualTo("学生基本信息");
        assertThat(curriculumSystem.get("label")).isEqualTo("课程体系");

        Map<String, Object> currentGrade = findField(castList(basicInfo.get("fields")), "current_grade");
        assertThat(currentGrade.get("label")).isEqualTo("当前年级");
        assertThat(castList(currentGrade.get("options")))
            .extracting(option -> castMap(option).get("label"))
            .contains("初一", "大一转学申请");

        Map<String, Object> majorInterest = findField(castList(basicInfo.get("fields")), "MAJ_INTEREST_TEXT");
        assertThat(majorInterest.get("helper_text"))
            .isEqualTo("如果目标专业下拉里没有合适选项，可在这里填写原始表述，例如：人文。");

        Map<String, Object> curriculumSystemNotes = findField(castList(basicInfo.get("fields")), "curriculum_system_notes");
        assertThat(curriculumSystemNotes.get("hidden")).isEqualTo(true);

        Map<String, Object> schoolType = findField(castList(academic.get("fields")), "school_type_code");
        assertThat(schoolType.get("label")).isEqualTo("学校类型");
        assertThat(castList(schoolType.get("options")))
            .extracting(option -> castMap(option).get("label"))
            .containsExactly("公立学校", "私立学校", "国际学校", "其他");
    }

    private static Map<String, Object> column(String field, String type) {
        return Map.of("Field", field, "Type", type);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> findField(List<Object> fields, String fieldName) {
        for (Object candidate : fields) {
            Map<String, Object> field = castMap(candidate);
            if (fieldName.equals(field.get("name"))) {
                return field;
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }
}
