package com.earthseaedu.backend.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionBankImportService {

    private static final Set<String> SOURCE_MODES = Set.of("zip", "directory", "files");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLANK_PLACEHOLDER_PATTERN = Pattern.compile("\\[\\[\\s*([^\\]]+)\\s*\\]\\]|\\{\\{\\s*([^}]+)\\s*\\}\\}");
    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern OPTION_KEY_PATTERN = Pattern.compile("^\\s*([A-Z])(?:[\\.)\\u3001\\s]+)(.*)$", Pattern.DOTALL);

    private final JdbcTemplate jdbcTemplate;
    private final StoragePathSupport storagePathSupport;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "question-bank-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    public QuestionBankImportService(
        JdbcTemplate jdbcTemplate,
        StoragePathSupport storagePathSupport,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.storagePathSupport = storagePathSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> createImportJob(
        String sourceMode,
        String bankName,
        String entryPathsJson,
        List<MultipartFile> files
    ) {
        String normalizedSourceMode = normalizeSourceMode(sourceMode);
        String normalizedBankName = trimToNull(bankName);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少上传一个文件");
        }

        long jobId = insertImportJob(normalizedSourceMode, normalizedBankName, files.size());
        String storagePath = "job_" + jobId;
        try {
            persistUploadedFiles(storagePath, normalizedSourceMode, normalizedBankName, entryPathsJson, files);
            jdbcTemplate.update(
                """
                UPDATE exam_import_job
                SET storage_path = ?,
                    progress_message = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE exam_import_job_id = ?
                """,
                storagePath,
                "导入任务已创建，等待后台解析",
                jobId
            );
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存导入文件失败：" + exception.getMessage());
        }

        executorService.submit(() -> processImportJob(jobId));
        return getImportJobDetail(jobId);
    }

    public Map<String, Object> getImportJobDetail(long jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT *
            FROM exam_import_job
            WHERE exam_import_job_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            jobId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入任务不存在");
        }
        return serializeImportJob(rows.get(0));
    }

    private long insertImportJob(String sourceMode, String bankName, int uploadedFileCount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO exam_import_job
                  (job_name, bank_name, source_mode, storage_path, status, uploaded_file_count,
                   progress_message, delete_flag)
                VALUES
                  (?, ?, ?, NULL, 'pending', ?, ?, '1')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, "question_bank_import_" + System.currentTimeMillis());
            statement.setString(2, bankName);
            statement.setString(3, sourceMode);
            statement.setInt(4, uploadedFileCount);
            statement.setString(5, "导入任务已创建，等待后台解析");
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "创建导入任务失败");
        }
        return key.longValue();
    }

    private void persistUploadedFiles(
        String storagePath,
        String sourceMode,
        String bankName,
        String entryPathsJson,
        List<MultipartFile> files
    ) throws IOException {
        Path jobDir = importJobRoot().resolve(storagePath);
        FileUtil.mkdir(jobDir.toFile());

        JSONArray fileMetas = new JSONArray();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String storedName = "file_%05d.bin".formatted(index + 1);
            Path destination = jobDir.resolve(storedName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            JSONObject fileMeta = new JSONObject();
            fileMeta.set("filename", StrUtil.blankToDefault(file.getOriginalFilename(), storedName));
            fileMeta.set("stored_name", storedName);
            fileMetas.add(fileMeta);
        }

        JSONObject metadata = new JSONObject();
        metadata.set("source_mode", sourceMode);
        metadata.set("bank_name", bankName);
        metadata.set("entry_paths_json", entryPathsJson);
        metadata.set("files", fileMetas);
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(metadata), jobDir.resolve("metadata.json").toFile());
    }

    private void processImportJob(long jobId) {
        try {
            jdbcTemplate.update(
                """
                UPDATE exam_import_job
                SET status = 'running',
                    start_time = CURRENT_TIMESTAMP,
                    progress_message = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE exam_import_job_id = ?
                """,
                "后台解析已开始",
                jobId
            );
            Map<String, Object> row = requireImportJobRow(jobId);
            ImportMetadata metadata = loadMetadata(stringValue(row.get("storage_path")));
            ImportResult result = importFromStoredFiles(metadata, progress -> updateJobProgress(jobId, progress));
            updateJobCompleted(jobId, result);
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
        }
    }

    private ImportResult importFromStoredFiles(ImportMetadata metadata, ProgressSink progressSink) {
        String sourceMode = normalizeSourceMode(metadata.sourceMode());
        List<String> entryPaths = parseEntryPaths(metadata.entryPathsJson(), metadata.files().size());
        Map<String, VirtualImportFile> virtualFiles = buildVirtualFiles(metadata, entryPaths);
        List<ImportPackage> packages = discoverPackages(virtualFiles);
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("导入内容中没有找到 manifest.json");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        Set<String> touchedBankCodes = new LinkedHashSet<>();
        Set<String> seenBatchPaperCodes = new HashSet<>();
        Counts aggregateCounts = new Counts();
        int skippedCount = 0;

        List<PreparedPackage> preparedPackages = new ArrayList<>();
        for (ImportPackage importPackage : packages) {
            try {
                preparedPackages.add(preparePackage(importPackage, metadata.bankName()));
            } catch (Exception exception) {
                failures.add(failure(importPackage.rootPath(), exception));
            }
        }
        emitProgress(progressSink, virtualFiles.size(), packages.size(), items, failures, touchedBankCodes, aggregateCounts, skippedCount, "已识别 " + packages.size() + " 个题包，开始导入");

        Set<String> existingPaperCodes = loadExistingPaperCodes(preparedPackages);
        int totalPackages = preparedPackages.size();
        for (int index = 0; index < preparedPackages.size(); index++) {
            PreparedPackage preparedPackage = preparedPackages.get(index);
            if (seenBatchPaperCodes.contains(preparedPackage.paperCode())) {
                skippedCount++;
                items.add(resultItem(preparedPackage, "skipped_duplicate_batch"));
                emitProgress(progressSink, virtualFiles.size(), packages.size(), items, failures, touchedBankCodes, aggregateCounts, skippedCount, progressMessage(index + 1, totalPackages, items, skippedCount, failures));
                continue;
            }
            seenBatchPaperCodes.add(preparedPackage.paperCode());

            if (existingPaperCodes.contains(preparedPackage.paperCode())) {
                skippedCount++;
                items.add(resultItem(preparedPackage, "skipped_existing"));
                emitProgress(progressSink, virtualFiles.size(), packages.size(), items, failures, touchedBankCodes, aggregateCounts, skippedCount, progressMessage(index + 1, totalPackages, items, skippedCount, failures));
                continue;
            }

            try {
                PackageImportResult packageResult = transactionTemplate.execute(status -> importSinglePackage(preparedPackage));
                if (packageResult == null) {
                    throw new IllegalStateException("导入事务没有返回结果");
                }
                existingPaperCodes.add(packageResult.paperCode());
                touchedBankCodes.add(packageResult.bankCode());
                aggregateCounts.add(packageResult.counts());
                items.add(resultItem(preparedPackage, "imported"));
            } catch (Exception exception) {
                failures.add(failure(preparedPackage.rootPath(), exception));
            }
            emitProgress(progressSink, virtualFiles.size(), packages.size(), items, failures, touchedBankCodes, aggregateCounts, skippedCount, progressMessage(index + 1, totalPackages, items, skippedCount, failures));
        }

        int importedCount = importedCount(items);
        if (importedCount == 0 && skippedCount == 0) {
            String message = failures.stream()
                .limit(5)
                .map(item -> stringValue(item.get("package_root")) + "：" + stringValue(item.get("message")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("未成功导入任何题库");
            throw new IllegalArgumentException(message);
        }

        return new ImportResult(
            sourceMode,
            metadata.files().size(),
            virtualFiles.size(),
            packages.size(),
            importedCount,
            skippedCount,
            failures.size(),
            touchedBankCodes.size(),
            importedCount,
            aggregateCounts,
            items,
            failures
        );
    }

    private PackageImportResult importSinglePackage(PreparedPackage preparedPackage) {
        ImportPackage importPackage = preparedPackage.importPackage();
        JSONObject manifest = preparedPackage.manifest();
        long bankId = getOrCreateBank(
            preparedPackage.bankCode(),
            preparedPackage.bankName(),
            preparedPackage.subjectType(),
            firstNonBlank(importPackage.rootPath(), manifest.getStr("paper"), manifest.getStr("module"), "import")
        );
        long paperId = insertAndReturnId(
            """
            INSERT INTO exam_paper
              (exam_bank_id, paper_code, paper_name, module_name, subject_type, book_code, test_no, status, delete_flag)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1, '1')
            """,
            bankId,
            preparedPackage.paperCode(),
            preparedPackage.paperName(),
            trimToNull(manifest.getStr("module")),
            preparedPackage.subjectType(),
            preparedPackage.bookCode(),
            preparedPackage.testNo()
        );

        Counts counts = new Counts();
        JSONArray passages = preparedPackage.passages();
        for (int sectionIndex = 0; sectionIndex < passages.size(); sectionIndex++) {
            Object passageObject = passages.get(sectionIndex);
            if (!(passageObject instanceof JSONObject manifestPassage)) {
                continue;
            }
            String sourceFile = trimToNull(manifestPassage.getStr("file"));
            if (sourceFile == null) {
                throw new IllegalArgumentException((StrUtil.blankToDefault(importPackage.rootPath(), ".") + " 的 manifest.json 存在缺少 file 的 section"));
            }

            JSONObject sectionDoc = readJsonObject(importPackage, sourceFile);
            JSONObject sectionPayload = resolveSectionPayload(sectionDoc, manifestPassage.getStr("id"));
            String sectionContent = stringValue(sectionPayload.get("content"));
            String sectionInstructions = stringValue(sectionPayload.get("instructions"));
            long sectionId = insertAndReturnId(
                """
                INSERT INTO exam_section
                  (exam_paper_id, section_id, section_no, section_title, content_html, content_text,
                   instructions_html, instructions_text, sort_order, source_file, status, delete_flag)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, '1')
                """,
                paperId,
                firstNonBlank(sectionPayload.getStr("id"), manifestPassage.getStr("id")),
                parseQuestionNo(sectionPayload.getStr("id"), sectionIndex + 1),
                firstNonBlank(sectionPayload.getStr("title"), manifestPassage.getStr("title")),
                trimToNull(sectionContent),
                htmlToText(sectionContent),
                trimToNull(sectionInstructions),
                htmlToText(sectionInstructions),
                sectionIndex + 1,
                sourceFile
            );
            counts.sections++;

            List<Long> sectionAudioAssets = createAssets(
                importPackage,
                preparedPackage.paperCode(),
                "section",
                List.of(stringValue(sectionPayload.get("audio"))),
                "primary_audio",
                sourceFile,
                sectionId,
                null,
                null
            );
            if (!sectionAudioAssets.isEmpty()) {
                jdbcTemplate.update("UPDATE exam_section SET primary_audio_asset_id = ? WHERE exam_section_id = ?", sectionAudioAssets.get(0), sectionId);
                counts.assets += sectionAudioAssets.size();
            }
            List<Long> sectionImageAssets = createAssets(
                importPackage,
                preparedPackage.paperCode(),
                "section",
                concat(extractImageSources(sectionInstructions), extractImageSources(sectionContent)),
                "primary_image",
                sourceFile,
                sectionId,
                null,
                null
            );
            if (!sectionImageAssets.isEmpty()) {
                jdbcTemplate.update("UPDATE exam_section SET primary_image_asset_id = ? WHERE exam_section_id = ?", sectionImageAssets.get(0), sectionId);
                counts.assets += sectionImageAssets.size();
            }

            JSONArray groups = sectionPayload.getJSONArray("groups");
            if (groups == null) {
                continue;
            }
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                Object groupObject = groups.get(groupIndex);
                if (!(groupObject instanceof JSONObject groupPayload)) {
                    continue;
                }
                importGroup(preparedPackage, sourceFile, sectionId, groupPayload, groupIndex + 1, counts);
            }
        }

        return new PackageImportResult(
            preparedPackage.bankCode(),
            preparedPackage.bankName(),
            preparedPackage.paperCode(),
            preparedPackage.paperName(),
            preparedPackage.subjectType(),
            counts
        );
    }

    private void importGroup(
        PreparedPackage preparedPackage,
        String sourceFile,
        long sectionId,
        JSONObject groupPayload,
        int groupIndex,
        Counts counts
    ) {
        String rawType = trimToNull(groupPayload.getStr("type"));
        List<String> blankIds = collectBlankIds(groupPayload);
        List<String> rawQuestionIds = collectQuestionIds(groupPayload);
        List<String> sharedOptions = extractSharedOptions(groupPayload);
        String groupContent = extractGroupContentHtml(groupPayload);
        long groupId = insertAndReturnId(
            """
            INSERT INTO exam_group
              (exam_section_id, group_id, group_title, raw_type, stat_type, instructions_html, instructions_text,
               content_html, content_text, has_shared_options, has_blanks, structure_json, sort_order, status, delete_flag)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, '1')
            """,
            sectionId,
            trimToNull(groupPayload.getStr("id")),
            trimToNull(groupPayload.getStr("title")),
            rawType,
            inferStatType(rawType, groupPayload),
            trimToNull(groupPayload.getStr("instructions")),
            htmlToText(groupPayload.getStr("instructions")),
            trimToNull(groupContent),
            htmlToText(groupContent),
            sharedOptions.isEmpty() ? 0 : 1,
            blankIds.isEmpty() ? 0 : 1,
            JSONUtil.toJsonStr(Map.of("blank_ids", blankIds, "raw_question_ids", rawQuestionIds)),
            groupIndex
        );
        counts.groups++;

        List<Long> groupImageAssets = createAssets(
            preparedPackage.importPackage(),
            preparedPackage.paperCode(),
            "group",
            concat(extractImageSources(groupPayload.getStr("instructions")), extractImageSources(groupContent)),
            "primary_image",
            sourceFile,
            sectionId,
            groupId,
            null
        );
        if (!groupImageAssets.isEmpty()) {
            jdbcTemplate.update("UPDATE exam_group SET primary_image_asset_id = ? WHERE exam_group_id = ?", groupImageAssets.get(0), groupId);
            counts.assets += groupImageAssets.size();
        }

        for (int optionIndex = 0; optionIndex < sharedOptions.size(); optionIndex++) {
            ParsedOption option = parseOption(sharedOptions.get(optionIndex), optionIndex + 1);
            jdbcTemplate.update(
                """
                INSERT INTO exam_group_option
                  (exam_group_id, option_key, option_html, option_text, sort_order, status, delete_flag)
                VALUES (?, ?, ?, ?, ?, 1, '1')
                """,
                groupId,
                option.optionKey(),
                trimToNull(sharedOptions.get(optionIndex)),
                option.optionText(),
                optionIndex + 1
            );
            counts.options++;
        }

        JSONArray questions = groupPayload.getJSONArray("questions");
        if (questions == null) {
            return;
        }
        int questionSortOrder = 0;
        for (Object questionObject : questions) {
            if (!(questionObject instanceof JSONObject sourceQuestion)) {
                continue;
            }
            JSONArray blanks = sourceQuestion.getJSONArray("blanks");
            if (blanks != null && !blanks.isEmpty()) {
                for (int blankIndex = 0; blankIndex < blanks.size(); blankIndex++) {
                    Object blankObject = blanks.get(blankIndex);
                    if (!(blankObject instanceof JSONObject blankPayload)) {
                        continue;
                    }
                    questionSortOrder++;
                    importBlankQuestion(preparedPackage, sourceFile, sectionId, groupId, groupPayload, sourceQuestion, blankPayload, blankIndex + 1, questionSortOrder, counts);
                }
                continue;
            }
            questionSortOrder++;
            importSimpleQuestion(preparedPackage, sourceFile, sectionId, groupId, groupPayload, sourceQuestion, questionSortOrder, counts);
        }
    }

    private void importSimpleQuestion(
        PreparedPackage preparedPackage,
        String sourceFile,
        long sectionId,
        long groupId,
        JSONObject groupPayload,
        JSONObject sourceQuestion,
        int sortOrder,
        Counts counts
    ) {
        String questionId = firstNonBlank(sourceQuestion.getStr("id"), "q" + sortOrder);
        int questionNo = parseQuestionNo(firstNonBlank(questionId, sourceQuestion.getStr("stem"), sourceQuestion.getStr("content")), sortOrder);
        long questionDbId = insertQuestion(
            preparedPackage,
            groupId,
            questionId,
            questionNo,
            groupPayload.getStr("type"),
            inferStatType(groupPayload.getStr("type"), groupPayload),
            trimToNull(sourceQuestion.getStr("stem")),
            htmlToText(sourceQuestion.getStr("stem")),
            trimToNull(sourceQuestion.getStr("content")),
            htmlToText(sourceQuestion.getStr("content")),
            null,
            sortOrder
        );
        counts.questions++;
        insertAnswer(questionDbId, sourceQuestion.get("answer"), counts);
        List<Long> questionAssets = createAssets(
            preparedPackage.importPackage(),
            preparedPackage.paperCode(),
            "question",
            concat(extractImageSources(sourceQuestion.getStr("stem")), extractImageSources(sourceQuestion.getStr("content"))),
            "primary_image",
            sourceFile,
            sectionId,
            groupId,
            questionDbId
        );
        counts.assets += questionAssets.size();
    }

    private void importBlankQuestion(
        PreparedPackage preparedPackage,
        String sourceFile,
        long sectionId,
        long groupId,
        JSONObject groupPayload,
        JSONObject sourceQuestion,
        JSONObject blankPayload,
        int blankIndex,
        int sortOrder,
        Counts counts
    ) {
        String blankId = firstNonBlank(blankPayload.getStr("id"), "b" + sortOrder);
        String sourceQuestionId = trimToNull(sourceQuestion.getStr("id"));
        String questionId = firstNonBlank(sourceQuestionId, blankId);
        int questionNo = parseQuestionNo(firstNonBlank(blankId, sourceQuestion.getStr("content")), sortOrder);
        String sourceContent = firstNonBlank(sourceQuestion.getStr("content"), sourceQuestion.getStr("stem"));
        String questionContent = StrUtil.isBlank(sourceContent)
            ? "<p>Question " + questionNo + ": _____</p>"
            : sourceContent.replace("[[" + blankId + "]]", "_____").replace("{{" + blankId + "}}", "_____");
        long questionDbId = insertQuestion(
            preparedPackage,
            groupId,
            questionId,
            questionNo,
            groupPayload.getStr("type"),
            inferStatType(groupPayload.getStr("type"), groupPayload),
            trimToNull(sourceQuestion.getStr("stem")),
            htmlToText(sourceQuestion.getStr("stem")),
            trimToNull(questionContent),
            htmlToText(questionContent),
            blankId,
            sortOrder
        );
        counts.questions++;
        insertAnswer(questionDbId, blankPayload.get("answer"), counts);
        jdbcTemplate.update(
            """
            INSERT INTO exam_question_blank
              (exam_question_id, blank_id, sort_order, status, delete_flag)
            VALUES (?, ?, ?, 1, '1')
            """,
            questionDbId,
            blankId,
            blankIndex
        );
        counts.blanks++;
        List<Long> questionAssets = createAssets(
            preparedPackage.importPackage(),
            preparedPackage.paperCode(),
            "question",
            concat(extractImageSources(sourceQuestion.getStr("stem")), extractImageSources(questionContent)),
            "primary_image",
            sourceFile,
            sectionId,
            groupId,
            questionDbId
        );
        counts.assets += questionAssets.size();
    }

    private long insertQuestion(
        PreparedPackage preparedPackage,
        long groupId,
        String questionId,
        int questionNo,
        String rawType,
        String statType,
        String stemHtml,
        String stemText,
        String contentHtml,
        String contentText,
        String blankId,
        int sortOrder
    ) {
        String questionCode = buildQuestionCode(preparedPackage.paperCode(), questionId, blankId, questionNo, sortOrder);
        return insertAndReturnId(
            """
            INSERT INTO exam_question
              (exam_group_id, question_id, question_code, question_no, raw_type, stat_type,
               stem_html, stem_text, content_html, content_text, source_blank_id, sort_order, score, status, delete_flag)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, '1')
            """,
            groupId,
            trimToNull(questionId),
            questionCode,
            questionNo,
            trimToNull(rawType),
            trimToNull(statType),
            stemHtml,
            stemText,
            contentHtml,
            contentText,
            trimToNull(blankId),
            sortOrder,
            BigDecimal.ONE
        );
    }

    private void insertAnswer(long questionDbId, Object answer, Counts counts) {
        if (answer == null) {
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO exam_question_answer
              (exam_question_id, answer_raw, answer_json, status, delete_flag)
            VALUES (?, ?, ?, 1, '1')
            """,
            questionDbId,
            stringifyAnswer(answer),
            JSONUtil.toJsonStr(answer)
        );
        counts.answers++;
    }

    private List<Long> createAssets(
        ImportPackage importPackage,
        String paperCode,
        String ownerType,
        List<String> sourcePaths,
        String assetRole,
        String sourceContextPath,
        Long sectionId,
        Long groupId,
        Long questionId
    ) {
        List<String> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawPath : sourcePaths) {
            String sourcePath = trimToNull(rawPath);
            if (sourcePath == null || seen.contains(sourcePath)) {
                continue;
            }
            seen.add(sourcePath);
            deduped.add(sourcePath);
        }

        List<Long> result = new ArrayList<>();
        for (int index = 0; index < deduped.size(); index++) {
            String sourcePath = deduped.get(index);
            String role = index == 0 ? assetRole : "inline_image";
            String assetUrl = isExternalAssetUrl(sourcePath) ? sourcePath : "";
            String storagePath = null;
            if (StrUtil.isBlank(assetUrl)) {
                byte[] bytes = resolveAssetBytes(importPackage, sourcePath, sourceContextPath);
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                storagePath = buildAssetStoragePath(paperCode, ownerType, sourcePath, sectionId, groupId, questionId);
                Path destination = examAssetRoot().resolve(storagePath);
                try {
                    FileUtil.mkdir(destination.getParent().toFile());
                    Files.write(destination, bytes);
                } catch (IOException exception) {
                    continue;
                }
                assetUrl = "/exam-assets/" + storagePath.replace("\\", "/");
            }
            long assetId = insertAndReturnId(
                """
                INSERT INTO exam_asset
                  (exam_section_id, exam_group_id, exam_question_id, owner_type, asset_type,
                   asset_role, asset_name, source_path, storage_path, asset_url, sort_order, status, delete_flag)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, '1')
                """,
                sectionId,
                groupId,
                questionId,
                ownerType,
                inferAssetType(sourcePath),
                role,
                fileName(sourcePath),
                sourcePath,
                storagePath,
                assetUrl,
                index + 1
            );
            result.add(assetId);
        }
        return result;
    }

    private byte[] resolveAssetBytes(ImportPackage importPackage, String sourcePath, String sourceContextPath) {
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizeImportPath(sourcePath));
        String contextDir = parentPath(sourceContextPath);
        if (StrUtil.isNotBlank(contextDir)) {
            candidates.add(normalizeImportPath(contextDir + "/" + sourcePath));
        }
        String basename = fileName(sourcePath);
        if (StrUtil.isNotBlank(basename)) {
            candidates.add(basename);
        }
        for (String candidate : candidates) {
            VirtualImportFile file = importPackage.files().get(candidate);
            if (file != null) {
                return file.rawBytes();
            }
        }
        for (Map.Entry<String, VirtualImportFile> entry : importPackage.files().entrySet()) {
            if (fileName(entry.getKey()).equals(basename)) {
                return entry.getValue().rawBytes();
            }
        }
        return null;
    }

    private long getOrCreateBank(String bankCode, String bankName, String subjectType, String sourceName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT exam_bank_id, subject_scope
            FROM exam_bank
            WHERE bank_code = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            bankCode
        );
        if (rows.isEmpty()) {
            return insertAndReturnId(
                """
                INSERT INTO exam_bank
                  (bank_code, bank_name, exam_type, subject_scope, source_name, status, delete_flag)
                VALUES (?, ?, 'IELTS', ?, ?, 1, '1')
                """,
                bankCode,
                bankName,
                subjectType,
                sourceName
            );
        }
        Map<String, Object> row = rows.get(0);
        long bankId = longValue(row.get("exam_bank_id"));
        jdbcTemplate.update(
            """
            UPDATE exam_bank
            SET bank_name = ?,
                subject_scope = ?,
                source_name = ?,
                status = 1,
                delete_flag = '1',
                update_time = CURRENT_TIMESTAMP
            WHERE exam_bank_id = ?
            """,
            bankName,
            mergeSubjectScope(stringValue(row.get("subject_scope")), subjectType),
            sourceName,
            bankId
        );
        return bankId;
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入数据后没有返回主键");
        }
        return key.longValue();
    }

    private ImportMetadata loadMetadata(String storagePath) throws IOException {
        String normalizedStoragePath = trimToNull(storagePath);
        if (normalizedStoragePath == null) {
            throw new IllegalArgumentException("导入任务缺少 storage_path");
        }
        Path jobDir = importJobRoot().resolve(normalizedStoragePath);
        Path metadataPath = jobDir.resolve("metadata.json");
        if (!Files.exists(metadataPath)) {
            throw new IllegalArgumentException("导入任务缺少 metadata.json");
        }
        JSONObject metadata = JSONUtil.parseObj(FileUtil.readUtf8String(metadataPath.toFile()));
        JSONArray files = metadata.getJSONArray("files");
        List<StoredImportFile> storedFiles = new ArrayList<>();
        if (files != null) {
            for (Object item : files) {
                if (!(item instanceof JSONObject fileMeta)) {
                    continue;
                }
                String storedName = trimToNull(fileMeta.getStr("stored_name"));
                if (storedName == null) {
                    continue;
                }
                storedFiles.add(new StoredImportFile(
                    StrUtil.blankToDefault(fileMeta.getStr("filename"), storedName),
                    storedName,
                    jobDir.resolve(storedName)
                ));
            }
        }
        return new ImportMetadata(
            metadata.getStr("source_mode"),
            trimToNull(metadata.getStr("bank_name")),
            metadata.getStr("entry_paths_json"),
            storedFiles
        );
    }

    private Map<String, VirtualImportFile> buildVirtualFiles(ImportMetadata metadata, List<String> entryPaths) {
        Map<String, VirtualImportFile> result = new LinkedHashMap<>();
        for (int index = 0; index < metadata.files().size(); index++) {
            StoredImportFile storedFile = metadata.files().get(index);
            String logicalPath = normalizeImportPath(index < entryPaths.size() ? entryPaths.get(index) : storedFile.filename());
            logicalPath = StrUtil.blankToDefault(logicalPath, normalizeImportPath(storedFile.filename()));
            if (storedFile.filePath().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                || storedFile.filename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                addZipVirtualFiles(result, storedFile, index);
                continue;
            }
            try {
                result.put(logicalPath, new VirtualImportFile(logicalPath, Files.readAllBytes(storedFile.filePath()), storedFile.filename()));
            } catch (IOException exception) {
                throw new IllegalArgumentException(storedFile.filename() + " 读取失败：" + exception.getMessage(), exception);
            }
        }
        return result;
    }

    private void addZipVirtualFiles(Map<String, VirtualImportFile> result, StoredImportFile storedFile, int index) {
        String prefix = firstNonBlank(slugify(storedFile.filename()), "archive_" + (index + 1));
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(storedFile.filePath()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String logicalPath = normalizeImportPath(prefix + "/" + entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int length;
                while ((length = zipInputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
                byte[] rawBytes = output.toByteArray();
                result.put(logicalPath, new VirtualImportFile(logicalPath, rawBytes, storedFile.filename()));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(storedFile.filename() + " 不是合法 zip 压缩包", exception);
        }
    }

    private List<ImportPackage> discoverPackages(Map<String, VirtualImportFile> virtualFiles) {
        List<String> manifestPaths = virtualFiles.keySet().stream()
            .filter(path -> "manifest.json".equalsIgnoreCase(fileName(path)))
            .sorted()
            .toList();
        List<ImportPackage> result = new ArrayList<>();
        for (String manifestPath : manifestPaths) {
            String rootPath = parentPath(manifestPath);
            Map<String, VirtualImportFile> packageFiles = new LinkedHashMap<>();
            for (Map.Entry<String, VirtualImportFile> entry : virtualFiles.entrySet()) {
                String relativePath = makeRelativePath(entry.getKey(), rootPath);
                if (relativePath != null) {
                    packageFiles.put(relativePath, new VirtualImportFile(relativePath, entry.getValue().rawBytes(), entry.getValue().originName()));
                }
            }
            result.add(new ImportPackage(rootPath, packageFiles));
        }
        return result;
    }

    private PreparedPackage preparePackage(ImportPackage importPackage, String bankNameOverride) {
        JSONObject manifest = readJsonObject(importPackage, "manifest.json");
        JSONArray passages = manifest.getJSONArray("passages");
        if (passages == null || passages.isEmpty()) {
            throw new IllegalArgumentException("manifest.json 缺少 passages[]");
        }
        String subjectType = inferSubjectType(manifest, importPackage);
        BookTestIdentity bookTestIdentity = extractBookAndTest(manifest, importPackage);
        String bankCode = buildBankCode(manifest, importPackage, bookTestIdentity.bookCode());
        String bankName = firstNonBlank(bankNameOverride, bookTestIdentity.bookCode(), manifest.getStr("module"), importPackage.rootPath(), "IELTS Question Bank");
        String paperCode = buildPaperCode(bankCode, subjectType, bookTestIdentity.testNo(), manifest, importPackage);
        String paperName = buildPaperName(manifest, subjectType, bookTestIdentity.testNo(), paperCode);
        return new PreparedPackage(
            importPackage,
            manifest,
            passages,
            subjectType,
            bookTestIdentity.bookCode(),
            bookTestIdentity.testNo(),
            bankCode,
            bankName,
            paperCode,
            paperName
        );
    }

    private Set<String> loadExistingPaperCodes(List<PreparedPackage> preparedPackages) {
        Set<String> requestedCodes = new LinkedHashSet<>();
        for (PreparedPackage preparedPackage : preparedPackages) {
            requestedCodes.add(preparedPackage.paperCode());
        }
        if (requestedCodes.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(requestedCodes.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT paper_code
            FROM exam_paper
            WHERE delete_flag = '1'
              AND paper_code IN (%s)
            """.formatted(placeholders),
            requestedCodes.toArray()
        );
        Set<String> result = new HashSet<>();
        for (Map<String, Object> row : rows) {
            result.add(stringValue(row.get("paper_code")));
        }
        return result;
    }

    private JSONObject readJsonObject(ImportPackage importPackage, String path) {
        String normalizedPath = normalizeImportPath(path);
        VirtualImportFile file = importPackage.files().get(normalizedPath);
        if (file == null) {
            throw new IllegalArgumentException("题包缺少文件：" + normalizedPath);
        }
        String text = new String(file.rawBytes(), StandardCharsets.UTF_8);
        text = StrUtil.removePrefix(text, "\uFEFF");
        try {
            return JSONUtil.parseObj(text);
        } catch (Exception exception) {
            throw new IllegalArgumentException(file.logicalPath() + " 不是合法 JSON", exception);
        }
    }

    private JSONObject resolveSectionPayload(JSONObject sectionDoc, String expectedSectionId) {
        JSONArray passages = sectionDoc.getJSONArray("passages");
        if (passages == null || passages.isEmpty()) {
            return sectionDoc;
        }
        String normalizedExpectedId = trimToNull(expectedSectionId);
        if (normalizedExpectedId != null) {
            for (Object item : passages) {
                if (item instanceof JSONObject passage && normalizedExpectedId.equals(trimToNull(passage.getStr("id")))) {
                    return passage;
                }
            }
        }
        Object first = passages.get(0);
        if (first instanceof JSONObject firstPassage) {
            return firstPassage;
        }
        throw new IllegalArgumentException("section JSON passages[0] 不是对象");
    }

    private void updateJobProgress(long jobId, Progress progress) {
        JSONObject result = new JSONObject();
        result.set("items", progress.items());
        result.set("failures", progress.failures());
        result.set("skipped_count", progress.skippedCount());
        jdbcTemplate.update(
            """
            UPDATE exam_import_job
            SET resolved_file_count = ?,
                manifest_count = ?,
                success_count = ?,
                failure_count = ?,
                imported_bank_count = ?,
                imported_paper_count = ?,
                imported_section_count = ?,
                imported_group_count = ?,
                imported_question_count = ?,
                imported_answer_count = ?,
                imported_blank_count = ?,
                imported_option_count = ?,
                imported_asset_count = ?,
                progress_message = ?,
                result_json = ?,
                update_time = CURRENT_TIMESTAMP
            WHERE exam_import_job_id = ?
            """,
            progress.resolvedFileCount(),
            progress.manifestCount(),
            progress.successCount(),
            progress.failureCount(),
            progress.importedBankCount(),
            progress.importedPaperCount(),
            progress.counts().sections,
            progress.counts().groups,
            progress.counts().questions,
            progress.counts().answers,
            progress.counts().blanks,
            progress.counts().options,
            progress.counts().assets,
            progress.message(),
            result.toString(),
            jobId
        );
    }

    private void updateJobCompleted(long jobId, ImportResult result) {
        JSONObject resultJson = new JSONObject();
        resultJson.set("items", result.items());
        resultJson.set("failures", result.failures());
        resultJson.set("skipped_count", result.skippedCount());
        jdbcTemplate.update(
            """
            UPDATE exam_import_job
            SET status = 'completed',
                resolved_file_count = ?,
                manifest_count = ?,
                success_count = ?,
                failure_count = ?,
                imported_bank_count = ?,
                imported_paper_count = ?,
                imported_section_count = ?,
                imported_group_count = ?,
                imported_question_count = ?,
                imported_answer_count = ?,
                imported_blank_count = ?,
                imported_option_count = ?,
                imported_asset_count = ?,
                progress_message = ?,
                result_json = ?,
                finish_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            WHERE exam_import_job_id = ?
            """,
            result.resolvedFileCount(),
            result.manifestCount(),
            result.successCount(),
            result.failureCount(),
            result.importedBankCount(),
            result.importedPaperCount(),
            result.counts().sections,
            result.counts().groups,
            result.counts().questions,
            result.counts().answers,
            result.counts().blanks,
            result.counts().options,
            result.counts().assets,
            "导入完成：成功 " + result.successCount() + " 个，跳过 " + result.skippedCount() + " 个，失败 " + result.failureCount() + " 个",
            resultJson.toString(),
            jobId
        );
    }

    private void markJobFailed(long jobId, Exception exception) {
        jdbcTemplate.update(
            """
            UPDATE exam_import_job
            SET status = 'failed',
                error_message = ?,
                progress_message = ?,
                finish_time = COALESCE(finish_time, CURRENT_TIMESTAMP),
                update_time = CURRENT_TIMESTAMP
            WHERE exam_import_job_id = ?
            """,
            exception.getMessage(),
            "导入失败：" + exception.getMessage(),
            jobId
        );
    }

    private Map<String, Object> requireImportJobRow(long jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM exam_import_job WHERE exam_import_job_id = ? AND delete_flag = '1' LIMIT 1",
            jobId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入任务不存在：" + jobId);
        }
        return rows.get(0);
    }

    private Map<String, Object> serializeImportJob(Map<String, Object> row) {
        JSONObject resultPayload = parseObject(row.get("result_json"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job_id", longValue(row.get("exam_import_job_id")));
        result.put("job_name", row.get("job_name"));
        result.put("bank_name", row.get("bank_name"));
        result.put("source_mode", row.get("source_mode"));
        result.put("status", row.get("status"));
        result.put("uploaded_file_count", intValue(row.get("uploaded_file_count")));
        result.put("resolved_file_count", intValue(row.get("resolved_file_count")));
        result.put("manifest_count", intValue(row.get("manifest_count")));
        result.put("success_count", intValue(row.get("success_count")));
        result.put("skipped_count", intValue(resultPayload.get("skipped_count")));
        result.put("failure_count", intValue(row.get("failure_count")));
        result.put("imported_bank_count", intValue(row.get("imported_bank_count")));
        result.put("imported_paper_count", intValue(row.get("imported_paper_count")));
        result.put("imported_section_count", intValue(row.get("imported_section_count")));
        result.put("imported_group_count", intValue(row.get("imported_group_count")));
        result.put("imported_question_count", intValue(row.get("imported_question_count")));
        result.put("imported_answer_count", intValue(row.get("imported_answer_count")));
        result.put("imported_blank_count", intValue(row.get("imported_blank_count")));
        result.put("imported_option_count", intValue(row.get("imported_option_count")));
        result.put("imported_asset_count", intValue(row.get("imported_asset_count")));
        result.put("progress_message", row.get("progress_message"));
        result.put("error_message", row.get("error_message"));
        result.put("start_time", timestamp(row.get("start_time")));
        result.put("finish_time", timestamp(row.get("finish_time")));
        result.put("create_time", timestamp(row.get("create_time")));
        result.put("update_time", timestamp(row.get("update_time")));
        result.put("items", resultPayload.getJSONArray("items") == null ? List.of() : resultPayload.getJSONArray("items"));
        result.put("failures", resultPayload.getJSONArray("failures") == null ? List.of() : resultPayload.getJSONArray("failures"));
        return result;
    }

    private JSONObject parseObject(Object raw) {
        if (raw == null) {
            return new JSONObject();
        }
        try {
            if (raw instanceof JSONObject object) {
                return object;
            }
            if (raw instanceof Map<?, ?> map) {
                return new JSONObject(map);
            }
            String text = raw instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : stringValue(raw);
            if (StrUtil.isBlank(text)) {
                return new JSONObject();
            }
            return JSONUtil.parseObj(text);
        } catch (Exception exception) {
            return new JSONObject();
        }
    }

    private void emitProgress(
        ProgressSink sink,
        int resolvedFileCount,
        int manifestCount,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures,
        Set<String> touchedBankCodes,
        Counts counts,
        int skippedCount,
        String message
    ) {
        sink.accept(new Progress(
            resolvedFileCount,
            manifestCount,
            importedCount(items),
            skippedCount,
            failures.size(),
            touchedBankCodes.size(),
            importedCount(items),
            counts.copy(),
            message,
            List.copyOf(items),
            List.copyOf(failures)
        ));
    }

    private int importedCount(List<Map<String, Object>> items) {
        int count = 0;
        for (Map<String, Object> item : items) {
            if ("imported".equals(item.get("import_status"))) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> resultItem(PreparedPackage preparedPackage, String status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bank_code", preparedPackage.bankCode());
        item.put("bank_name", preparedPackage.bankName());
        item.put("paper_code", preparedPackage.paperCode());
        item.put("paper_name", preparedPackage.paperName());
        item.put("subject_type", preparedPackage.subjectType());
        item.put("import_status", status);
        item.put("package_root", StrUtil.blankToDefault(preparedPackage.rootPath(), "."));
        return item;
    }

    private Map<String, Object> failure(String packageRoot, Exception exception) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("package_root", StrUtil.blankToDefault(packageRoot, "."));
        item.put("message", exception.getMessage());
        return item;
    }

    private String progressMessage(int done, int total, List<Map<String, Object>> items, int skippedCount, List<Map<String, Object>> failures) {
        return "已完成 " + done + "/" + total + " 个题包，成功 " + importedCount(items) + " 个，跳过 " + skippedCount + " 个，失败 " + failures.size() + " 个";
    }

    private String normalizeSourceMode(String value) {
        String normalized = StrUtil.blankToDefault(value, "").trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_MODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "导入模式不合法，仅支持 zip / directory / files");
        }
        return normalized;
    }

    private List<String> parseEntryPaths(String entryPathsJson, int expectedCount) {
        String text = trimToNull(entryPathsJson);
        if (text == null) {
            return List.of();
        }
        Object parsed;
        try {
            parsed = JSONUtil.parse(text);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "entry_paths_json 不是合法 JSON");
        }
        if (!(parsed instanceof JSONArray values)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "entry_paths_json 必须是数组");
        }
        if (values.size() != expectedCount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "entry_paths_json 数量必须与上传文件数一致");
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            result.add(normalizeImportPath(stringValue(value)));
        }
        return result;
    }

    private String normalizeImportPath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\\", "/").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            String segment = part.trim();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(segment);
        }
        return String.join("/", parts);
    }

    private String makeRelativePath(String fullPath, String rootPath) {
        String normalizedFullPath = normalizeImportPath(fullPath);
        String normalizedRootPath = normalizeImportPath(rootPath);
        if (StrUtil.isBlank(normalizedRootPath)) {
            return normalizedFullPath;
        }
        if (normalizedFullPath.equals(normalizedRootPath)) {
            return "";
        }
        String prefix = normalizedRootPath + "/";
        if (!normalizedFullPath.startsWith(prefix)) {
            return null;
        }
        return normalizedFullPath.substring(prefix.length());
    }

    private String parentPath(String path) {
        String normalized = normalizeImportPath(path);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? "" : normalized.substring(0, index);
    }

    private String fileName(String path) {
        String normalized = normalizeImportPath(path);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private String inferSubjectType(JSONObject manifest, ImportPackage importPackage) {
        String probe = (stringValue(manifest.get("module")) + " " + stringValue(manifest.get("paper")) + " " + importPackage.rootPath()).toLowerCase(Locale.ROOT);
        if (probe.contains("listen")) {
            return "listening";
        }
        return "reading";
    }

    private BookTestIdentity extractBookAndTest(JSONObject manifest, ImportPackage importPackage) {
        String probe = firstNonBlank(manifest.getStr("module"), manifest.getStr("paper"), importPackage.rootPath(), "");
        Matcher numberMatcher = QUESTION_NUMBER_PATTERN.matcher(probe);
        String bookCode = null;
        Integer testNo = null;
        if (numberMatcher.find()) {
            bookCode = "C" + StrUtil.padPre(numberMatcher.group(1), 2, '0');
        }
        Matcher testMatcher = Pattern.compile("test\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(probe);
        if (testMatcher.find()) {
            testNo = intValue(testMatcher.group(1));
        }
        return new BookTestIdentity(bookCode, testNo);
    }

    private String buildBankCode(JSONObject manifest, ImportPackage importPackage, String bookCode) {
        if (StrUtil.isNotBlank(bookCode)) {
            return "ielts_" + bookCode.toLowerCase(Locale.ROOT);
        }
        String slug = slugify(firstNonBlank(manifest.getStr("module"), manifest.getStr("paper"), importPackage.rootPath(), "bank"));
        return shorten("ielts_" + slug, 100);
    }

    private String buildPaperCode(String bankCode, String subjectType, Integer testNo, JSONObject manifest, ImportPackage importPackage) {
        String testPart = testNo == null ? shortHash(firstNonBlank(manifest.getStr("paper"), manifest.getStr("module"), importPackage.rootPath(), bankCode)) : "test" + testNo;
        return shorten(bankCode + "_" + subjectType + "_" + testPart, 100);
    }

    private String buildPaperName(JSONObject manifest, String subjectType, Integer testNo, String paperCode) {
        return firstNonBlank(
            manifest.getStr("paper"),
            manifest.getStr("module"),
            "IELTS " + examContentFromSubjectType(subjectType) + (testNo == null ? "" : " Test " + testNo),
            paperCode
        );
    }

    private String buildQuestionCode(String paperCode, String questionId, String blankId, int questionNo, int sortOrder) {
        String source = firstNonBlank(questionId, blankId, "q" + questionNo, "q" + sortOrder);
        return shorten(paperCode + "_" + slugify(source) + "_" + shortHash(paperCode + source + blankId + sortOrder), 100);
    }

    private String slugify(String value) {
        String normalized = stringValue(value).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (StrUtil.isBlank(normalized)) {
            normalized = shortHash(value);
        }
        return normalized;
    }

    private String shortHash(String value) {
        return DigestUtil.md5Hex(stringValue(value)).substring(0, 8);
    }

    private String shorten(String value, int maxLength) {
        String text = stringValue(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 9)) + "_" + shortHash(text);
    }

    private int parseQuestionNo(String value, int fallback) {
        Matcher matcher = QUESTION_NUMBER_PATTERN.matcher(stringValue(value));
        return matcher.find() ? intValue(matcher.group(1)) : fallback;
    }

    private String inferStatType(String rawType, JSONObject groupPayload) {
        String probe = (stringValue(rawType) + " " + stringValue(groupPayload.get("title")) + " " + stringValue(groupPayload.get("instructions"))).toLowerCase(Locale.ROOT);
        if (probe.contains("tfng") || probe.contains("true") || probe.contains("false") || probe.contains("not given")) {
            return "true_false_not_given";
        }
        if (probe.contains("choice") || probe.contains("choose") || probe.contains("mcq")) {
            return "choice";
        }
        if (probe.contains("matching") || probe.contains("match")) {
            return "matching";
        }
        if (probe.contains("cloze") || probe.contains("blank") || probe.contains("completion")) {
            return "completion";
        }
        return trimToNull(rawType) == null ? "other" : trimToNull(rawType);
    }

    private String extractGroupContentHtml(JSONObject groupPayload) {
        String directContent = stringValue(groupPayload.get("content"));
        if (StrUtil.isNotBlank(directContent)) {
            return directContent;
        }
        JSONArray questions = groupPayload.getJSONArray("questions");
        if (questions == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Object questionObject : questions) {
            if (questionObject instanceof JSONObject question) {
                String content = stringValue(question.get("content"));
                if (StrUtil.isNotBlank(content) && !parts.contains(content)) {
                    parts.add(content);
                }
            }
        }
        return String.join("\n", parts);
    }

    private List<String> collectBlankIds(JSONObject groupPayload) {
        List<String> result = new ArrayList<>();
        JSONArray questions = groupPayload.getJSONArray("questions");
        if (questions == null) {
            return result;
        }
        for (Object questionObject : questions) {
            if (!(questionObject instanceof JSONObject question)) {
                continue;
            }
            JSONArray blanks = question.getJSONArray("blanks");
            if (blanks != null) {
                for (Object blankObject : blanks) {
                    if (blankObject instanceof JSONObject blank) {
                        String blankId = trimToNull(blank.getStr("id"));
                        if (blankId != null) {
                            result.add(blankId);
                        }
                    }
                }
            }
            Matcher matcher = BLANK_PLACEHOLDER_PATTERN.matcher(stringValue(question.get("content")));
            while (matcher.find()) {
                String blankId = firstNonBlank(matcher.group(1), matcher.group(2));
                if (StrUtil.isNotBlank(blankId) && !result.contains(blankId)) {
                    result.add(blankId);
                }
            }
        }
        return result;
    }

    private List<String> collectQuestionIds(JSONObject groupPayload) {
        List<String> result = new ArrayList<>();
        JSONArray questions = groupPayload.getJSONArray("questions");
        if (questions == null) {
            return result;
        }
        for (Object questionObject : questions) {
            if (questionObject instanceof JSONObject question) {
                String questionId = trimToNull(question.getStr("id"));
                if (questionId != null) {
                    result.add(questionId);
                }
            }
        }
        return result;
    }

    private List<String> extractSharedOptions(JSONObject groupPayload) {
        Object rawOptions = groupPayload.get("options");
        if (!(rawOptions instanceof JSONArray)) {
            rawOptions = groupPayload.get("choices");
        }
        if (!(rawOptions instanceof JSONArray options)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object option : options) {
            if (option instanceof JSONObject optionObject) {
                result.add(firstNonBlank(optionObject.getStr("html"), optionObject.getStr("text"), optionObject.toString()));
            } else {
                result.add(stringValue(option));
            }
        }
        return result;
    }

    private ParsedOption parseOption(String value, int fallbackIndex) {
        String text = htmlToText(value);
        Matcher matcher = OPTION_KEY_PATTERN.matcher(text);
        if (matcher.find()) {
            return new ParsedOption(matcher.group(1), trimToNull(matcher.group(2)));
        }
        return new ParsedOption(String.valueOf((char) ('A' + Math.max(0, fallbackIndex - 1))), trimToNull(text));
    }

    private List<String> extractImageSources(String html) {
        if (StrUtil.isBlank(html)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = IMG_SRC_PATTERN.matcher(html);
        while (matcher.find()) {
            String source = trimToNull(matcher.group(2));
            if (source != null) {
                result.add(source);
            }
        }
        return result;
    }

    private String htmlToText(String html) {
        if (StrUtil.isBlank(html)) {
            return null;
        }
        String withoutTags = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        String unescaped = HtmlUtil.unescape(withoutTags).replace('\u00A0', ' ');
        return trimToNull(unescaped.replaceAll("\\s+", " "));
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private String stringifyAnswer(Object answer) {
        if (answer instanceof JSONObject || answer instanceof JSONArray || answer instanceof Map<?, ?> || answer instanceof List<?>) {
            return JSONUtil.toJsonStr(answer);
        }
        return stringValue(answer);
    }

    private String inferAssetType(String sourcePath) {
        String lower = stringValue(sourcePath).toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(mp3|wav|m4a|aac|ogg)$")) {
            return "audio";
        }
        if (lower.matches(".*\\.(png|jpg|jpeg|gif|webp|svg)$")) {
            return "image";
        }
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        return "other";
    }

    private boolean isExternalAssetUrl(String sourcePath) {
        String lower = stringValue(sourcePath).toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/exam-assets/");
    }

    private String buildAssetStoragePath(String paperCode, String ownerType, String sourcePath, Long sectionId, Long groupId, Long questionId) {
        String ownerId = questionId != null ? "question_" + questionId : groupId != null ? "group_" + groupId : sectionId != null ? "section_" + sectionId : "paper";
        return normalizeImportPath(slugify(paperCode) + "/" + ownerType + "/" + ownerId + "/" + safeAssetFileName(sourcePath));
    }

    private String safeAssetFileName(String sourcePath) {
        String fileName = fileName(sourcePath);
        String suffix = "";
        int suffixIndex = fileName.lastIndexOf('.');
        if (suffixIndex >= 0) {
            suffix = fileName.substring(suffixIndex).toLowerCase(Locale.ROOT);
            fileName = fileName.substring(0, suffixIndex);
        }
        return slugify(fileName) + "_" + shortHash(sourcePath) + suffix;
    }

    private String examContentFromSubjectType(String subjectType) {
        return "listening".equalsIgnoreCase(subjectType) ? "Listening" : "Reading";
    }

    private String mergeSubjectScope(String existingScope, String subjectType) {
        Set<String> scopes = new LinkedHashSet<>();
        for (String part : stringValue(existingScope).split(",")) {
            String scope = trimToNull(part);
            if (scope != null) {
                scopes.add(scope);
            }
        }
        scopes.add(subjectType);
        return String.join(",", scopes);
    }

    private Path importJobRoot() {
        return storagePathSupport.ensureImportJobRoot();
    }

    private Path examAssetRoot() {
        return storagePathSupport.ensureExamAssetRoot();
    }

    private String trimToNull(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value).trim());
        } catch (Exception exception) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(stringValue(value).trim());
        } catch (Exception exception) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object timestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return value;
        }
        return value;
    }

    private record StoredImportFile(String filename, String storedName, Path filePath) {
    }

    private record VirtualImportFile(String logicalPath, byte[] rawBytes, String originName) {
    }

    private record ImportMetadata(String sourceMode, String bankName, String entryPathsJson, List<StoredImportFile> files) {
    }

    private record ImportPackage(String rootPath, Map<String, VirtualImportFile> files) {
    }

    private record BookTestIdentity(String bookCode, Integer testNo) {
    }

    private record PreparedPackage(
        ImportPackage importPackage,
        JSONObject manifest,
        JSONArray passages,
        String subjectType,
        String bookCode,
        Integer testNo,
        String bankCode,
        String bankName,
        String paperCode,
        String paperName
    ) {
        String rootPath() {
            return importPackage.rootPath();
        }
    }

    private record PackageImportResult(
        String bankCode,
        String bankName,
        String paperCode,
        String paperName,
        String subjectType,
        Counts counts
    ) {
    }

    private record ParsedOption(String optionKey, String optionText) {
    }

    private record Progress(
        int resolvedFileCount,
        int manifestCount,
        int successCount,
        int skippedCount,
        int failureCount,
        int importedBankCount,
        int importedPaperCount,
        Counts counts,
        String message,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures
    ) {
    }

    private record ImportResult(
        String sourceMode,
        int uploadedFileCount,
        int resolvedFileCount,
        int manifestCount,
        int successCount,
        int skippedCount,
        int failureCount,
        int importedBankCount,
        int importedPaperCount,
        Counts counts,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures
    ) {
    }

    @FunctionalInterface
    private interface ProgressSink {
        void accept(Progress progress);
    }

    private static class Counts {
        int sections;
        int groups;
        int questions;
        int answers;
        int blanks;
        int options;
        int assets;

        void add(Counts other) {
            this.sections += other.sections;
            this.groups += other.groups;
            this.questions += other.questions;
            this.answers += other.answers;
            this.blanks += other.blanks;
            this.options += other.options;
            this.assets += other.assets;
        }

        Counts copy() {
            Counts copy = new Counts();
            copy.sections = this.sections;
            copy.groups = this.groups;
            copy.questions = this.questions;
            copy.answers = this.answers;
            copy.blanks = this.blanks;
            copy.options = this.options;
            copy.assets = this.assets;
            return copy;
        }
    }
}
