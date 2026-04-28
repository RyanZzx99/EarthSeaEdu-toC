package com.earthseaedu.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.earthseaedu.backend.mapper.AlevelAssetMapper;
import com.earthseaedu.backend.mapper.AlevelPaperMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionAnswerMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionOptionMapper;
import com.earthseaedu.backend.mapper.AlevelSourceFileMapper;
import com.earthseaedu.backend.mapper.MockExamPaperRefMapper;
import com.earthseaedu.backend.mapper.MockExamQuestionRefMapper;
import com.earthseaedu.backend.model.alevel.AlevelAsset;
import com.earthseaedu.backend.model.alevel.AlevelPaper;
import com.earthseaedu.backend.model.alevel.AlevelQuestion;
import com.earthseaedu.backend.model.alevel.AlevelQuestionAnswer;
import com.earthseaedu.backend.model.alevel.AlevelQuestionOption;
import com.earthseaedu.backend.model.alevel.AlevelSourceFile;
import com.earthseaedu.backend.model.mockexam.MockExamPaperRef;
import com.earthseaedu.backend.model.mockexam.MockExamQuestionRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StringUtils;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "management.health.redis.enabled=false",
        "spring.main.lazy-initialization=true"
    }
)
class AlevelSourceFileImportProbeTest {

    @Autowired
    private AlevelSourceFileImportService alevelSourceFileImportService;

    @Autowired
    private MockExamService mockExamService;

    @Autowired
    private AlevelPaperMapper alevelPaperMapper;

    @Autowired
    private AlevelSourceFileMapper alevelSourceFileMapper;

    @Autowired
    private AlevelQuestionMapper alevelQuestionMapper;

    @Autowired
    private AlevelQuestionOptionMapper alevelQuestionOptionMapper;

    @Autowired
    private AlevelQuestionAnswerMapper alevelQuestionAnswerMapper;

    @Autowired
    private AlevelAssetMapper alevelAssetMapper;

    @Autowired
    private MockExamPaperRefMapper mockExamPaperRefMapper;

    @Autowired
    private MockExamQuestionRefMapper mockExamQuestionRefMapper;

