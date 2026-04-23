package com.earthseaedu.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** 认证、账号、后台管理相关请求 DTO 集合。 */
public final class AuthRequests {

    private AuthRequests() {
    }

    /**
     * 发送短信验证码请求。
     *
     * @param mobile 手机号，必填
     * @param bizType 业务类型，必填，允许 login 或 bind_mobile
     */
    public record SendSmsCodeRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @JsonProperty("biz_type")
        @NotBlank(message = "请输入业务类型")
        String bizType
    ) {
    }

    /**
     * 短信登录邀请码要求检查请求。
     *
     * @param mobile 手机号，必填
     */
    public record SmsInviteRequirementCheckRequest(
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    /**
     * 微信绑定手机号邀请码要求检查请求。
     *
     * @param bindToken 微信绑定凭证，必填
     * @param mobile 待绑定手机号，必填
     */
    public record WechatBindInviteRequirementCheckRequest(
        @JsonProperty("bind_token")
        @NotBlank(message = "请输入 bind_token")
        String bindToken,
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    /**
     * 密码登录请求。
     *
     * @param mobile 手机号，必填
     * @param password 密码，必填
     */
    public record PasswordLoginRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入密码")
        String password
    ) {
    }

    /**
     * 邀请码注册登录请求。
     *
     * @param mobile 手机号，必填
     * @param password 密码，必填
     * @param inviteCode 注册邀请码，必填
     */
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

    /**
     * 短信登录请求。
     *
     * @param mobile 手机号，必填
     * @param code 短信验证码，必填
     * @param inviteCode 注册邀请码，未注册手机号首次登录时必填
     */
    public record SmsLoginRequest(
        @NotBlank(message = "请输入手机号")
        String mobile,
        @NotBlank(message = "请输入验证码")
        String code,
        @JsonProperty("invite_code")
        String inviteCode
    ) {
    }

    /**
     * 微信扫码登录请求。
     *
     * @param code 微信回调 code，必填
     * @param state 微信回调 state，必填
     */
    public record WechatLoginRequest(
        @NotBlank(message = "缺少微信回调 code")
        String code,
        @NotBlank(message = "缺少微信回调 state")
        String state
    ) {
    }

    /**
     * 微信临时账号邀请码注册请求。
     *
     * @param registerToken 微信注册凭证，必填
     * @param inviteCode 注册邀请码，必填
     */
    public record WechatInviteRegisterRequest(
        @JsonProperty("register_token")
        @NotBlank(message = "请输入 register_token")
        String registerToken,
        @JsonProperty("invite_code")
        @NotBlank(message = "请输入邀请码")
        String inviteCode
    ) {
    }

    /**
     * 微信账号绑定手机号请求。
     *
     * @param bindToken 微信绑定凭证，必填
     * @param mobile 待绑定手机号，必填
     * @param code 短信验证码，必填
     * @param inviteCode 注册邀请码，绑定新手机号首次注册时必填
     */
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

    /**
     * 当前账号绑定手机号请求。
     *
     * @param mobile 手机号，必填，长度 11 位
     */
    public record BindMyMobileRequest(
        @NotBlank(message = "请输入手机号")
        @Size(min = 11, max = 11, message = "请输入正确的手机号")
        String mobile
    ) {
    }

    /**
     * 教师端激活请求。
     *
     * @param inviteCode 教师邀请码，必填
     */
    public record TeacherPortalActivateRequest(
        @JsonProperty("invite_code")
        @NotBlank(message = "请输入教师邀请码")
        String inviteCode
    ) {
    }

    /**
     * 批量生成邀请码请求。
     *
     * @param count 生成数量，必填，范围 1 到 200
     * @param expiresDays 过期天数，选填，范围 1 到 3650
     * @param note 备注，选填
     * @param inviteScene 邀请码用途，选填，允许 register 或 teacher_portal
     */
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

    /**
     * 发放邀请码请求。
     *
     * @param code 邀请码，必填
     * @param mobile 目标手机号，必填
     */
    public record IssueInviteCodeRequest(
        @NotBlank(message = "请输入邀请码")
        String code,
        @NotBlank(message = "请输入手机号")
        String mobile
    ) {
    }

    /**
     * 更新邀请码状态请求。
     *
     * @param code 邀请码，必填
     * @param status 目标状态，必填，允许 1、2、3
     */
    public record UpdateInviteCodeStatusRequest(
        @NotBlank(message = "请输入邀请码")
        String code,
        @NotBlank(message = "请输入目标状态")
        String status
    ) {
    }

    /**
     * 更新用户状态请求。
     *
     * @param userId 用户 ID，选填，和 mobile 至少提供一个
     * @param mobile 手机号，选填，和 userId 至少提供一个
     * @param status 目标状态，必填，允许 active 或 disabled
     */
    public record UpdateUserStatusRequest(
        @JsonProperty("user_id")
        String userId,
        String mobile,
        @NotBlank(message = "请输入用户状态")
        String status
    ) {
    }

    /**
     * 设置或修改密码请求。
     *
     * @param currentPassword 当前密码，已有密码时必填
     * @param newPassword 新密码，必填
     */
    public record SetPasswordRequest(
        @JsonProperty("current_password")
        String currentPassword,
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    /**
     * 短信重置密码请求。
     *
     * @param mobile 手机号，必填，必须与当前账号绑定手机号一致
     * @param code 短信验证码，必填
     * @param newPassword 新密码，必填
     */
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

    /**
     * 密码可用性检查请求。
     *
     * @param currentPassword 当前密码，选填
     * @param newPassword 新密码，必填
     */
    public record CheckPasswordAvailabilityRequest(
        @JsonProperty("current_password")
        String currentPassword,
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    /**
     * 重置密码可用性检查请求。
     *
     * @param newPassword 新密码，必填
     */
    public record CheckResetPasswordAvailabilityRequest(
        @JsonProperty("new_password")
        @NotBlank(message = "请输入新密码")
        String newPassword
    ) {
    }

    /**
     * 更新昵称请求。
     *
     * @param nickname 新昵称，必填，最长 100 个字符
     */
    public record UpdateNicknameRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = 100, message = "昵称长度不能超过 100 位")
        String nickname
    ) {
    }

    /**
     * 昵称可用性检查请求。
     *
     * @param nickname 待检查昵称，必填，最长 100 个字符
     */
    public record CheckNicknameAvailabilityRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = 100, message = "昵称长度不能超过 100 位")
        String nickname
    ) {
    }

    /**
     * 创建昵称规则分组请求。
     *
     * @param groupCode 分组编码，必填，最长 50 个字符
     * @param groupName 分组名称，必填，最长 100 个字符
     * @param groupType 分组类型，必填，最长 20 个字符
     * @param scope 作用域，选填，最长 20 个字符
     * @param status 状态，选填，最长 20 个字符
     * @param priority 优先级，选填，范围 1 到 10000
     * @param description 描述，选填，最长 255 个字符
     */
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

    /**
     * 创建昵称词条规则请求。
     *
     * @param groupId 分组 ID，必填，必须大于 0
     * @param word 词条，必填，最长 100 个字符
     * @param matchType 匹配方式，选填，最长 20 个字符
     * @param decision 命中决策，选填，最长 20 个字符
     * @param status 状态，选填，最长 20 个字符
     * @param priority 优先级，选填，范围 1 到 10000
     * @param riskLevel 风险等级，选填，最长 20 个字符
     * @param source 来源，选填，最长 20 个字符
     * @param note 备注，选填，最长 255 个字符
     */
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

    /**
     * 创建昵称联系方式规则请求。
     *
     * @param groupId 分组 ID，选填，提供时必须大于 0
     * @param patternName 规则名称，必填，最长 100 个字符
     * @param patternType 规则类型，必填，最长 20 个字符
     * @param patternRegex 匹配正则，必填，最长 500 个字符
     * @param decision 命中决策，选填，最长 20 个字符
     * @param status 状态，选填，最长 20 个字符
     * @param priority 优先级，选填，范围 1 到 10000
     * @param riskLevel 风险等级，选填，最长 20 个字符
     * @param normalizedHint 归一化提示，选填，最长 255 个字符
     * @param note 备注，选填，最长 255 个字符
     */
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

    /**
     * 更新昵称规则状态请求。
     *
     * @param targetType 目标类型，必填，允许 group、word、pattern
     * @param targetId 目标 ID，必填，必须大于 0
     * @param status 目标状态，必填，最长 20 个字符
     */
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

    /** 更新 AI 建档 Prompt 配置的请求体。 */
    public record UpdateAiPromptConfigRequest(
        /** Prompt 名称，必填，最长 200 位。 */
        @JsonProperty("prompt_name")
        @NotBlank(message = "Prompt 名称不能为空")
        @Size(max = 200, message = "Prompt 名称长度不能超过 200 位")
        String promptName,
        /** Prompt 内容，必填。 */
        @JsonProperty("prompt_content")
        @NotBlank(message = "Prompt 内容不能为空")
        String promptContent,
        /** Prompt 版本号，必填，最长 50 位。 */
        @JsonProperty("prompt_version")
        @NotBlank(message = "Prompt 版本不能为空")
        @Size(max = 50, message = "Prompt 版本长度不能超过 50 位")
        String promptVersion,
        /** Prompt 状态，必填，例如 active 或 draft。 */
        @NotBlank(message = "Prompt 状态不能为空")
        @Size(max = 20, message = "Prompt 状态长度不能超过 20 位")
        String status,
        /** 输出格式，必填，最长 30 位。 */
        @JsonProperty("output_format")
        @NotBlank(message = "输出格式不能为空")
        @Size(max = 30, message = "输出格式长度不能超过 30 位")
        String outputFormat,
        /** 模型名称，选填，最长 100 位；为空时使用运行时默认模型。 */
        @JsonProperty("model_name")
        @Size(max = 100, message = "模型名称长度不能超过 100 位")
        String modelName,
        /** 模型温度，选填；为空时使用运行时默认温度。 */
        Double temperature,
        /** top_p 采样参数，选填。 */
        @JsonProperty("top_p")
        Double topP,
        /** 最大输出 token 数，选填，填写时必须大于等于 1。 */
        @JsonProperty("max_tokens")
        @Min(value = 1, message = "max_tokens 不能小于 1")
        Integer maxTokens,
        /** Prompt 变量定义，选填；只允许 JSON 对象、数组或 null。 */
        @JsonProperty("variables_json")
        Object variablesJson,
        /** 备注，选填，最长 500 位。 */
        @Size(max = 500, message = "remark 长度不能超过 500 位")
        String remark
    ) {
        public boolean hasStructuredVariablesJson() {
            return variablesJson == null
                || variablesJson instanceof Map<?, ?>
                || variablesJson instanceof List<?>;
        }
    }

    /** 更新 AI 建档运行时配置的请求体。 */
    public record UpdateAiRuntimeConfigRequest(
        /** 覆盖配置值，选填；为空且未清空覆盖时保留原值。 */
        @JsonProperty("config_value")
        String configValue,
        /** 配置状态，选填，最长 20 位；当前支持 active 或 disabled。 */
        @Size(max = 20, message = "status 长度不能超过 20 位")
        String status,
        /** 备注，选填，最长 500 位。 */
        @Size(max = 500, message = "remark 长度不能超过 500 位")
        String remark,
        /** 是否清空数据库覆盖值，选填；true 时回退到环境默认值。 */
        @JsonProperty("clear_override")
        Boolean clearOverride
    ) {
    }
}
