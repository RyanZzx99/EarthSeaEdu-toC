package com.earthseaedu.backend.dto.aichat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** AI 建档接口请求体集合。 */
public final class AiChatRequests {

    private AiChatRequests() {
    }

    /** 保存官方档案表单的请求体。 */
    public record ArchiveFormSaveRequest(
        /**
         * 官方档案表单快照，必填。
         * 内容按业务档案表结构组织，由后端负责校验、归一化和持久化。
         */
        @NotNull
        @JsonProperty("archive_form")
        Map<String, Object> archiveForm
    ) {
    }
}