    @Test
    void importsConfiguredZipAndPrintsStorageSnapshot() throws Exception {
        String zipPathValue = System.getProperty("alevel.import.zip");
        String paperCode = System.getProperty("alevel.paper.code");
        String examContent = System.getProperty("alevel.exam.content", "Economics");

        assumeTrue(StringUtils.hasText(zipPathValue), "manual probe requires -Dalevel.import.zip");
        assumeTrue(StringUtils.hasText(paperCode), "manual probe requires -Dalevel.paper.code");

        Path zipPath = Path.of(zipPathValue);
        assumeTrue(Files.exists(zipPath), "zip file not found: " + zipPath);

        MockMultipartFile file = new MockMultipartFile(
            "files",
            zipPath.getFileName().toString(),
            "application/zip",
            Files.readAllBytes(zipPath)
        );

        Map<String, Object> createdJob = alevelSourceFileImportService.createImportJob(
            "zip",
            "alevel_probe_" + System.currentTimeMillis(),
            null,
            List.of(file)
        );
        long jobId = longValue(createdJob.get("job_id"));
        Map<String, Object> jobDetail = waitForImportJob(jobId, Duration.ofMinutes(3));

        assertThat(stringValue(jobDetail.get("status")))
            .withFailMessage("import job failed: %s", jobDetail)
            .isEqualToIgnoringCase("COMPLETED");

        AlevelPaper paper = alevelPaperMapper.findActiveByPaperCode(paperCode);
        assertThat(paper).as("alevel paper").isNotNull();

        List<AlevelSourceFile> sourceFiles = alevelSourceFileMapper.findActiveByPaperId(paper.getAlevelPaperId());
        List<AlevelQuestion> questions = new ArrayList<>(alevelQuestionMapper.findActiveByPaperId(paper.getAlevelPaperId()));
        questions.sort(Comparator.comparing(AlevelQuestion::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        List<AlevelQuestion> leafQuestions = questions.stream()
            .filter(question -> questions.stream().noneMatch(candidate -> Objects.equals(candidate.getParentQuestionId(), question.getAlevelQuestionId())))
            .toList();

        AlevelQuestion firstLeaf = leafQuestions.isEmpty() ? null : leafQuestions.getFirst();
        List<AlevelQuestionOption> firstOptions = firstLeaf == null
            ? List.of()
            : alevelQuestionOptionMapper.findActiveByQuestionId(firstLeaf.getAlevelQuestionId());
        AlevelQuestionAnswer firstAnswer = firstLeaf == null
            ? null
            : alevelQuestionAnswerMapper.findActiveByQuestionId(firstLeaf.getAlevelQuestionId());
        List<AlevelAsset> assets = alevelAssetMapper.findActiveByOwner("PAPER", paper.getAlevelPaperId());

        MockExamPaperRef paperRef = mockExamPaperRefMapper.findActiveBySource("A_LEVEL", paper.getAlevelPaperId());
        assertThat(paperRef).as("mockexam paper ref").isNotNull();

        List<MockExamQuestionRef> questionRefs = mockExamQuestionRefMapper.findActiveByPaperRefId(paperRef.getMockexamPaperRefId());
        assertThat(questionRefs).as("mockexam question refs").isNotEmpty();

        Map<String, Object> options = mockExamService.getOptions();
        Map<String, Object> papers = mockExamService.listPapers("ALEVEL", examContent);
        Map<String, Object> paperPayload = mockExamService.getPaper(-paperRef.getMockexamPaperRefId());
        Map<String, Object> questionDetail = mockExamService.getQuestionDetail("probe-user", -questionRefs.getFirst().getMockexamQuestionRefId());

        System.out.println("==== ALEVEL IMPORT PROBE ====");
        System.out.println("job: " + jobDetail);
        System.out.println("paper: id=" + paper.getAlevelPaperId()
            + ", code=" + paper.getPaperCode()
            + ", name=" + paper.getPaperName()
            + ", unit=" + paper.getUnitName()
            + ", total=" + paper.getTotalScore()
            + ", duration=" + paper.getDurationSeconds());
        System.out.println("source_files:");
        for (AlevelSourceFile sourceFile : sourceFiles) {
            System.out.println("  - " + sourceFile.getSourceFileType()
                + " | " + sourceFile.getSourceFileName()
                + " | pages=" + sourceFile.getPageCount()
                + " | parse=" + sourceFile.getParseStatus()
                + " | result=" + preview(sourceFile.getParseResultJson(), 200));
        }
        System.out.println("questions: all=" + questions.size() + ", leaf=" + leafQuestions.size());
        questions.stream().limit(12).forEach(question -> System.out.println(
            "  - " + question.getQuestionNoDisplay()
                + " | type=" + question.getQuestionType()
                + " | mode=" + question.getResponseMode()
                + " | marks=" + question.getMaxScore()
                + " | stem=" + preview(question.getStemText(), 140)
        ));
        if (firstLeaf != null) {
            System.out.println("first_leaf_question: " + firstLeaf.getQuestionNoDisplay());
            for (AlevelQuestionOption option : firstOptions) {
                System.out.println("    option " + option.getOptionKey() + ": " + preview(option.getOptionText(), 120));
            }
            System.out.println("    answer_raw=" + (firstAnswer == null ? null : preview(firstAnswer.getAnswerRaw(), 120)));
            System.out.println("    mark_scheme_json=" + (firstAnswer == null ? null : preview(firstAnswer.getMarkSchemeJson(), 220)));
            System.out.println("    mark_excerpt=" + (firstAnswer == null ? null : preview(firstAnswer.getMarkSchemeExcerptText(), 220)));
        }
        System.out.println("assets: " + assets.size());
        assets.forEach(asset -> System.out.println("  - " + asset.getAssetRole() + " | " + asset.getAssetName() + " | " + asset.getAssetUrl()));
        System.out.println("paper_ref: id=" + paperRef.getMockexamPaperRefId() + ", content=" + paperRef.getExamContent());
        System.out.println("question_ref_count: " + questionRefs.size());
        System.out.println("mockexam options: " + options);
        System.out.println("mockexam papers: " + papers);
        System.out.println("mockexam paper payload: " + preview(String.valueOf(paperPayload), 2000));
        System.out.println("mockexam first question detail: " + preview(String.valueOf(questionDetail), 2000));

        assertThat(sourceFiles).isNotEmpty();
        assertThat(questions).isNotEmpty();
        assertThat(papers).containsKey("items");
        assertThat(paperPayload).isNotEmpty();
        assertThat(questionDetail).isNotEmpty();
    }

    private Map<String, Object> waitForImportJob(long jobId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, Object> detail = null;
        while (System.nanoTime() < deadline) {
            detail = alevelSourceFileImportService.getImportJobDetail(jobId);
            String status = stringValue(detail.get("status"));
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return detail;
            }
            Thread.sleep(1000L);
        }
        return detail == null ? Map.of() : detail;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String preview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim() + "...";
    }
}
