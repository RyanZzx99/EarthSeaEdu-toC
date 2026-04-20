package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.dto.auth.AuthRequests;
import com.earthseaedu.backend.dto.auth.AuthResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.model.ai.AiPromptConfig;
import com.earthseaedu.backend.model.nickname.NicknameAuditLog;
import com.earthseaedu.backend.model.nickname.NicknameContactPattern;
import com.earthseaedu.backend.model.nickname.NicknameRuleGroup;
import com.earthseaedu.backend.model.nickname.NicknameWordRule;
import com.earthseaedu.backend.service.AiConfigAdminService;
import com.earthseaedu.backend.service.AuthService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.NicknameGuardService;
import com.earthseaedu.backend.service.QuestionBankImportService;
import com.earthseaedu.backend.service.WechatService;
import com.earthseaedu.backend.support.PageResult;
import com.earthseaedu.backend.support.RequestHeaderSupport;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final WechatService wechatService;
    private final NicknameGuardService nicknameGuardService;
    private final AiConfigAdminService aiConfigAdminService;
    private final QuestionBankImportService questionBankImportService;
    private final EarthSeaProperties properties;

    public AuthController(
        AuthService authService,
        JwtService jwtService,
        WechatService wechatService,
        NicknameGuardService nicknameGuardService,
        AiConfigAdminService aiConfigAdminService,
        QuestionBankImportService questionBankImportService,
        EarthSeaProperties properties
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.wechatService = wechatService;
        this.nicknameGuardService = nicknameGuardService;
        this.aiConfigAdminService = aiConfigAdminService;
        this.questionBankImportService = questionBankImportService;
        this.properties = properties;
    }

    @GetMapping("/wechat/authorize-url")
    public AuthResponses.WechatAuthorizeUrlResponse getWechatAuthorizeUrl() {
        String state = authService.createWechatState();
        String authorizeUrl = wechatService.buildWechatAuthorizeUrl(state);
        return new AuthResponses.WechatAuthorizeUrlResponse(authorizeUrl, state);
    }

    @GetMapping("/wechat/callback")
    public RedirectView wechatCallback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error
    ) {
        String frontendLoginPageUrl = wechatService.resolveFrontendLoginPageUrl();
        String redirectUrl;

        if (error != null && !error.isBlank()) {
            redirectUrl = UriComponentsBuilder.fromUriString(frontendLoginPageUrl)
                .queryParam("wechat_error", error)
                .build()
                .toUriString();
            return new RedirectView(redirectUrl, true);
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            redirectUrl = UriComponentsBuilder.fromUriString(frontendLoginPageUrl)
                .queryParam("wechat_error", "missing_code_or_state")
                .build()
                .toUriString();
            return new RedirectView(redirectUrl, true);
        }

        redirectUrl = UriComponentsBuilder.fromUriString(frontendLoginPageUrl)
            .queryParam("code", code)
            .queryParam("state", state)
            .build()
            .toUriString();
        return new RedirectView(redirectUrl, true);
    }

    @PostMapping("/sms/send-code")
    public Map<String, Object> sendSmsCode(@Valid @RequestBody AuthRequests.SendSmsCodeRequest payload) {
        String code = authService.sendAndSaveSmsCode(payload.mobile(), payload.bizType());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "验证码已发送");
        if (properties.isAppDebug() && properties.getTencentcloud().isSmsMock()) {
            response.put("debug_code", code);
        }
        return response;
    }

    @PostMapping("/login/sms/invite-required")
    public AuthResponses.InviteRequirementCheckResponse checkSmsInviteRequired(
        @Valid @RequestBody AuthRequests.SmsInviteRequirementCheckRequest payload
    ) {
        return authService.checkSmsLoginInviteRequirement(payload.mobile());
    }

    @PostMapping("/wechat/bind-mobile/invite-required")
    public AuthResponses.InviteRequirementCheckResponse checkWechatBindInviteRequired(
        @Valid @RequestBody AuthRequests.WechatBindInviteRequirementCheckRequest payload
    ) {
        Claims claims = jwtService.requireTokenUse(
            payload.bindToken(),
            "bind_mobile",
            "bind_token 无效或已过期",
            "bind_token 类型错误"
        );
        return authService.checkWechatBindInviteRequirement(claims.getSubject(), payload.mobile());
    }

    @PostMapping("/login/password")
    public AuthResponses.LoginResponse passwordLogin(
        @Valid @RequestBody AuthRequests.PasswordLoginRequest payload,
        HttpServletRequest request
    ) {
        try {
            AuthResponses.LoginResponse result = authService.loginByPassword(payload.mobile(), payload.password());
            authService.createLoginLog(
                "password",
                payload.mobile(),
                true,
                result.userId(),
                null,
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "password",
                payload.mobile(),
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/login/temp-register")
    public AuthResponses.LoginResponse tempRegisterLogin(
        @Valid @RequestBody AuthRequests.TempRegisterLoginRequest payload,
        HttpServletRequest request
    ) {
        try {
            AuthResponses.LoginResponse result = authService.registerAndLoginByInvitePassword(
                payload.mobile(),
                payload.password(),
                payload.inviteCode()
            );
            authService.createLoginLog(
                "temp_register",
                payload.mobile(),
                true,
                result.userId(),
                null,
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "temp_register",
                payload.mobile(),
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/login/sms")
    public AuthResponses.LoginResponse smsLogin(
        @Valid @RequestBody AuthRequests.SmsLoginRequest payload,
        HttpServletRequest request
    ) {
        try {
            AuthResponses.LoginResponse result = authService.loginBySms(
                payload.mobile(),
                payload.code(),
                payload.inviteCode()
            );
            authService.createLoginLog(
                "sms",
                payload.mobile(),
                true,
                result.userId(),
                null,
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "sms",
                payload.mobile(),
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/login/wechat")
    public Object wechatLogin(
        @Valid @RequestBody AuthRequests.WechatLoginRequest payload,
        HttpServletRequest request
    ) {
        try {
            Object result = authService.loginByWechat(payload.code(), payload.state());
            if (result instanceof AuthResponses.LoginResponse loginResponse) {
                authService.createLoginLog(
                    "wechat_open",
                    "wechat_openid_hidden",
                    true,
                    loginResponse.userId(),
                    null,
                    RequestHeaderSupport.resolveClientIp(request),
                    request.getHeader(HttpHeaders.USER_AGENT)
                );
            }
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "wechat_open",
                null,
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/login/wechat/invite-register")
    public AuthResponses.LoginResponse wechatInviteRegister(
        @Valid @RequestBody AuthRequests.WechatInviteRegisterRequest payload,
        HttpServletRequest request
    ) {
        Claims claims = jwtService.requireTokenUse(
            payload.registerToken(),
            "wechat_register",
            "注册凭证无效或已过期",
            "注册凭证类型错误"
        );
        try {
            AuthResponses.LoginResponse result = authService.registerWechatUserByInvite(
                claims.getSubject(),
                payload.inviteCode()
            );
            authService.createLoginLog(
                "wechat_open_register",
                null,
                true,
                result.userId(),
                null,
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "wechat_open_register",
                null,
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/wechat/bind-mobile")
    public AuthResponses.LoginResponse wechatBindMobile(
        @Valid @RequestBody AuthRequests.WechatBindMobileRequest payload,
        HttpServletRequest request
    ) {
        Claims claims = jwtService.requireTokenUse(
            payload.bindToken(),
            "bind_mobile",
            "bind_token 无效或已过期",
            "bind_token 类型错误"
        );
        try {
            AuthResponses.LoginResponse result = authService.bindMobileForWechatUser(
                claims.getSubject(),
                payload.mobile(),
                payload.code(),
                payload.inviteCode()
            );
            authService.createLoginLog(
                "wechat_bind_mobile",
                payload.mobile(),
                true,
                result.userId(),
                null,
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            return result;
        } catch (ApiException exception) {
            authService.createLoginLog(
                "wechat_bind_mobile",
                payload.mobile(),
                false,
                null,
                exception.getMessage(),
                RequestHeaderSupport.resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)
            );
            throw exception;
        }
    }

    @PostMapping("/password/set")
    public AuthResponses.SimpleMessageResponse setPassword(
        @Valid @RequestBody AuthRequests.SetPasswordRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        authService.setPasswordForUser(userId, payload.newPassword(), payload.currentPassword());
        return new AuthResponses.SimpleMessageResponse("密码设置成功");
    }

    @GetMapping("/me")
    public AuthResponses.UserProfileResponse me(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        AuthResponses.UserProfileResponse profile = authService.getUserProfile(userId);
        if (profile == null) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在");
        }
        return profile;
    }

    @PostMapping("/me/nickname")
    public AuthResponses.NicknameResponse updateMyNickname(
        @Valid @RequestBody AuthRequests.UpdateNicknameRequest payload,
        HttpServletRequest request,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return nicknameGuardService.updateNicknameForUser(
            userId,
            payload.nickname(),
            RequestHeaderSupport.resolveClientIp(request),
            request.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @PostMapping("/me/nickname/check")
    public AuthResponses.AvailabilityCheckResponse checkMyNickname(
        @Valid @RequestBody AuthRequests.CheckNicknameAvailabilityRequest payload,
        HttpServletRequest request,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return nicknameGuardService.checkNicknameAvailability(
            userId,
            payload.nickname(),
            RequestHeaderSupport.resolveClientIp(request),
            request.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @PostMapping("/me/teacher/activate")
    public AuthResponses.TeacherPortalActivateResponse activateTeacherPortal(
        @Valid @RequestBody AuthRequests.TeacherPortalActivateRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.activateTeacherPortalWithInvite(userId, payload.inviteCode());
    }

    @PostMapping("/me/mobile/bind")
    public AuthResponses.MobileBindResponse bindMyMobile(
        @Valid @RequestBody AuthRequests.BindMyMobileRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.bindMobileForCurrentUser(userId, payload.mobile());
    }

    @PostMapping("/me/password/reset-by-sms")
    public AuthResponses.SimpleMessageResponse resetMyPasswordBySms(
        @Valid @RequestBody AuthRequests.ResetPasswordBySmsRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        authService.resetPasswordForUserBySms(userId, payload.mobile(), payload.code(), payload.newPassword());
        return new AuthResponses.SimpleMessageResponse("密码重置成功");
    }

    @PostMapping("/me/password/check")
    public AuthResponses.AvailabilityCheckResponse checkMyPassword(
        @Valid @RequestBody AuthRequests.CheckPasswordAvailabilityRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.checkPasswordAvailability(userId, payload.newPassword());
    }

    @PostMapping("/me/password/check-for-reset")
    public AuthResponses.AvailabilityCheckResponse checkMyResetPassword(
        @Valid @RequestBody AuthRequests.CheckResetPasswordAvailabilityRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.checkResetPasswordAvailability(userId, payload.newPassword());
    }

    @PostMapping("/logout")
    public AuthResponses.SimpleMessageResponse logout() {
        return new AuthResponses.SimpleMessageResponse("退出成功");
    }

    @PostMapping("/invite-codes/generate")
    public AuthResponses.InviteCodeItemsResponse generateInviteCodes(
        @Valid @RequestBody AuthRequests.GenerateInviteCodesRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.createInviteCodes(
            payload.count(),
            payload.expiresDays(),
            payload.note(),
            payload.inviteScene()
        );
    }

    @PostMapping("/invite-codes/issue")
    public AuthResponses.InviteCodeItem issueInviteCode(
        @Valid @RequestBody AuthRequests.IssueInviteCodeRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.issueInviteCode(payload.code(), payload.mobile());
    }

    @GetMapping("/invite-codes")
    public AuthResponses.InviteCodeListResponse listInviteCodes(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "mobile", required = false) String mobile,
        @RequestParam(value = "code_keyword", required = false) String codeKeyword,
        @RequestParam(value = "invite_scene", required = false) String inviteScene,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.listInviteCodes(status, mobile, codeKeyword, inviteScene, limit);
    }

    @PostMapping("/invite-codes/update-status")
    public AuthResponses.InviteCodeItem updateInviteCodeStatus(
        @Valid @RequestBody AuthRequests.UpdateInviteCodeStatusRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.updateInviteCodeStatus(payload.code(), payload.status());
    }

    @PostMapping("/users/update-status")
    public AuthResponses.UserStatusResponse updateUserStatus(
        @Valid @RequestBody AuthRequests.UpdateUserStatusRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.updateUserStatus(payload.status(), payload.userId(), payload.mobile());
    }

    @GetMapping("/nickname/rule-groups")
    public Map<String, Object> listNicknameRuleGroups(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "group_type", required = false) String groupType,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
            "items",
            nicknameGuardService.listNicknameRuleGroups(status, groupType)
                .stream()
                .map(nicknameGuardService::toRuleGroupPayload)
                .toList()
        );
        return response;
    }

    @PostMapping("/nickname/rule-groups")
    public Map<String, Object> createNicknameRuleGroup(
        @Valid @RequestBody AuthRequests.CreateNicknameRuleGroupRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        NicknameRuleGroup row = nicknameGuardService.createNicknameRuleGroup(
            payload.groupCode(),
            payload.groupName(),
            payload.groupType(),
            payload.scope(),
            payload.status(),
            payload.priority(),
            payload.description()
        );
        return nicknameGuardService.toRuleGroupPayload(row);
    }

    @PostMapping("/nickname/word-rules")
    public Map<String, Object> createNicknameWordRule(
        @Valid @RequestBody AuthRequests.CreateNicknameWordRuleRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        NicknameWordRule row = nicknameGuardService.createNicknameWordRule(
            payload.groupId(),
            payload.word(),
            payload.matchType(),
            payload.decision(),
            payload.status(),
            payload.priority(),
            payload.riskLevel(),
            payload.source(),
            payload.note()
        );
        return nicknameGuardService.toWordRulePayload(row);
    }

    @GetMapping("/nickname/word-rules")
    public Map<String, Object> listNicknameWordRules(
        @RequestParam(value = "group_id", required = false) Integer groupId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "decision", required = false) String decision,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        PageResult<NicknameWordRule> result = nicknameGuardService.listNicknameWordRules(
            groupId,
            status,
            decision,
            keyword,
            limit
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.total());
        response.put("items", result.items().stream().map(nicknameGuardService::toWordRulePayload).toList());
        return response;
    }

    @PostMapping("/nickname/contact-patterns")
    public Map<String, Object> createNicknameContactPattern(
        @Valid @RequestBody AuthRequests.CreateNicknameContactPatternRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        NicknameContactPattern row = nicknameGuardService.createNicknameContactPattern(
            payload.patternName(),
            payload.patternType(),
            payload.patternRegex(),
            payload.groupId(),
            payload.decision(),
            payload.status(),
            payload.priority(),
            payload.riskLevel(),
            payload.normalizedHint(),
            payload.note()
        );
        return nicknameGuardService.toContactPatternPayload(row);
    }

    @GetMapping("/nickname/contact-patterns")
    public Map<String, Object> listNicknameContactPatterns(
        @RequestParam(value = "group_id", required = false) Integer groupId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "pattern_type", required = false) String patternType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        PageResult<NicknameContactPattern> result = nicknameGuardService.listNicknameContactPatterns(
            groupId,
            status,
            patternType,
            keyword,
            limit
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.total());
        response.put("items", result.items().stream().map(nicknameGuardService::toContactPatternPayload).toList());
        return response;
    }

    @PostMapping("/nickname/rules/update-status")
    public Map<String, Object> updateNicknameRuleTargetStatus(
        @Valid @RequestBody AuthRequests.UpdateNicknameRuleTargetStatusRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return nicknameGuardService.updateNicknameRuleTargetStatus(
            payload.targetType(),
            payload.targetId(),
            payload.status()
        );
    }

    @GetMapping("/nickname/audit-logs")
    public Map<String, Object> listNicknameAuditLogs(
        @RequestParam(value = "decision", required = false) String decision,
        @RequestParam(value = "scene", required = false) String scene,
        @RequestParam(value = "hit_group_code", required = false) String hitGroupCode,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        PageResult<NicknameAuditLog> result = nicknameGuardService.listNicknameAuditLogs(
            decision,
            scene,
            hitGroupCode,
            limit
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.total());
        response.put("items", result.items().stream().map(nicknameGuardService::toAuditLogPayload).toList());
        return response;
    }

    @GetMapping("/ai-prompts")
    public Map<String, Object> listAiPrompts(
        @RequestParam(value = "biz_domain", required = false) String bizDomain,
        @RequestParam(value = "prompt_stage", required = false) String promptStage,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        PageResult<AiPromptConfig> result = aiConfigAdminService.listAiPromptConfigs(
            bizDomain,
            promptStage,
            status,
            keyword,
            limit
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.total());
        response.put("items", result.items().stream().map(aiConfigAdminService::toAiPromptListPayload).toList());
        return response;
    }

    @PostMapping(value = "/question-banks/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importQuestionBank(
        @RequestParam("source_mode") String sourceMode,
        @RequestParam(value = "bank_name", required = false) String bankName,
        @RequestParam(value = "entry_paths_json", required = false) String entryPathsJson,
        @RequestParam("files") List<MultipartFile> files,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return questionBankImportService.createImportJob(sourceMode, bankName, entryPathsJson, files);
    }

    @GetMapping("/question-banks/import-jobs/{job_id}")
    public Map<String, Object> getQuestionBankImportJob(
        @PathVariable("job_id") long jobId,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return questionBankImportService.getImportJobDetail(jobId);
    }

    @PostMapping("/ai-prompts/{prompt_id}/update")
    public Map<String, Object> updateAiPrompt(
        @PathVariable("prompt_id") long promptId,
        @Valid @RequestBody AuthRequests.UpdateAiPromptConfigRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        if (!payload.hasStructuredVariablesJson()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "variables_json 必须是 JSON 对象、数组或空值");
        }
        AiPromptConfig row = aiConfigAdminService.updateAiPromptConfig(
            promptId,
            payload.promptName(),
            payload.promptContent(),
            payload.promptVersion(),
            payload.status(),
            payload.outputFormat(),
            payload.modelName(),
            payload.temperature(),
            payload.topP(),
            payload.maxTokens(),
            payload.variablesJson(),
            payload.remark()
        );
        return aiConfigAdminService.toAiPromptUpdatePayload(row);
    }

    @GetMapping("/ai-runtime-configs")
    public Map<String, Object> listAiRuntimeConfigs(
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", aiConfigAdminService.listAiRuntimeConfigs());
        return response;
    }

    @PostMapping("/ai-runtime-configs/{config_key}/update")
    public Map<String, Object> updateAiRuntimeConfig(
        @PathVariable("config_key") String configKey,
        @Valid @RequestBody AuthRequests.UpdateAiRuntimeConfigRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return aiConfigAdminService.updateAiRuntimeConfig(
            configKey,
            payload.configValue(),
            payload.status(),
            payload.remark(),
            Boolean.TRUE.equals(payload.clearOverride())
        );
    }
}
