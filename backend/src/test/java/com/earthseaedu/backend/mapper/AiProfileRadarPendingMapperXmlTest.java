package com.earthseaedu.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AiProfileRadarPendingMapperXmlTest {

    @Test
    void xmlDefinesStatementsForEveryMapperMethod() throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream inputStream = Resources.getResourceAsStream("mapper/AiProfileRadarPendingMapper.xml")) {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                inputStream,
                configuration,
                "mapper/AiProfileRadarPendingMapper.xml",
                configuration.getSqlFragments()
            );
            mapperBuilder.parse();
        }

        assertThat(Arrays.stream(AiProfileRadarPendingMapper.class.getDeclaredMethods()).map(Method::getName))
            .allSatisfy(methodName -> assertThat(configuration.hasStatement(AiProfileRadarPendingMapper.class.getName() + "." + methodName))
                .as(methodName)
                .isTrue());
    }
}
