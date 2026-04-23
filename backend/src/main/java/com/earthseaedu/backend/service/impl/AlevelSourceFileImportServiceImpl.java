package com.earthseaedu.backend.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AlevelImportJobMapper;
import com.earthseaedu.backend.mapper.AlevelSourceFileMapper;
import com.earthseaedu.backend.model.alevel.AlevelSourceFile;
import com.earthseaedu.backend.service.AlevelQuestionBankBuildService;
import com.earthseaedu.backend.service.AlevelSourceFileImportService;
import com.earthseaedu.backend.support.AlevelPdfMetaSupport;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * AlevelSourceFileImportServiceImpl 服务实现，负责 A-Level 原始 PDF 文件的异步导入和分类入库。
 */
@Service
public class AlevelSourceFileImportServiceImpl implements AlevelSourceFileImportService {

    private static final String JOB_NAME_PREFIX = "alevel_source_import_";
    private static final Set<String> SOURCE_MODES = Set.of("zip", "directory", "files");

    private final AlevelImportJobMapper alevelImportJobMapper;
    private final AlevelSourceFileMapper alevelSourceFileMapper;
    private final AlevelQuestionBankBuildService alevelQuestionBankBuildService;
    private final AlevelPdfMetaSupport alevelPdfMetaSupport;
    private final StoragePathSupport storagePathSupport;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "alevel-source-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    public AlevelSourceFileImportServiceImpl(
        AlevelImportJobMapper alevelImportJobMapper,
        AlevelSourceFileMapper alevelSourceFileMapper,
        AlevelQuestionBankBuildService alevelQuestionBankBuildService,
        AlevelPdfMetaSupport alevelPdfMetaSupport,
        StoragePathSupport storagePathSupport,
        PlatformTransactionManager transactionManager
    ) {
        this.alevelImportJobMapper = alevelImportJobMapper;
        this.alevelSourceFileMapper = alevelSourceFileMapper;
        this.alevelQuestionBankBuildService = alevelQuestionBankBuildService;
        this.alevelPdfMetaSupport = alevelPdfMetaSupport;
        this.storagePathSupport = storagePathSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Map<String, Object> createImportJob(
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<MultipartFile> files
    ) {
        String normalizedSourceMode = normalizeSourceMode(sourceMode);
        validateUploadedFiles(normalizedSourceMode, files);

        long jobId = insertImportJob(normalizedSourceMode, trimToNull(batchName), files.size());
        String storagePath = "alevel_job_" + jobId;
        try {
            persistUploadedFiles(storagePath, normalizedSourceMode, trimToNull(batchName), entryPathsJson, files);
            alevelImportJobMapper.updateImportJobStoragePath(
                jobId,
                storagePath,
                "导入任务已创建，等待后台识别 PDF 并写入来源表"
            );
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存 A-Level 导入文件失败：" + exception.getMessage());
        }

        executorService.submit(() -> processImportJob(jobId));
        return getImportJobDetail(jobId);
    }

    @Override
    public Map<String, Object> getImportJobDetail(long jobId) {
        Map<String, Object> row = requireAlevelImportJobRow(jobId);
        return serializeImportJob(row);
    }

    private long insertImportJob(String sourceMode, String batchName, int uploadedFileCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobName", JOB_NAME_PREFIX + System.currentTimeMillis());
        row.put("batchName", batchName);
        row.put("sourceMode", sourceMode);
        row.put("uploadedFileCount", uploadedFileCount);
        row.put("progressMessage", "导入任务已创建，等待后台识别 PDF 并写入来源表");
        alevelImportJobMapper.insertImportJob(row);
        return generatedId(row, "创建 A-Level 导入任务失败");
    }

    private void persistUploadedFiles(
        String storagePath,
        String sourceMode,
        String batchName,
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
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            JSONObject fileMeta = new JSONObject();
            fileMeta.set("filename", CharSequenceUtil.blankToDefault(file.getOriginalFilename(), storedName));
            fileMeta.set("stored_name", storedName);
            fileMetas.add(fileMeta);
        }

        JSONObject metadata = new JSONObject();
        metadata.set("source_mode", sourceMode);
        metadata.set("batch_name", batchName);
        metadata.set("entry_paths_json", entryPathsJson);
        metadata.set("files", fileMetas);
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(metadata), jobDir.resolve("metadata.json").toFile());
    }

    private void processImportJob(long jobId) {
        try {
            alevelImportJobMapper.markImportJobRunning(jobId, "后台识别已开始");
            Map<String, Object> row = requireAlevelImportJobRow(jobId);
            ImportMetadata metadata = loadMetadata(stringValue(column(row, "storage_path")));
            ImportResult result = importFromStoredFiles(metadata, progress -> updateJobProgress(jobId, progress));
            updateJobCompleted(jobId, result);
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
        }
    }

    private ImportResult importFromStoredFiles(ImportMetadata metadata, ProgressSink progressSink) {
        String sourceMode = normalizeSourceMode(metadata.sourceMode());
        List<String> entryPaths = parseEntryPaths(metadata.entryPathsJson(), metadata.files().size());
        List<ResolvedPdfFile> resolvedPdfFiles = resolvePdfFiles(metadata, entryPaths);
        if (resolvedPdfFiles.isEmpty()) {
            throw new IllegalArgumentException("未识别到任何 PDF 文件");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        TypeCounts typeCounts = new TypeCounts();
        Set<String> bundleCodes = new LinkedHashSet<>();
        int builtBundleCount = 0;
        int questionCount = 0;
        int answerCount = 0;
        int questionRefCount = 0;

        emitProgress(
            progressSink,
            resolvedPdfFiles.size(),
            bundleCodes.size(),
            builtBundleCount,
            0,
            0,
            0,
            questionCount,
            answerCount,
            questionRefCount,
            typeCounts.copy(),
            items,
            failures,
            "已识别 " + resolvedPdfFiles.size() + " 个 PDF，开始写入来源表"
        );

        for (int index = 0; index < resolvedPdfFiles.size(); index++) {
            ResolvedPdfFile resolvedPdfFile = resolvedPdfFiles.get(index);
            try {
                ImportItem importItem = transactionTemplate.execute(status -> importSinglePdfFile(resolvedPdfFile));
                if (importItem == null) {
                    throw new IllegalStateException("PDF 导入事务没有返回结果");
                }
                bundleCodes.add(importItem.bundleCode());
                typeCounts.increment(importItem.sourceFileType());
                items.add(importItem.toPayload());
            } catch (Exception exception) {
                failures.add(failure(resolvedPdfFile.logicalPath(), exception));
            }

            emitProgress(
                progressSink,
                resolvedPdfFiles.size(),
                bundleCodes.size(),
                builtBundleCount,
                items.size(),
                failures.size(),
                items.size(),
                questionCount,
                answerCount,
                questionRefCount,
                typeCounts.copy(),
                items,
                failures,
                progressMessage(index + 1, resolvedPdfFiles.size(), items.size(), failures.size())
            );
        }

        if (items.isEmpty()) {
            String message = failures.stream()
                .limit(5)
                .map(item -> stringValue(item.get("source_name")) + "：" + stringValue(item.get("message")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("没有成功写入任何来源文件");
            throw new IllegalArgumentException(message);
        }

        int buildIndex = 0;
        for (String bundleCode : bundleCodes) {
            buildIndex++;
            try {
                AlevelQuestionBankBuildService.BuildResult buildResult =
                    transactionTemplate.execute(status -> alevelQuestionBankBuildService.buildBundle(bundleCode));
                if (buildResult == null) {
                    throw new IllegalStateException("bundle build transaction returned null");
                }
                builtBundleCount++;
                questionCount += buildResult.questionCount();
                answerCount += buildResult.answerCount();
                questionRefCount += buildResult.questionRefCount();
                appendBuildResult(items, bundleCode, buildResult);
            } catch (Exception exception) {
                appendBuildFailure(items, bundleCode, exception);
                failures.add(failure("bundle:" + bundleCode, exception));
            }

            emitProgress(
                progressSink,
                resolvedPdfFiles.size(),
                bundleCodes.size(),
                builtBundleCount,
                items.size(),
                failures.size(),
                items.size(),
                questionCount,
                answerCount,
                questionRefCount,
                typeCounts.copy(),
                items,
                failures,
                "姝ｅ湪鏋勫缓 bundle " + buildIndex + " / " + bundleCodes.size()
                    + "锛屽凡鎴愬姛鏋勫缓 " + builtBundleCount + " 涓?bundle"
            );
        }

        if (builtBundleCount <= 0) {
            String message = failures.stream()
                .limit(5)
                .map(item -> stringValue(item.get("source_name")) + " | " + stringValue(item.get("message")))
                .reduce((left, right) -> left + " ; " + right)
                .orElse("bundle build failed");
            throw new IllegalArgumentException(message);
        }
        /*
            String message = failures.stream()
                .limit(5)
                .map(item -> stringValue(item.get("source_name")) + "锛? + stringValue(item.get("message")))
                .reduce((left, right) -> left + "锛? + right)
                .orElse("bundle 瑙ｆ瀽澶辫触");
            throw new IllegalArgumentException(message);
        }

        */
        return new ImportResult(
            sourceMode,
            metadata.batchName(),
            metadata.files().size(),
            resolvedPdfFiles.size(),
            bundleCodes.size(),
            builtBundleCount,
            items.size(),
            failures.size(),
            questionCount,
            answerCount,
            questionRefCount,
            typeCounts.copy(),
            items,
            failures
        );
    }

    private ImportItem importSinglePdfFile(ResolvedPdfFile file) {
        String fileHash = DigestUtil.sha256Hex(file.rawBytes());
        String sourceFileName = fileName(file.logicalPath());
        AlevelPdfMetaSupport.DetectionResult detectionResult =
            alevelPdfMetaSupport.detect(file.rawBytes(), file.logicalPath(), sourceFileName);
        String sourceFileType = valueOrDefault(detectionResult.documentType(), "OTHER");
        String bundleCode = valueOrDefault(detectionResult.bundleKey(), deriveBundleCodeFallback(file.logicalPath()));
        String storagePath = buildStoragePath(bundleCode, file.logicalPath(), fileHash);
        String assetUrl = writeExamAsset(storagePath, file.rawBytes());

        AlevelSourceFile row = new AlevelSourceFile();
        row.setAlevelPaperId(null);
        row.setBundleCode(bundleCode);
        row.setSourceFileType(sourceFileType);
        row.setSourceFileName(sourceFileName);
        row.setSourceFileHash(fileHash);
        row.setStoragePath(storagePath);
        row.setAssetUrl(assetUrl);
        row.setPageCount(detectionResult.pageCount());
        row.setParseStatus("PENDING");
        row.setParseResultJson(
            alevelPdfMetaSupport.buildParseResultJson(file.logicalPath(), file.originName(), sourceFileType, bundleCode, detectionResult)
        );
        row.setParseWarningJson(alevelPdfMetaSupport.buildParseWarningJson(detectionResult, sourceFileType));
        row.setImportVersion(1);
        row.setIsVerified(0);
        row.setStatus(1);
        row.setErrorMessage(null);
        row.setRemark(null);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleteFlag("1");
        alevelSourceFileMapper.insert(row);
        alevelSourceFileMapper.deactivateDuplicatesBySourceName(
            row.getSourceFileName(),
            row.getAlevelSourceFileId(),
            LocalDateTime.now()
        );

        return new ImportItem(
            row.getAlevelSourceFileId(),
            file.logicalPath(),
            row.getSourceFileName(),
            sourceFileType,
            bundleCode,
            row.getStoragePath(),
            row.getAssetUrl(),
            row.getParseStatus()
        );
    }

    private List<ResolvedPdfFile> resolvePdfFiles(ImportMetadata metadata, List<String> entryPaths) {
        if ("zip".equals(metadata.sourceMode())) {
            return resolvePdfFilesFromZips(metadata.files());
        }
        return resolvePdfFilesFromDirectUploads(metadata.files(), entryPaths);
    }

    private List<ResolvedPdfFile> resolvePdfFilesFromZips(List<StoredImportFile> files) {
        List<ResolvedPdfFile> result = new ArrayList<>();
        for (StoredImportFile file : files) {
            try (InputStream inputStream = Files.newInputStream(file.filePath());
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String normalizedEntryPath = normalizeImportPath(entry.getName());
                    if (!isPdfName(normalizedEntryPath)) {
                        continue;
                    }
                    byte[] rawBytes = readAllBytes(zipInputStream);
                    String logicalPath = normalizeImportPath(stripExtension(file.filename()) + "/" + normalizedEntryPath);
                    result.add(new ResolvedPdfFile(logicalPath, rawBytes, file.filename()));
                }
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "解析压缩包失败：" + file.filename());
            }
        }
        return result;
    }

    private List<ResolvedPdfFile> resolvePdfFilesFromDirectUploads(List<StoredImportFile> files, List<String> entryPaths) {
        List<ResolvedPdfFile> result = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            StoredImportFile file = files.get(index);
            String logicalPath = entryPaths.size() > index ? entryPaths.get(index) : file.filename();
            logicalPath = CharSequenceUtil.isBlank(logicalPath) ? file.filename() : logicalPath;
            if (!isPdfName(logicalPath) && !isPdfName(file.filename())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持导入 PDF 文件：" + file.filename());
            }
            try {
                result.add(new ResolvedPdfFile(
                    normalizeImportPath(logicalPath),
                    Files.readAllBytes(file.filePath()),
                    file.filename()
                ));
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取上传文件失败：" + file.filename());
            }
        }
        return result;
    }

    private ImportMetadata loadMetadata(String storagePath) {
        Path jobDir = importJobRoot().resolve(stringValue(storagePath));
        Path metadataFile = jobDir.resolve("metadata.json");
        if (!Files.exists(metadataFile)) {
            throw new IllegalArgumentException("导入任务缺少 metadata.json：" + jobDir);
        }
        JSONObject metadata = JSONUtil.parseObj(FileUtil.readUtf8String(metadataFile.toFile()));
        JSONArray filesArray = metadata.getJSONArray("files");
        List<StoredImportFile> files = new ArrayList<>();
        for (Object item : filesArray) {
            if (!(item instanceof JSONObject object)) {
                continue;
            }
            String filename = object.getStr("filename");
            String storedName = object.getStr("stored_name");
            files.add(new StoredImportFile(
                filename,
                storedName,
                jobDir.resolve(storedName)
            ));
        }
        return new ImportMetadata(
            normalizeSourceMode(metadata.getStr("source_mode")),
            trimToNull(metadata.getStr("batch_name")),
            metadata.getStr("entry_paths_json"),
            files
        );
    }

    private void updateJobProgress(long jobId, Progress progress) {
        JSONObject resultJson = new JSONObject();
        resultJson.set("bundle_count", progress.bundleCount());
        resultJson.set("built_bundle_count", progress.builtBundleCount());
        resultJson.set("question_count", progress.questionCount());
        resultJson.set("answer_count", progress.answerCount());
        resultJson.set("question_ref_count", progress.questionRefCount());
        resultJson.set("question_paper_count", progress.typeCounts().questionPaperCount);
        resultJson.set("mark_scheme_count", progress.typeCounts().markSchemeCount);
        resultJson.set("insert_count", progress.typeCounts().insertCount);
        resultJson.set("exam_report_count", progress.typeCounts().examReportCount);
        resultJson.set("other_count", progress.typeCounts().otherCount);
        resultJson.set("items", progress.items());
        resultJson.set("failures", progress.failures());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", jobId);
        row.put("resolvedFileCount", progress.resolvedFileCount());
        row.put("bundleCount", progress.bundleCount());
        row.put("successCount", progress.successCount());
        row.put("failureCount", progress.failureCount());
        row.put("importedAssetCount", progress.importedAssetCount());
        row.put("progressMessage", progress.message());
        row.put("resultJson", resultJson.toString());
        alevelImportJobMapper.updateJobProgress(row);
    }

    private void updateJobCompleted(long jobId, ImportResult result) {
        JSONObject resultJson = new JSONObject();
        resultJson.set("bundle_count", result.bundleCount());
        resultJson.set("built_bundle_count", result.builtBundleCount());
        resultJson.set("question_count", result.questionCount());
        resultJson.set("answer_count", result.answerCount());
        resultJson.set("question_ref_count", result.questionRefCount());
        resultJson.set("question_paper_count", result.typeCounts().questionPaperCount);
        resultJson.set("mark_scheme_count", result.typeCounts().markSchemeCount);
        resultJson.set("insert_count", result.typeCounts().insertCount);
        resultJson.set("exam_report_count", result.typeCounts().examReportCount);
        resultJson.set("other_count", result.typeCounts().otherCount);
        resultJson.set("items", result.items());
        resultJson.set("failures", result.failures());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", jobId);
        row.put("resolvedFileCount", result.resolvedFileCount());
        row.put("bundleCount", result.bundleCount());
        row.put("successCount", result.successCount());
        row.put("failureCount", result.failureCount());
        row.put("importedAssetCount", result.successCount());
        row.put(
            "progressMessage",
            "导入完成：识别 " + result.resolvedFileCount() + " 个 PDF，成功入库 " + result.successCount() + " 个，失败 " + result.failureCount() + " 个"
        );
        row.put("resultJson", resultJson.toString());
        alevelImportJobMapper.updateJobCompleted(row);
    }

    private void markJobFailed(long jobId, Exception exception) {
        alevelImportJobMapper.markJobFailed(jobId, exception.getMessage(), "导入失败：" + exception.getMessage());
    }

    private void appendBuildResult(
        List<Map<String, Object>> items,
        String bundleCode,
        AlevelQuestionBankBuildService.BuildResult buildResult
    ) {
        for (Map<String, Object> item : items) {
            if (!bundleCode.equals(stringValue(item.get("bundle_code")))) {
                continue;
            }
            item.putAll(buildResult.payload());
        }
    }

    private void appendBuildFailure(List<Map<String, Object>> items, String bundleCode, Exception exception) {
        for (Map<String, Object> item : items) {
            if (!bundleCode.equals(stringValue(item.get("bundle_code")))) {
                continue;
            }
            item.put("build_status", "failed");
            item.put("build_error_message", trimToNull(exception.getMessage()));
        }
    }

    private Map<String, Object> requireAlevelImportJobRow(long jobId) {
        Map<String, Object> row = alevelImportJobMapper.findActiveImportJobById(jobId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入任务不存在");
        }
        String jobName = stringValue(column(row, "job_name"));
        if (!jobName.startsWith(JOB_NAME_PREFIX)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入任务不存在");
        }
        return row;
    }

    private Map<String, Object> serializeImportJob(Map<String, Object> row) {
        JSONObject resultPayload = parseObject(column(row, "result_json"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job_id", longValue(column(row, "exam_import_job_id")));
        result.put("job_name", column(row, "job_name"));
        result.put("batch_name", column(row, "bank_name"));
        result.put("source_mode", column(row, "source_mode"));
        result.put("status", column(row, "status"));
        result.put("uploaded_file_count", intValue(column(row, "uploaded_file_count")));
        result.put("resolved_file_count", intValue(column(row, "resolved_file_count")));
        result.put("bundle_count", intValue(resultPayload.get("bundle_count")));
        result.put("built_bundle_count", intValue(resultPayload.get("built_bundle_count")));
        result.put("success_count", intValue(column(row, "success_count")));
        result.put("failure_count", intValue(column(row, "failure_count")));
        result.put("question_count", intValue(resultPayload.get("question_count")));
        result.put("answer_count", intValue(resultPayload.get("answer_count")));
        result.put("question_ref_count", intValue(resultPayload.get("question_ref_count")));
        result.put("question_paper_count", intValue(resultPayload.get("question_paper_count")));
        result.put("mark_scheme_count", intValue(resultPayload.get("mark_scheme_count")));
        result.put("insert_count", intValue(resultPayload.get("insert_count")));
        result.put("exam_report_count", intValue(resultPayload.get("exam_report_count")));
        result.put("other_count", intValue(resultPayload.get("other_count")));
        result.put("progress_message", column(row, "progress_message"));
        result.put("error_message", column(row, "error_message"));
        result.put("start_time", timestamp(column(row, "start_time")));
        result.put("finish_time", timestamp(column(row, "finish_time")));
        result.put("create_time", timestamp(column(row, "create_time")));
        result.put("update_time", timestamp(column(row, "update_time")));
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
            return JSONUtil.parseObj(raw);
        } catch (Exception exception) {
            return new JSONObject();
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private void validateUploadedFiles(String sourceMode, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少上传一个文件");
        }
        for (MultipartFile file : files) {
            String filename = stringValue(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
            if ("zip".equals(sourceMode)) {
                if (!filename.endsWith(".zip")) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "压缩包模式仅支持 zip 文件");
                }
                continue;
            }
            if (!filename.endsWith(".pdf")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A-Level 原始文件导入仅支持 PDF 文件");
            }
        }
    }

    private String deriveBundleCodeFallback(String logicalPath) {
        String normalizedPath = normalizeImportPath(logicalPath);
        String stem = stripExtension(normalizedPath);
        stem = stripSourceTypeSuffix(normalizeClassifierText(stem));
        if (CharSequenceUtil.isBlank(stem)) {
            stem = normalizeClassifierText(normalizedPath);
        }
        return shorten(stem, 100);
    }

    private String stripSourceTypeSuffix(String stem) {
        String result = stem;
        result = result.replaceAll("-(question-paper)$", "");
        result = result.replaceAll("-(mark-scheme)$", "");
        result = result.replaceAll("-(insert)$", "");
        result = result.replaceAll("-(exam-report)$", "");
        result = result.replaceAll("-(examiners-report)$", "");
        result = result.replaceAll("-(examiner-report)$", "");
        if (CharSequenceUtil.isBlank(result)) {
            return stem;
        }
        return result;
    }

    private String buildParseResultJson(ResolvedPdfFile file, String sourceFileType, String bundleCode) {
        JSONObject result = new JSONObject();
        result.set("logical_path", file.logicalPath());
        result.set("origin_name", file.originName());
        result.set("bundle_code", bundleCode);
        result.set("source_file_type", sourceFileType);
        return result.toString();
    }

    private String buildParseWarningJson(String sourceFileType) {
        if (!"OTHER".equals(sourceFileType)) {
            return null;
        }
        JSONArray warnings = new JSONArray();
        warnings.add("未识别为 question-paper / mark-scheme / insert / exam-report，已按 OTHER 归类");
        return warnings.toString();
    }

    private Integer resolvePdfPageCount(byte[] rawBytes, String logicalPath) {
        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            return document.getNumberOfPages();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "解析 PDF 页数失败：" + logicalPath);
        }
    }

    private String buildStoragePath(String bundleCode, String logicalPath, String fileHash) {
        String fileName = fileName(logicalPath);
        String stem = stripExtension(fileName);
        String suffix = fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ? ".pdf" : "";
        String safeName = shorten(normalizeClassifierText(stem), 80);
        String safeBundleCode = shorten(normalizeClassifierText(bundleCode), 120);
        return normalizeImportPath("alevel/source-files/" + safeBundleCode + "/" + safeName + "_" + fileHash.substring(0, 12) + suffix);
    }

    private String writeExamAsset(String storagePath, byte[] rawBytes) {
        try {
            Path targetPath = examAssetRoot().resolve(storagePath);
            FileUtil.mkdir(targetPath.getParent().toFile());
            Files.write(targetPath, rawBytes);
            return "/exam-assets/" + storagePath.replace("\\", "/");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存来源 PDF 失败");
        }
    }

    private void emitProgress(
        ProgressSink progressSink,
        int resolvedFileCount,
        int bundleCount,
        int builtBundleCount,
        int successCount,
        int failureCount,
        int importedAssetCount,
        int questionCount,
        int answerCount,
        int questionRefCount,
        TypeCounts typeCounts,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures,
        String message
    ) {
        progressSink.accept(new Progress(
            resolvedFileCount,
            bundleCount,
            builtBundleCount,
            successCount,
            failureCount,
            importedAssetCount,
            questionCount,
            answerCount,
            questionRefCount,
            typeCounts,
            new ArrayList<>(items),
            new ArrayList<>(failures),
            message
        ));
    }

    private String progressMessage(int current, int total, int successCount, int failureCount) {
        return "正在处理第 " + current + " / " + total + " 个 PDF，已成功入库 " + successCount + " 个，失败 " + failureCount + " 个";
    }

    private Map<String, Object> failure(String sourceName, Exception exception) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("source_name", sourceName);
        failure.put("message", exception.getMessage());
        return failure;
    }

    private long generatedId(Map<String, Object> row, String message) {
        Object value = row.get("id");
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(message);
    }

    private Object column(Map<String, Object> row, String key) {
        return row.get(key);
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

    private String normalizeSourceMode(String sourceMode) {
        String normalized = stringValue(sourceMode).trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_MODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "导入模式不合法，仅支持 zip / directory / files");
        }
        return normalized;
    }

    private boolean isPdfName(String value) {
        return stringValue(value).toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String normalizeClassifierText(String value) {
        String normalized = stringValue(value).toLowerCase(Locale.ROOT).replace("\\", "/");
        normalized = normalized.replaceAll("[^a-z0-9/]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("(^-|-$)", "");
        return normalized;
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

    private String shorten(String value, int maxLength) {
        String text = stringValue(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 9)) + "_" + shortHash(text);
    }

    private String shortHash(String value) {
        return DigestUtil.md5Hex(stringValue(value)).substring(0, 8);
    }

    private String stripExtension(String value) {
        String text = stringValue(value);
        int index = text.lastIndexOf('.');
        return index < 0 ? text : text.substring(0, index);
    }

    private String fileName(String path) {
        String normalized = normalizeImportPath(path);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return CharSequenceUtil.isBlank(value) ? defaultValue : value;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private record ImportMetadata(String sourceMode, String batchName, String entryPathsJson, List<StoredImportFile> files) {
    }

    private record ResolvedPdfFile(String logicalPath, byte[] rawBytes, String originName) {
    }

    private record ImportItem(
        Long sourceFileId,
        String logicalPath,
        String sourceFileName,
        String sourceFileType,
        String bundleCode,
        String storagePath,
        String assetUrl,
        String parseStatus
    ) {
        Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source_file_id", sourceFileId);
            payload.put("relative_path", logicalPath);
            payload.put("source_file_name", sourceFileName);
            payload.put("source_file_type", sourceFileType);
            payload.put("bundle_code", bundleCode);
            payload.put("storage_path", storagePath);
            payload.put("asset_url", assetUrl);
            payload.put("parse_status", parseStatus);
            payload.put("import_status", "imported");
            return payload;
        }
    }

    private record Progress(
        int resolvedFileCount,
        int bundleCount,
        int builtBundleCount,
        int successCount,
        int failureCount,
        int importedAssetCount,
        int questionCount,
        int answerCount,
        int questionRefCount,
        TypeCounts typeCounts,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures,
        String message
    ) {
    }

    private record ImportResult(
        String sourceMode,
        String batchName,
        int uploadedFileCount,
        int resolvedFileCount,
        int bundleCount,
        int builtBundleCount,
        int successCount,
        int failureCount,
        int questionCount,
        int answerCount,
        int questionRefCount,
        TypeCounts typeCounts,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures
    ) {
    }

    @FunctionalInterface
    private interface ProgressSink {
        void accept(Progress progress);
    }

    private static class TypeCounts {
        private int questionPaperCount;
        private int markSchemeCount;
        private int insertCount;
        private int examReportCount;
        private int otherCount;

        private void increment(String sourceFileType) {
            switch (stringifyType(sourceFileType)) {
                case "QUESTION_PAPER" -> questionPaperCount++;
                case "MARK_SCHEME" -> markSchemeCount++;
                case "INSERT" -> insertCount++;
                case "EXAM_REPORT" -> examReportCount++;
                default -> otherCount++;
            }
        }

        private TypeCounts copy() {
            TypeCounts copy = new TypeCounts();
            copy.questionPaperCount = this.questionPaperCount;
            copy.markSchemeCount = this.markSchemeCount;
            copy.insertCount = this.insertCount;
            copy.examReportCount = this.examReportCount;
            copy.otherCount = this.otherCount;
            return copy;
        }

        private static String stringifyType(String sourceFileType) {
            return sourceFileType == null ? "" : sourceFileType;
        }
    }
}
