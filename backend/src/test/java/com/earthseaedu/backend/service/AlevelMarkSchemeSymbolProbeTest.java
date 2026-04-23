package com.earthseaedu.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.earthseaedu.backend.mapper.AlevelPaperMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionAnswerMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionMapper;
import com.earthseaedu.backend.model.alevel.AlevelPaper;
import com.earthseaedu.backend.model.alevel.AlevelQuestion;
import com.earthseaedu.backend.model.alevel.AlevelQuestionAnswer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "management.health.redis.enabled=false",
        "spring.main.lazy-initialization=true"
    }
)
class AlevelMarkSchemeSymbolProbeTest {

    @Autowired
    private AlevelPaperMapper alevelPaperMapper;

    @Autowired
    private AlevelQuestionMapper alevelQuestionMapper;

    @Autowired
    private AlevelQuestionAnswerMapper alevelQuestionAnswerMapper;

    @Test
    void printsSuspiciousMarkSchemeCodePoints() {
        String paperCode = System.getProperty("alevel.paper.code");
        assumeTrue(StringUtils.hasText(paperCode), "manual probe requires -Dalevel.paper.code");

        AlevelPaper paper = alevelPaperMapper.findActiveByPaperCode(paperCode);
        assertThat(paper).isNotNull();

        List<AlevelQuestion> questions = new ArrayList<>(alevelQuestionMapper.findActiveByPaperId(paper.getAlevelPaperId()));
        questions.sort(Comparator.comparing(AlevelQuestion::getSortOrder, Comparator.nullsLast(Integer::compareTo)));

        int printed = 0;
        for (AlevelQuestion question : questions) {
            AlevelQuestionAnswer answer = alevelQuestionAnswerMapper.findActiveByQuestionId(question.getAlevelQuestionId());
            if (answer == null) {
                continue;
            }
            printed += printSuspicious("answer_raw", question.getQuestionNoDisplay(), answer.getAnswerRaw());
            printed += printSuspicious("mark_excerpt", question.getQuestionNoDisplay(), answer.getMarkSchemeExcerptText());
            if (printed >= 12) {
                break;
            }
        }
        assertThat(printed).isGreaterThanOrEqualTo(0);
    }

    private int printSuspicious(String source, String questionNo, String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int printed = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (isSuspicious(text, index, codePoint)) {
                System.out.println(source
                    + " | q=" + questionNo
                    + " | cp=U+" + String.format(Locale.ROOT, "%04X", codePoint)
                    + " | char=" + new String(Character.toChars(codePoint))
                    + " | context=" + context(text, index));
                printed++;
                if (printed >= 4) {
                    break;
                }
            }
            index += Character.charCount(codePoint);
        }
        return printed;
    }

    private boolean isSuspicious(String text, int index, int codePoint) {
        if (codePoint > 0x7F) {
            return true;
        }
        if (codePoint == '?') {
            boolean surroundedBySpace = index > 0
                && Character.isWhitespace(text.charAt(index - 1))
                && index + 1 < text.length()
                && Character.isWhitespace(text.charAt(index + 1));
            return surroundedBySpace;
        }
        return false;
    }

    private String context(String text, int index) {
        int start = Math.max(0, index - 24);
        int end = Math.min(text.length(), index + 24);
        return text.substring(start, end).replaceAll("\\s+", " ").trim();
    }
}
