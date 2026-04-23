package com.earthseaedu.backend.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.service.AiChatRealtimeService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AiChatWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIMEOUT_MILLIS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final AiChatRealtimeService aiChatRealtimeService;
    private final Map<String, AiChatRealtimeService.ConnectionContext> connectionContexts = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentWebSocketSessionDecorator> decoratedSessions = new ConcurrentHashMap<>();

    public AiChatWebSocketHandler(AiChatRealtimeService aiChatRealtimeService) {
        this.aiChatRealtimeService = aiChatRealtimeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        decoratedSessions.put(
            session.getId(),
            new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MILLIS, BUFFER_SIZE_LIMIT)
        );
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> event;
        try {
            event = parseEvent(message.getPayload());
        } catch (RuntimeException exception) {
            sendError(session, null, null, "invalid websocket message");
            return;
        }

        String eventType = stringValue(event.get("type"));
        String requestId = stringValue(event.get("request_id"));
        String eventSessionId = stringValue(event.get("session_id"));
        Map<String, Object> payload = toMutableMap(event.get("payload"));

        try {
            if ("connect_init".equals(eventType)) {
                handleConnectInit(session, requestId, eventSessionId, payload);
                return;
            }
            if ("ping".equals(eventType)) {
                sendEvent(session, "pong", requestId, eventSessionId, Map.of());
                return;
            }
            if ("user_message".equals(eventType)) {
                handleUserMessage(session, requestId, payload);
                return;
            }
            sendError(session, requestId, eventSessionId, "unsupported websocket event: " + eventType);
        } catch (ApiException exception) {
            sendError(session, requestId, eventSessionId, exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(session, requestId, eventSessionId, "AI chat processing failed: " + exception.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        connectionContexts.remove(session.getId());
        decoratedSessions.remove(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR.withReason(trimReason("AI Chat WebSocket transport error")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connectionContexts.remove(session.getId());
        decoratedSessions.remove(session.getId());
    }

    private void handleConnectInit(
        WebSocketSession session,
        String requestId,
        String eventSessionId,
        Map<String, Object> payload
    ) {
        String accessToken = stringValue(payload.get("access_token"));
        String requestedSessionId = StrUtil.blankToDefault(stringValue(payload.get("session_id")), eventSessionId);
        AiChatRealtimeService.ConnectionInitResult initResult = aiChatRealtimeService.connect(
            accessToken,
            requestedSessionId,
            stringValue(payload.get("student_id")),
            stringValue(payload.get("biz_domain"))
        );
        connectionContexts.put(session.getId(), initResult.context());

        Map<String, Object> responsePayload = new LinkedHashMap<>();
        responsePayload.put("current_stage", initResult.currentStage());
        responsePayload.put("current_round", initResult.currentRound());
        responsePayload.put("missing_dimensions", initResult.missingDimensions());
        sendEvent(session, "connect_ack", requestId, initResult.context().sessionId(), responsePayload);
    }

    private void handleUserMessage(WebSocketSession session, String requestId, Map<String, Object> payload) {
        AiChatRealtimeService.ConnectionContext context = connectionContexts.get(session.getId());
        if (context == null) {
            sendError(session, requestId, null, "AI chat websocket is not initialized");
            return;
        }

        String content = stringValue(payload.get("content"));
        AiChatRealtimeService.SavedMessage userMessage = aiChatRealtimeService.saveUserMessage(context, content);
        sendEvent(
            session,
            "user_message_saved",
            requestId,
            context.sessionId(),
            Map.of(
                "message_id", userMessage.messageId(),
                "sequence_no", userMessage.sequenceNo(),
                "current_round", userMessage.currentRound()
            )
        );
        sendEvent(
            session,
            "stage_changed",
            requestId,
            context.sessionId(),
            aiChatRealtimeService.setStage(context, "conversation", "generating_assistant")
        );

        String assistantContent;
        try {
            assistantContent = aiChatRealtimeService.generateAssistant(context, content);
        } catch (RuntimeException exception) {
            sendEvent(
                session,
                "stage_changed",
                requestId,
                context.sessionId(),
                aiChatRealtimeService.setStage(context, "conversation", "ready_for_input")
            );
            throw exception;
        }
        List<String> chunks = splitChunks(assistantContent, 80);
        AiChatRealtimeService.SavedMessage assistantMessage = aiChatRealtimeService.saveAssistantMessage(
            context,
            assistantContent,
            chunks.size()
        );
        StringBuilder accumulated = new StringBuilder();
        for (String chunk : chunks) {
            accumulated.append(chunk);
            sendEvent(
                session,
                "assistant_token",
                requestId,
                context.sessionId(),
                Map.of(
                    "delta_text", chunk,
                    "accumulated_text", accumulated.toString()
                )
            );
        }
        sendEvent(
            session,
            "assistant_done",
            requestId,
            context.sessionId(),
            Map.of(
                "message_id", assistantMessage.messageId(),
                "content", assistantContent
            )
        );

        Map<String, Object> progressHint = new LinkedHashMap<>();
        try {
            Map<String, Object> patchResult = aiChatRealtimeService.extractLatestPatch(context);
            progressHint = toMutableMap(patchResult.get("progress_hint"));
            if (!progressHint.isEmpty()) {
                sendEvent(session, "progress_updated", requestId, context.sessionId(), progressHint);
            }
        } catch (RuntimeException exception) {
            sendError(session, requestId, context.sessionId(), "draft patch extraction failed: " + exception.getMessage());
        }
        boolean stopReady = Boolean.TRUE.equals(progressHint.get("stop_ready"));
        sendEvent(
            session,
            "stage_changed",
            requestId,
            context.sessionId(),
            aiChatRealtimeService.setStage(context, stopReady ? "build_ready" : "conversation", stopReady ? null : "ready_for_input")
        );
    }

    private void sendError(WebSocketSession session, String requestId, String sessionId, String message) {
        sendEvent(
            session,
            "error",
            requestId,
            sessionId,
            Map.of("message", StrUtil.blankToDefault(message, "AI chat websocket error"))
        );
    }

    private void sendEvent(
        WebSocketSession session,
        String type,
        String requestId,
        String sessionId,
        Map<String, Object> payload
    ) {
        ConcurrentWebSocketSessionDecorator decorated = decoratedSessions.get(session.getId());
        if (decorated == null || !decorated.isOpen()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        if (StrUtil.isNotBlank(requestId)) {
            event.put("request_id", requestId);
        }
        if (StrUtil.isNotBlank(sessionId)) {
            event.put("session_id", sessionId);
        }
        event.put("payload", payload == null ? Map.of() : payload);
        try {
            decorated.sendMessage(new TextMessage(JSONUtil.toJsonStr(event)));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> parseEvent(String payload) {
        Object parsed = JSONUtil.parse(payload);
        if (parsed instanceof JSONObject jsonObject) {
            return jsonObject.toBean(LinkedHashMap.class);
        }
        if (parsed instanceof Map<?, ?> map) {
            return toMutableMap(map);
        }
        throw new IllegalArgumentException("websocket message must be object");
    }

    private List<String> splitChunks(String content, int chunkSize) {
        String safeContent = StrUtil.blankToDefault(content, "");
        if (safeContent.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < safeContent.length(); index += chunkSize) {
            chunks.add(safeContent.substring(index, Math.min(index + chunkSize, safeContent.length())));
        }
        return chunks;
    }

    private Map<String, Object> toMutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() <= 120 ? reason : reason.substring(0, 120);
    }
}
