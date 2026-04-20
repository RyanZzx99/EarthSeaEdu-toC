package com.earthseaedu.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record SendSmsCodeRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @JsonProperty("biz_type")
        @NotBlank(message = "请输入业务类型")
        String bizType
    ) {
    }

    public record SmsInviteRequirementCheckRequest(
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    public record WechatBindInviteRequirementCheckRequest(
        @JsonProperty("bind_token")
        @NotBlank(message = "请输入 bind_token")
        String bindToken,
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    public record PasswordLoginRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入密码")
        String password
    ) {
    }

    public record TempRegisterLoginRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入密码")
        String password,
        @JsonProperty("invite_code")
        @NotBlank(message = "请输入邀请码")
        String inviteCode
    ) {
    }

    public record SmsLoginRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入验证码")
        String code,
        @JsonProperty("invite_code")
        String inviteCode
    ) {
    }

    public record WechatLoginRequest(
        @NotBlank(message = "缺少微信回调 code")
        String code,
        @NotBlank(message = "缺少微信回调 state")
        String state
    ) {
    }

    public record WechatInviteRegisterRequest(
        @JsonProperty("register_token")
        @NotBlank(message = "请输入 register_token")
        String registerToken,
        @JsonProperty("invite_code")
        @NotBlank(message = "请输入邀请码")
        String inviteCode
    ) {
    }

    public record WechatBindMobileRequest(
        @JsonProperty("bind_token")
        @NotBlank(message = "请输入 bind_token")
        String bindToken,
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入验证码")
        String code,
        @JsonProperty("invite_code")
        String inviteCode
    ) {
    }

    public record BindMyMobileRequest(
        @NotBlank(message = "请输入手机号")
        @Size(min = 11, max = 11, message = "请输入正确的手机号")
        String mobile
    ) {
    }

    public record TeacherPortalActivateRequest(
        @JsonProperty("invite_code")
        @NotBlank(message = "请输入教师邀请码")
        String inviteCode
    ) {
    }

    public record GenerateInviteCodesRequest(
        @NotNull(message = "请输入生成数量")
        @Min(value = 1, message = "生成数量至少为 1")
        @Max(value = 200, message = "生成数量不能超过 200")
        Integer count,
        @JsonProperty("expires_days")
        @Min(value = 1, message = "过期天数至少为 1")
        @Max(value = 3650, message = "过期天数不能超过 3650")
        Integer expiresDays,
        String note,
        @JsonProperty("invite_scene")
        String inviteScene
    ) {
    }

    public record IssueInviteCodeRequest(
        @NotBlank(message = "请输入邀请码")
        String code,
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    public record UpdateInviteCodeStatusRequest(
        @NotBlank(message = "请输入邀请码")
        String code,
        @NotBlank(message = "请输入目标状态")
        String status
    ) {
    }

    public record UpdateUserStatusRequest(
        @JsonProperty("user_id")
        String userId,
        String mobile,
        @NotBlank(message = "请输入用户状态")
        String status
    ) {
    }

    public record SetPasswordRequest(
        @JsonProperty("current_password")
        String currentPassword,
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    public record ResetPasswordBySmsRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入验证码")
        String code,
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    public record CheckPasswordAvailabilityRequest(
        @JsonProperty("current_password")
        String currentPassword,
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    public record CheckResetPasswordAvailabilityRequest(
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    public record UpdateNicknameRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = 100, message = "昵称长度不能超过 100 位")
        String nickname
    ) {
    }

    public record CheckNicknameAvailabilityRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = 100, message = "昵称长度不能超过 100 位")
        String nickname
    ) {
    }

    public record CreateNicknameRuleGroupRequest(
        @JsonProperty("group_code")
        @NotBlank(message = "group_code 不能为空")
        @Size(max = 50, message = "group_code 长度不能超过 50 位")
        String groupCode,
        @JsonProperty("group_name")
        @NotBlank(message = "group_name 不能为空")
        @Size(max = 100, message = "group_name 长度不能超过 100 位")
        String groupName,
        @JsonProperty("group_type")
        @NotBlank(message = "group_type 不能为空")
        @Size(max = 20, message = "group_type 长度不能超过 20 位")
        String groupType,
        @Size(max = 20, message = "scope 长度不能超过 20 位")
        String scope,
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status,
        @Min(value = 1, message = "priority 不能小于 1")
        @Max(value = 10000, message = "priority 不能大于 10000")
        Integer priority,
        @Size(max = 255, message = "description 长度不能超过 255 位")
        String description
    ) {
    }

    public record CreateNicknameWordRuleRequest(
        @JsonProperty("group_id")
        @NotNull(message = "group_id 不能为空")
        @Min(value = 1, message = "group_id 不能小于 1")
        Integer groupId,
        @NotBlank(message = "word 不能为空")
        @Size(max = 100, message = "word 长度不能超过 100 位")
        String word,
        @JsonProperty("match_type")
        @Size(max = 20, message = "match_type 长度不能超过 20 位")
        String matchType,
        @Size(max = 20, message = "decision 长度不能超过 20 位")
        String decision,
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status,
        @Min(value = 1, message = "priority 不能小于 1")
        @Max(value = 10000, message = "priority 不能大于 10000")
        Integer priority,
        @JsonProperty("risk_level")
        @Size(max = 20, message = "risk_level 长度不能超过 20 位")
        String riskLevel,
        @Size(max = 20, message = "source 长度不能超过 20 位")
        String source,
        @Size(max = 255, message = "note 长度不能超过 255 位")
        String note
    ) {
    }

    public record CreateNicknameContactPatternRequest(
        @JsonProperty("group_id")
        @Min(value = 1, message = "group_id 不能小于 1")
        Integer groupId,
        @JsonProperty("pattern_name")
        @NotBlank(message = "pattern_name 不能为空")
        @Size(max = 100, message = "pattern_name 长度不能超过 100 位")
        String patternName,
        @JsonProperty("pattern_type")
        @NotBlank(message = "pattern_type 不能为空")
        @Size(max = 20, message = "pattern_type 长度不能超过 20 位")
        String patternType,
        @JsonProperty("pattern_regex")
        @NotBlank(message = "pattern_regex 不能为空")
        @Size(max = 500, message = "pattern_regex 长度不能超过 500 位")
        String patternRegex,
        @Size(max = 20, message = "decision 长度不能超过 20 位")
        String decision,
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status,
        @Min(value = 1, message = "priority 不能小于 1")
        @Max(value = 10000, message = "priority 不能大于 10000")
        Integer priority,
        @JsonProperty("risk_level")
        @Size(max = 20, message = "risk_level 长度不能超过 20 位")
        String riskLevel,
        @JsonProperty("normalized_hint")
        @Size(max = 255, message = "normalized_hint 长度不能超过 255 位")
        String normalizedHint,
        @Size(max = 255, message = "note 长度不能超过 255 位")
        String note
    ) {
    }

    public record UpdateNicknameRuleTargetStatusRequest(
        @JsonProperty("target_type")
        @NotBlank(message = "target_type 不能为空")
        @Size(max = 20, message = "target_type 长度不能超过 20 位")
        String targetType,
        @JsonProperty("target_id")
        @NotNull(message = "target_id 不能为空")
        @Min(value = 1, message = "target_id 不能小于 1")
        Integer targetId,
        @NotBlank(message = "status 不能为空")
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status
    ) {
    }

    public record UpdateAiPromptConfigRequest(
        @JsonProperty("prompt_name")
        @NotBlank(message = "Prompt 名称不能为空")
        @Size(max = 200, message = "Prompt 名称长度不能超过 200 位")
        String promptName,
        @JsonProperty("prompt_content")
        @NotBlank(message = "Prompt 内容不能为空")
        String promptContent,
        @JsonProperty("prompt_version")
        @NotBlank(message = "Prompt 版本不能为空")
        @Size(max = 50, message = "Prompt 版本长度不能超过 50 位")
        String promptVersion,
        @NotBlank(message = "Prompt 状态不能为空")
        @Size(max = 20, message = "Prompt 状态长度不能超过 20 位")
        String status,
        @JsonProperty("output_format")
        @NotBlank(message = "输出格式不能为空")
        @Size(max = 30, message = "输出格式长度不能超过 30 位")
        String outputFormat,
        @JsonProperty("model_name")
        @Size(max = 100, message = "模型名称长度不能超过 100 位")
        String modelName,
        Double temperature,
        @JsonProperty("top_p")
        Double topP,
        @JsonProperty("max_tokens")
        @Min(value = 1, message = "max_tokens 不能小于 1")
        Integer maxTokens,
        @JsonProperty("variables_json")
        Object variablesJson,
        @Size(max = 500, message = "remark 长度不能超过 500 位")
        String remark
    ) {
        public boolean hasStructuredVariablesJson() {
            return variablesJson == null
                || variablesJson instanceof Map<?, ?>
                || variablesJson instanceof List<?>;
        }
    }

    public record UpdateAiRuntimeConfigRequest(
        @JsonProperty("config_value")
        String configValue,
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status,
        @Size(max = 500, message = "remark 长度不能超过 500 位")
        String remark,
        @JsonProperty("clear_override")
        Boolean clearOverride
    ) {
    }
}
