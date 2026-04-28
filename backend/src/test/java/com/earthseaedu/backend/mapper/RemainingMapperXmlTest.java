package com.earthseaedu.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class RemainingMapperXmlTest {

    private static final Map<Class<?>, String> MAPPER_XML_RESOURCES = Map.ofEntries(
        Map.entry(ExamPaperQueryMapper.class, "mapper/ExamPaperQueryMapper.xml"),
        Map.entry(InviteCodeMapper.class, "mapper/InviteCodeMapper.xml"),
        Map.entry(MockExamPaperSetMapper.class, "mapper/MockExamPaperSetMapper.xml"),
        Map.entry(MockExamPaperSetItemMapper.class, "mapper/MockExamPaperSetItemMapper.xml"),
        Map.entry(NicknameAuditLogMapper.class, "mapper/NicknameAuditLogMapper.xml"),
        Map.entry(NicknameContactPatternMapper.class, "mapper/NicknameContactPatternMapper.xml"),
        Map.entry(NicknameRuleGroupMapper.class, "mapper/NicknameRuleGroupMapper.xml"),
        Map.entry(NicknameRulePublishLogMapper.class, "mapper/NicknameRulePublishLogMapper.xml"),
        Map.entry(NicknameWordRuleMapper.class, "mapper/NicknameWordRuleMapper.xml"),
        Map.entry(QuestionBankImportMapper.class, "mapper/QuestionBankImportMapper.xml"),
        Map.entry(UserMapper.class, "mapper/UserMapper.xml"),
        Map.entry(UserAuthIdentityMapper.class, "mapper/UserAuthIdentityMapper.xml"),
        Map.entry(UserLoginLogMapper.class, "mapper/UserLoginLogMapper.xml")
    );

    @Test
    void xmlDefinesStatementsForEveryMapperMethod() throws Exception {
        for (Map.Entry<Class<?>, String> entry : MAPPER_XML_RESOURCES.entrySet()) {
            Class<?> mapperClass = entry.getKey();
            String resource = entry.getValue();
            Configuration configuration = new Configuration();

            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
                );
                mapperBuilder.parse();
            }

            assertThat(Arrays.stream(mapperClass.getDeclaredMethods()).map(Method::getName))
                .allSatisfy(methodName -> assertThat(configuration.hasStatement(mapperClass.getName() + "." + methodName))
                    .as(mapperClass.getSimpleName() + "." + methodName)
                    .isTrue());
        }
    }
}
