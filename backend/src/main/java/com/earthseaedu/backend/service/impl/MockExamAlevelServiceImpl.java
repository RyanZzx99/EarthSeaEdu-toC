package com.earthseaedu.backend.service.impl;

import com.earthseaedu.backend.service.MockExamAlevelService;
import com.earthseaedu.backend.service.MockExamService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A-Level 模拟考试业务实现，当前复用已落地的 mockexam 读题能力。
 */
@Service
public class MockExamAlevelServiceImpl implements MockExamAlevelService {

    private static final String EXAM_CATEGORY_ALEVEL = "ALEVEL";

    private final MockExamService mockExamService;

    public MockExamAlevelServiceImpl(MockExamService mockExamService) {
        this.mockExamService = mockExamService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getOptions() {
        Map<String, Object> genericOptions = mockExamService.getOptions();
        List<String> contentOptions = extractAlevelContentOptions(genericOptions);

        Map<String, Object> contentOptionsMap = new LinkedHashMap<>();
        contentOptionsMap.put(EXAM_CATEGORY_ALEVEL, contentOptions);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exam_category_options", List.of(EXAM_CATEGORY_ALEVEL));
        response.put("supported_categories", List.of(EXAM_CATEGORY_ALEVEL));
        response.put("content_options", contentOptions);
        response.put("content_options_map", contentOptionsMap);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPapers(String examContent) {
        return mockExamService.listPapers(EXAM_CATEGORY_ALEVEL, examContent);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPaper(long examPaperId) {
        long normalizedPaperId = examPaperId > 0 ? -examPaperId : examPaperId;
        return mockExamService.getPaper(normalizedPaperId);
    }

    private List<String> extractAlevelContentOptions(Map<String, Object> genericOptions) {
        List<String> contentOptions = new ArrayList<>();
        Object contentOptionsMapObject = genericOptions.get("content_options_map");
        if (!(contentOptionsMapObject instanceof Map<?, ?> contentOptionsMap)) {
            return contentOptions;
        }

        Object rawOptions = contentOptionsMap.get(EXAM_CATEGORY_ALEVEL);
        if (!(rawOptions instanceof Iterable<?> values)) {
            return contentOptions;
        }

        for (Object value : values) {
            if (value != null) {
                contentOptions.add(String.valueOf(value));
            }
        }
        return contentOptions;
    }
}
