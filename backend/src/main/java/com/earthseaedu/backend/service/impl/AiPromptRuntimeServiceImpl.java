package com.earthseaedu.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiPromptConfigMapper;
import com.earthseaedu.backend.mapper.AiRuntimeConfigMapper;
import com.earthseaedu.backend.service.AiPromptRuntimeService;
import com.earthseaedu.backend.service.AiPromptRuntimeService.PromptRuntimeResult;
import com.earthseaedu.backend.support.AiHttpSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * AiPromptRuntimeServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiPromptRuntimeServiceImpl implements AiPromptRuntimeService {

    private static final List<String> DEFAULT_ALLOWED_STATUSES = List.of("active");

    private final AiPromptConfigMapper aiPromptConfigMapper;
    private final AiRuntimeConfigMapper aiRuntimeConfigMapper;
    private final EarthSeaProperties properties;
    private final RestClient restClient;

    /**
     * 创建 AiPromptRuntimeServiceImpl 实例。
     */
    public AiPromptRuntimeServiceImpl(
        AiPromptConfigMapper aiPromptConfigMapper,
        AiRuntimeConfigMapper aiRuntimeConfigMapper,
        EarthSeaProperties properties
    ) {
        this.aiPromptConfigMapper = aiPromptConfigMapper;
        this.aiRuntimeConfigMapper = aiRuntimeConfigMapper;
        this.properties = properties;
        this.restClient = AiHttpSupport.createNonStreamRestClient(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PromptRuntimeResult executePrompt(String promptKey, Map<String, Object> context) {
        return executePrompt(promptKey, context, DEFAULT_ALLOWED_STATUSES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PromptRuntimeResult executePrompt(
        String promptKey,
        Map<String, Object> context,
        Collection<String> allowedStatuses
    ) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        List<String> statuses = normalizeAllowedStatuses(allowedStatuses);
        Map<String, Object> prompt = loadPromptConfig(promptKey, statuses);

        String baseUrl = trimToNull(effectiveRuntimeValue("AI_MODEL_BASE_URL", properties.getAiRuntime().getModelBaseUrl()));
        String apiKey = trimToNull(effectiveRuntimeValue("AI_MODEL_API_KEY", properties.getAiRuntime().getModelApiKey()));
        String model = blankToDefault(
            stringValue(column(prompt, "model_name")),
            effectiveRuntimeValue("AI_MODEL_DEFAULT_NAME", properties.getAiRuntime().getModelDefaultName())
        );
        if (baseUrl == null || apiKey == null || CharSequenceUtil.isBlank(model)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime config is incomplete");
        }

        String promptContent = renderPrompt(stringValue(column(prompt, "prompt_content")), safeContext);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put(
            "temperature",
            column(prompt, "temperature") == null
                ? properties.getAiRuntime().getModelDefaultTemperature()
                : doubleValue(column(prompt, "temperature"))
        );
        if (column(prompt, "top_p") != null) {
            requestBody.put("top_p", doubleValue(column(prompt, "top_p")));
        }
        if (column(prompt, "max_tokens") != null) {
            requestBody.put("max_tokens", intValue(column(prompt, "max_tokens")));
        }
        requestBody.put("messages", buildMessages(prompt, promptContent, safeContext));

        String rawResponse = AiHttpSupport.postJsonWithRetry(
            restClient,
            properties,
            resolveChatCompletionsUrl(baseUrl),
            apiKey,
            requestBody
        );
        String content = extractContent(rawResponse);
        if (CharSequenceUtil.isBlank(content)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI runtime returned an empty response");
        }
        return new PromptRuntimeResult(promptKey, model, content, rawResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> parseJsonObject(String rawText, String sceneName) {
        String cleaned = stripJsonFence(CharSequenceUtil.trimToEmpty(rawText));
        if (CharSequenceUtil.isBlank(cleaned)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, sceneName + " result is empty");
        }

        Map<String, Object> parsed = tryParseJsonObject(cleaned);
        if (parsed != null) {
            return parsed;
        }

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            parsed = tryParseJsonObject(cleaned.substring(firstBrace, lastBrace + 1));
            if (parsed != null) {
                return parsed;
            }
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, sceneName + " result is not valid JSON");
    }

    private Map<String, Object> loadPromptConfig(String promptKey, List<String> allowedStatuses) {
        Map<String, Object> row = aiPromptConfigMapper.findRuntimePromptConfig(promptKey, allowedStatuses);
        if (row == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI prompt config not found: " + promptKey);
        }
        return row;
    }

    private List<String> normalizeAllowedStatuses(Collection<String> allowedStatuses) {
        if (CollUtil.isEmpty(allowedStatuses)) {
            return DEFAULT_ALLOWED_STATUSES;
        }
        List<String> statuses = new ArrayList<>();
        for (String status : allowedStatuses) {
            String normalized = CharSequenceUtil.trim(status);
            if (CharSequenceUtil.isNotBlank(normalized) && !statuses.contains(normalized)) {
                statuses.add(normalized);
            }
        }
        return statuses.isEmpty() ? DEFAULT_ALLOWED_STATUSES : statuses;
    }

    private List<Map<String, String>> buildMessages(
        Map<String, Object> prompt,
        String promptContent,
        Map<String, Object> context
    ) {
        String role = blankToDefault(stringValue(column(prompt, "prompt_role")), "system");
        String systemContent = "You are a careful admissions profile assistant. Follow the prompt exactly. Return valid JSON when the prompt asks for JSON.";
        if ("system".equals(role) || "developer".equals(role)) {
            systemContent = promptContent;
        }

        String userContent = ("user_template".equals(role) ? promptContent : "")
            + "\n\nContext JSON:\n"
            + JSONUtil.toJsonStr(context);
        return List.of(
            Map.of("role", "system", "content", systemContent),
            Map.of("role", "user", "content", userContent)
        );
    }

    private String extractContent(String rawResponse) {
        Object parsed = parseJson(rawResponse);
        if (!(parsed instanceof Map<?, ?> responseMap)) {
            return "";
        }
        Object choicesValue = responseMap.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            return "";
        }
        Object messageValue = choice.get("message");
        if (messageValue instanceof Map<?, ?> message) {
            return stringValue(message.get("content"));
        }
        return stringValue(choice.get("text"));
    }

    private String effectiveRuntimeValue(String configKey, String defaultValue) {
        Map<String, Object> row = aiRuntimeConfigMapper.findLatestRuntimeConfig(configKey);
        if (row != null && "active".equals(stringValue(column(row, "status")))
            && CharSequenceUtil.isNotBlank(stringValue(column(row, "config_value")))) {
            return stringValue(column(row, "config_value"));
        }
        return defaultValue;
    }

    private String renderPrompt(String promptContent, Map<String, Object> context) {
        String rendered = blankToDefault(promptContent, "");
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            String value = stringValue(entry.getValue());
            rendered = rendered.replace("{{" + key + "}}", value);
            rendered = rendered.replace("{" + key + "}", value);
        }
        return rendered;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = CharSequenceUtil.trimToEmpty(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }

    private Map<String, Object> tryParseJsonObject(String rawJson) {
        try {
            Object parsed = JSONUtil.parse(rawJson);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject.toBean(LinkedHashMap.class);
            }
            if (parsed instanceof Map<?, ?> map) {
                return safeMap(map);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Object parseJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?> || value instanceof JSONObject || value instanceof JSONArray) {
            return value;
        }
        String text = trimToNull(stringValue(value));
        if (text == null) {
            return null;
        }
        try {
            Object parsed = JSONUtil.parse(text);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject.toBean(LinkedHashMap.class);
            }
            if (parsed instanceof JSONArray jsonArray) {
                return jsonArray.toList(Object.class);
            }
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripJsonFence(String value) {
        String cleaned = value;
        if (!cleaned.startsWith("```")) {
            return cleaned;
        }
        int firstLineEnd = cleaned.indexOf('\n');
        if (firstLineEnd >= 0) {
            cleaned = cleaned.substring(firstLineEnd + 1);
        } else {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private Map<String, Object> safeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String blankToDefault(String value, String defaultValue) {
        return CharSequenceUtil.isBlank(value) ? defaultValue : value;
    }

    private Object column(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        return row.get(snakeToCamel(columnName));
    }

    private String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private String trimToNull(String value) {
        String trimmed = CharSequenceUtil.trim(value);
        return CharSequenceUtil.isBlank(trimmed) ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(CharSequenceUtil.trimToEmpty(stringValue(value)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(CharSequenceUtil.trimToEmpty(stringValue(value)));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

}
