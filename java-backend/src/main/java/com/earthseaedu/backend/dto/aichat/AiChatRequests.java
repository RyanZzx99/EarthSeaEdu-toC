package com.earthseaedu.backend.dto.aichat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public final class AiChatRequests {

    private AiChatRequests() {
    }

    public record ArchiveFormSaveRequest(
        @NotNull
        @JsonProperty("archive_form")
        Map<String, Object> archiveForm
    ) {
    }
}
