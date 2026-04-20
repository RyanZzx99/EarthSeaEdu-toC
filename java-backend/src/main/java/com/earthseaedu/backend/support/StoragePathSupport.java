package com.earthseaedu.backend.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

@Component
public class StoragePathSupport {

    private final EarthSeaProperties properties;

    public StoragePathSupport(EarthSeaProperties properties) {
        this.properties = properties;
    }

    public Path ensureExamAssetRoot() {
        Path path = resolveStoragePath(
            properties.getExamAssetRoot(),
            System.getenv("EXAM_ASSET_ROOT"),
            Paths.get("storage", "exam-assets")
        );
        FileUtil.mkdir(path.toFile());
        return path;
    }

    public Path ensureImportJobRoot() {
        Path path = resolveStoragePath(
            properties.getImportJobRoot(),
            System.getenv("IMPORT_JOB_ROOT"),
            Paths.get("storage", "import-jobs")
        );
        FileUtil.mkdir(path.toFile());
        return path;
    }

    private Path resolveStoragePath(String configuredValue, String envValue, Path defaultRelativePath) {
        String preferred = CharSequenceUtil.trim(configuredValue);
        if (CharSequenceUtil.isBlank(preferred)) {
            preferred = CharSequenceUtil.trim(envValue);
        }
        if (CharSequenceUtil.isNotBlank(preferred)) {
            return Paths.get(preferred).toAbsolutePath().normalize();
        }
        return defaultRelativePath.toAbsolutePath().normalize();
    }
}
