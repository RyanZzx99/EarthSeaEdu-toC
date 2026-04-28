package com.earthseaedu.backend.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface MockExamActService {

    Map<String, Object> getOptions();

    Map<String, Object> listPapers(String examContent);

    Map<String, Object> getPaper(long examPaperId);

    Map<String, Object> getPaper(long examPaperId, String examContent);

    Map<String, Object> createImportJob(
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<MultipartFile> files
    );

    Map<String, Object> getImportJobDetail(long jobId);
}
