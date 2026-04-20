package com.earthseaedu.backend.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiPromptConfigMapper;
import com.earthseaedu.backend.mapper.AiRuntimeConfigMapper;
import com.earthseaedu.backend.model.ai.AiPromptConfig;
import com.earthseaedu.backend.model.ai.AiRuntimeConfig;
import com.earthseaedu.backend.support.PageResult;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConfigAdminService {

    private static final String ADMIN_CONSOLE = "admin_console";
    private static final List<AiRuntimeConfigSpec> AI_RUNTIME_CONFIG_SPECS = List.of(
        new AiRuntimeConfigSpec(
            "AI_MODEL_BASE_URL",
            "ai_model",
            "模型基础地址",
            "url",
            0,
            10,
            "为空时回退到 backend/.env 的 AI_MODEL_BASE_URL"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_API_KEY",
            "ai_model",
            "模型 API Key",
            "secret",
            1,
            20,
            "为空时回退到 backend/.env 的 AI_MODEL_API_KEY"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_DEFAULT_NAME",
            "ai_model",
            "默认模型名称",
            "model_name",
            0,
            30,
            "为空时回退到 backend/.env 的 AI_MODEL_DEFAULT_NAME"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_CONNECT_TIMEOUT_SECONDS",
            "ai_model",
            "连接超时（秒）",
            "float",
            0,
            40,
            "为空时回退到 backend/.env 的 AI_MODEL_CONNECT_TIMEOUT_SECONDS"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_READ_TIMEOUT_SECONDS",
            "ai_model",
            "非流式读取超时（秒）",
            "float",
            0,
            50,
            "为空时回退到 backend/.env 的 AI_MODEL_READ_TIMEOUT_SECONDS"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_STREAM_READ_TIMEOUT_SECONDS",
            "ai_model",
            "流式读取超时（秒）",
            "float",
            0,
            60,
            "为空时回退到 backend/.env 的 AI_MODEL_STREAM_READ_TIMEOUT_SECONDS"
        ),
        new AiRuntimeConfigSpec(
            "AI_MODEL_DEFAULT_TEMPERATURE",
            "ai_model",
            "默认 Temperature",
            "float",
            0,
            70,
            "为空时回退到 backend/.env 的 AI_MODEL_DEFAULT_TEMPERATURE"
        )
    );

    private final AiPromptConfigMapper aiPromptConfigMapper;
    private final AiRuntimeConfigMapper aiRuntimeConfigMapper;
    private final EarthSeaProperties properties;

    public AiConfigAdminService(
        AiPromptConfigMapper aiPromptConfigMapper,
        AiRuntimeConfigMapper aiRuntimeConfigMapper,
        EarthSeaProperties properties
    ) {
        this.aiPromptConfigMapper = aiPromptConfigMapper;
        this.aiRuntimeConfigMapper = aiRuntimeConfigMapper;
        this.properties = properties;
    }

    public PageResult<AiPromptConfig> listAiPromptConfigs(
        String bizDomain,
        String promptStage,
        String status,
        String keyword,
        int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AiPromptConfig> rows = aiPromptConfigMapper.list(
            blankToNull(bizDomain),
            blankToNull(promptStage),
            blankToNull(status),
            blankToNull(keyword),
            safeLimit
        );
        long total = aiPromptConfigMapper.count(
            blankToNull(bizDomain),
            blankToNull(promptStage),
            blankToNull(status),
            blankToNull(keyword)
        );
        return new PageResult<>(total, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiPromptConfig updateAiPromptConfig(
        long promptId,
        String promptName,
        String promptContent,
        String promptVersion,
        String status,
        String outputFormat,
        String modelName,
        Double temperature,
        Double topP,
        Integer maxTokens,
        Object variablesJson,
        String remark
    ) {
        AiPromptConfig row = aiPromptConfigMapper.findActiveById(promptId);
        if (row == null) {
            throw badRequest("Prompt 配置不存在");
        }

        String normalizedName = requiredTrimmed(promptName, "Prompt 名称不能为空");
        String normalizedContent = requiredTrimmed(promptContent, "Prompt 内容不能为空");
        String normalizedVersion = requiredTrimmed(promptVersion, "Prompt 版本不能为空");
        String normalizedStatus = requiredTrimmed(status, "Prompt 状态不能为空");
        String normalizedOutputFormat = requiredTrimmed(outputFormat, "输出格式不能为空");

        row.setPromptName(normalizedName);
        row.setPromptContent(normalizedContent);
        row.setPromptVersion(normalizedVersion);
        row.setStatus(normalizedStatus);
        row.setOutputFormat(normalizedOutputFormat);
        row.setModelName(trimToNull(modelName));
        row.setTemperature(temperature);
        row.setTopP(topP);
        row.setMaxTokens(maxTokens);
        row.setVariablesJson(serializeVariablesJson(variablesJson));
        row.setRemark(trimToNull(remark));
        row.setUpdatedBy(ADMIN_CONSOLE);
        row.setUpdateTime(nowUtc());
        aiPromptConfigMapper.update(row);
        return aiPromptConfigMapper.findActiveById(promptId);
    }

    public Map<String, Object> toAiPromptListPayload(AiPromptConfig row) {
        return toAiPromptPayload(row, true);
    }

    public Map<String, Object> toAiPromptUpdatePayload(AiPromptConfig row) {
        return toAiPromptPayload(row, false);
    }

    public List<Map<String, Object>> listAiRuntimeConfigs() {
        List<AiRuntimeConfig> rows = aiRuntimeConfigMapper.listActive();
        Map<String, AiRuntimeConfig> rowMap = new LinkedHashMap<>();
        for (AiRuntimeConfig row : rows) {
            rowMap.put(row.getConfigKey(), row);
        }

        Map<String, Object> defaults = getDefaultAiRuntimeConfigMap();
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiRuntimeConfigSpec spec : AI_RUNTIME_CONFIG_SPECS) {
            items.add(serializeAiRuntimeConfigRow(rowMap.get(spec.configKey()), spec.configKey(), spec, defaults.get(spec.configKey())));
        }

        Set<String> knownKeys = new LinkedHashSet<>();
        for (AiRuntimeConfigSpec spec : AI_RUNTIME_CONFIG_SPECS) {
            knownKeys.add(spec.configKey());
        }
        for (AiRuntimeConfig row : rows) {
            if (knownKeys.contains(row.getConfigKey())) {
                continue;
            }
            items.add(
                serializeAiRuntimeConfigRow(
                    row,
                    row.getConfigKey(),
                    new AiRuntimeConfigSpec(
                        row.getConfigKey(),
                        row.getConfigGroup(),
                        row.getConfigName(),
                        row.getValueType(),
                        row.getIsSecret() == null ? 0 : row.getIsSecret(),
                        row.getSortOrder() == null ? 100 : row.getSortOrder(),
                        row.getRemark()
                    ),
                    null
                )
            );
        }
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateAiRuntimeConfig(
        String configKey,
        String configValue,
        String status,
        String remark,
        boolean clearOverride
    ) {
        String normalizedKey = requiredTrimmed(configKey, "运行时配置键不存在");
        AiRuntimeConfigSpec spec = findSpec(normalizedKey);
        if (spec == null) {
            throw badRequest("运行时配置键不存在");
        }

        String normalizedStatus = defaultTrimmed(status, "active");
        if (!"active".equals(normalizedStatus) && !"disabled".equals(normalizedStatus)) {
            throw badRequest("运行时配置状态仅支持 active 或 disabled");
        }

        LocalDateTime now = nowUtc();
        AiRuntimeConfig row = aiRuntimeConfigMapper.findActiveByConfigKey(normalizedKey);
        boolean isNew = row == null;
        if (isNew) {
            row = new AiRuntimeConfig();
            row.setConfigKey(normalizedKey);
            row.setCreatedBy(ADMIN_CONSOLE);
            row.setCreateTime(now);
            row.setDeleteFlag("1");
        }

        row.setConfigGroup(spec.configGroup());
        row.setConfigName(spec.configName());
        row.setValueType(spec.valueType());
        row.setIsSecret(spec.isSecret());
        row.setStatus(normalizedStatus);
        row.setSortOrder(spec.sortOrder());
        row.setUpdatedBy(ADMIN_CONSOLE);
        row.setUpdateTime(now);
        if (CharSequenceUtil.isNotBlank(remark)) {
            row.setRemark(remark.trim());
        } else if (row.getRemark() == null) {
            row.setRemark(spec.remark());
        }

        if (clearOverride) {
            row.setConfigValue(null);
        } else if (configValue != null) {
            String normalizedValue = configValue.trim();
            if (CharSequenceUtil.isNotBlank(normalizedValue)) {
                row.setConfigValue(normalizedValue);
            }
        }

        if (isNew) {
            aiRuntimeConfigMapper.insert(row);
        } else {
            aiRuntimeConfigMapper.update(row);
        }

        AiRuntimeConfig latest = aiRuntimeConfigMapper.findActiveByConfigKey(normalizedKey);
        return serializeAiRuntimeConfigRow(
            latest,
            normalizedKey,
            spec,
            getDefaultAiRuntimeConfigMap().get(normalizedKey)
        );
    }

    private Map<String, Object> toAiPromptPayload(AiPromptConfig row, boolean includeCreatedFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("prompt_key", row.getPromptKey());
        payload.put("prompt_name", row.getPromptName());
        payload.put("biz_domain", row.getBizDomain());
        payload.put("prompt_role", row.getPromptRole());
        payload.put("prompt_stage", row.getPromptStage());
        payload.put("prompt_content", row.getPromptContent());
        payload.put("prompt_version", row.getPromptVersion());
        payload.put("status", row.getStatus());
        payload.put("model_name", row.getModelName());
        payload.put("temperature", row.getTemperature());
        payload.put("top_p", row.getTopP());
        payload.put("max_tokens", row.getMaxTokens());
        payload.put("output_format", row.getOutputFormat());
        payload.put("variables_json", parseJson(row.getVariablesJson()));
        payload.put("remark", row.getRemark());
        if (includeCreatedFields) {
            payload.put("created_by", row.getCreatedBy());
            payload.put("updated_by", row.getUpdatedBy());
            payload.put("create_time", row.getCreateTime());
            payload.put("update_time", row.getUpdateTime());
        } else {
            payload.put("updated_by", row.getUpdatedBy());
            payload.put("update_time", row.getUpdateTime());
        }
        return payload;
    }

    private Map<String, Object> serializeAiRuntimeConfigRow(
        AiRuntimeConfig row,
        String configKey,
        AiRuntimeConfigSpec spec,
        Object defaultValue
    ) {
        String rowStatus = row == null ? "active" : row.getStatus();
        String rawOverrideValue = normalizeDbOverrideValue(row == null ? null : row.getConfigValue());
        boolean hasOverride = CharSequenceUtil.isNotBlank(rawOverrideValue);
        boolean usingDefault = !hasOverride || !"active".equals(rowStatus);
        Object effectiveSourceValue = hasOverride && "active".equals(rowStatus) ? rawOverrideValue : defaultValue;
        boolean isSecret = ((row == null ? spec.isSecret() : row.getIsSecret()) != null)
            && (row == null ? spec.isSecret() : row.getIsSecret()) == 1;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row == null ? null : row.getId());
        payload.put("config_group", row == null ? spec.configGroup() : row.getConfigGroup());
        payload.put("config_key", configKey);
        payload.put("config_name", row == null ? spec.configName() : row.getConfigName());
        payload.put("config_value", isSecret ? "" : (rawOverrideValue == null ? "" : rawOverrideValue));
        payload.put("effective_value_display", formatRuntimeValueForDisplay(effectiveSourceValue, isSecret));
        payload.put("default_value_display", formatRuntimeValueForDisplay(defaultValue, isSecret));
        payload.put("value_type", row == null ? spec.valueType() : row.getValueType());
        payload.put("is_secret", isSecret ? 1 : 0);
        payload.put("status", rowStatus);
        payload.put("sort_order", row == null ? spec.sortOrder() : row.getSortOrder());
        payload.put("remark", row != null && row.getRemark() != null ? row.getRemark() : spec.remark());
        payload.put("has_override", hasOverride);
        payload.put("using_default", usingDefault);
        payload.put("update_time", row == null ? null : row.getUpdateTime());
        return payload;
    }

    private Map<String, Object> getDefaultAiRuntimeConfigMap() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("AI_MODEL_BASE_URL", properties.getAiRuntime().getModelBaseUrl());
        defaults.put("AI_MODEL_API_KEY", properties.getAiRuntime().getModelApiKey());
        defaults.put("AI_MODEL_DEFAULT_NAME", properties.getAiRuntime().getModelDefaultName());
        defaults.put("AI_MODEL_CONNECT_TIMEOUT_SECONDS", properties.getAiRuntime().getModelConnectTimeoutSeconds());
        defaults.put("AI_MODEL_READ_TIMEOUT_SECONDS", properties.getAiRuntime().getModelReadTimeoutSeconds());
        defaults.put("AI_MODEL_STREAM_READ_TIMEOUT_SECONDS", properties.getAiRuntime().getModelStreamReadTimeoutSeconds());
        defaults.put("AI_MODEL_DEFAULT_TEMPERATURE", properties.getAiRuntime().getModelDefaultTemperature());
        return defaults;
    }

    private AiRuntimeConfigSpec findSpec(String configKey) {
        for (AiRuntimeConfigSpec spec : AI_RUNTIME_CONFIG_SPECS) {
            if (spec.configKey().equals(configKey)) {
                return spec;
            }
        }
        return null;
    }

    private String serializeVariablesJson(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            throw badRequest("variables_json 必须是 JSON 对象、数组或空值");
        }
        return JSONUtil.toJsonStr(value);
    }

    private Object parseJson(String text) {
        if (CharSequenceUtil.isBlank(text)) {
            return null;
        }
        return JSONUtil.parse(text);
    }

    private String normalizeDbOverrideValue(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized;
    }

    private String formatRuntimeValueForDisplay(Object value, boolean isSecret) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return isSecret ? maskSecretValue(text) : text;
    }

    private String maskSecretValue(String value) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        if (CharSequenceUtil.isBlank(normalized)) {
            return "";
        }
        if (normalized.length() <= 8) {
            return "*".repeat(normalized.length());
        }
        return normalized.substring(0, 4)
            + "*".repeat(normalized.length() - 8)
            + normalized.substring(normalized.length() - 4);
    }

    private String requiredTrimmed(String value, String message) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        if (CharSequenceUtil.isBlank(normalized)) {
            throw badRequest(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(normalized) ? null : normalized;
    }

    private String blankToNull(String value) {
        return trimToNull(value);
    }

    private String defaultTrimmed(String value, String defaultValue) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(normalized) ? defaultValue : normalized;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record AiRuntimeConfigSpec(
        String configKey,
        String configGroup,
        String configName,
        String valueType,
        Integer isSecret,
        Integer sortOrder,
        String remark
    ) {
    }
}
