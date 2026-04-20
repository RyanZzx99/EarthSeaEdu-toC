package com.earthseaedu.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record WechatAuthorizeUrlResponse(
        @JsonProperty("authorize_url")
        String authorizeUrl,
        String state
    ) {
    }

    public record InviteRequirementCheckResponse(
        @JsonProperty("need_invite_code")
        boolean needInviteCode,
        @JsonProperty("user_exists")
        boolean userExists,
        String message
    ) {
    }

    public record AvailabilityCheckResponse(boolean available, String message) {
    }

    public record LoginResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("user_id")
        String userId,
        String mobile
    ) {
    }

    public record WechatInviteRegisterRequiredResponse(
        @JsonProperty("next_step")
        String nextStep,
        @JsonProperty("register_token")
        String registerToken,
        String message
    ) {
    }

    public record UserProfileResponse(
        @JsonProperty("user_id")
        String userId,
        String mobile,
        String nickname,
        @JsonProperty("avatar_url")
        String avatarUrl,
        Integer sex,
        String province,
        String city,
        String country,
        String status,
        @JsonProperty("is_teacher")
        boolean isTeacher,
        @JsonProperty("has_password")
        boolean hasPassword
    ) {
    }

    public record NicknameResponse(
        @JsonProperty("user_id")
        String userId,
        String nickname
    ) {
    }

    public record TeacherPortalActivateResponse(
        String message,
        @JsonProperty("user_id")
        String userId,
        @JsonProperty("is_teacher")
        boolean isTeacher
    ) {
    }

    public record SimpleMessageResponse(String message) {
    }

    public record MobileBindResponse(
        String message,
        @JsonProperty("user_id")
        String userId,
        String mobile
    ) {
    }

    public record UserStatusResponse(
        @JsonProperty("user_id")
        String userId,
        String mobile,
        String status
    ) {
    }

    public record InviteCodeItem(
        String code,
        @JsonProperty("invite_scene")
        String inviteScene,
        String status,
        @JsonProperty("issued_to_mobile")
        String issuedToMobile,
        @JsonProperty("used_by_user_id")
        String usedByUserId,
        @JsonProperty("issued_time")
        LocalDateTime issuedTime,
        @JsonProperty("used_time")
        LocalDateTime usedTime,
        @JsonProperty("expires_time")
        LocalDateTime expiresTime,
        String note
    ) {
    }

    public record InviteCodeListResponse(long total, List<InviteCodeItem> items) {
    }

    public record InviteCodeItemsResponse(List<InviteCodeItem> items) {
    }
}
