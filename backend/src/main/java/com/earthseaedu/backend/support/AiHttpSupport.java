package com.earthseaedu.backend.support;

import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class AiHttpSupport {

    private AiHttpSupport() {
    }

    public static RestClient createNonStreamRestClient(EarthSeaProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(secondsToMillis(properties.getAiRuntime().getModelConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(secondsToMillis(properties.getAiRuntime().getModelReadTimeoutSeconds()));
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    public static String postJsonWithRetry(
        RestClient restClient,
        EarthSeaProperties properties,
        String url,
        String apiKey,
        Map<String, Object> requestBody
    ) {
        int retryCount = Math.max(0, properties.getAiRuntime().getModelNonStreamRetryCount());
        double backoffSeconds = Math.max(0.0, properties.getAiRuntime().getModelNonStreamRetryBackoffSeconds());

        for (int attemptIndex = 0; attemptIndex <= retryCount; attemptIndex++) {
            try {
                return restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            } catch (RestClientResponseException exception) {
                if (attemptIndex < retryCount && shouldRetryStatus(exception.getStatusCode().value())) {
                    sleepBeforeRetry(backoffSeconds);
                    continue;
                }
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI runtime request failed with status "
                        + exception.getStatusCode().value()
                        + ": "
                        + abbreviate(exception.getResponseBodyAsString())
                );
            } catch (ResourceAccessException exception) {
                if (attemptIndex < retryCount) {
                    sleepBeforeRetry(backoffSeconds);
                    continue;
                }
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime request failed: " + exception.getMessage());
            } catch (RestClientException exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime request failed: " + exception.getMessage());
            }
        }

        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime request failed after retry");
    }

    private static boolean shouldRetryStatus(int statusCode) {
        return statusCode >= 500 || statusCode == 408 || statusCode == 429;
    }

    private static void sleepBeforeRetry(double backoffSeconds) {
        long millis = Math.round(backoffSeconds * 1000.0);
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime retry interrupted");
        }
    }

    private static int secondsToMillis(int seconds) {
        long millis = Math.max(1L, seconds) * 1000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
