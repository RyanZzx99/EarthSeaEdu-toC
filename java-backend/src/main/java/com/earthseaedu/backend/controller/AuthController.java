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

/** 认证、账号、后台管理和导入管理接口，负责请求绑定、鉴权入口和服务层转发。 */
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

    /** 获取微信扫码授权地址，返回授权 URL 和一次性 state。 */
    @GetMapping("/wechat/authorize-url")
    public AuthResponses.WechatAuthorizeUrlResponse getWechatAuthorizeUrl() {
        String state = authService.createWechatState();
        String authorizeUrl = wechatService.buildWechatAuthorizeUrl(state);
        return new AuthResponses.WechatAuthorizeUrlResponse(authorizeUrl, state);
    }

    /** 处理微信开放平台回调，入参为 code、state 或错误信息，重定向回前端登录页。 */
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

    /** 发送短信验证码，入参为手机号和业务类型，返回发送结果和调试码。 */
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

    /** 检查短信登录是否需要邀请码，入参为手机号，返回邀请码要求。 */
    @PostMapping("/login/sms/invite-required")
    public AuthResponses.InviteRequirementCheckResponse checkSmsInviteRequired(
        @Valid @RequestBody AuthRequests.SmsInviteRequirementCheckRequest payload
    ) {
        return authService.checkSmsLoginInviteRequirement(payload.mobile());
    }

    /** 检查微信绑定手机号是否需要邀请码，入参为绑定凭证和手机号，返回邀请码要求。 */
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

    /** 手机号密码登录，入参为手机号和密码，返回登录令牌并记录登录日志。 */
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

    /** 使用邀请码注册并登录，入参为手机号、密码和邀请码，返回登录令牌。 */
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

    /** 短信验证码登录，入参为手机号、验证码和可选邀请码，返回登录令牌。 */
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

    /** 微信扫码登录，入参为回调 code 和 state，返回登录结果或后续注册要求。 */
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

    /** 微信临时账号邀请码注册，入参为注册凭证和邀请码，返回登录令牌。 */
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

    /** 微信账号绑定手机号，入参为绑定凭证、手机号、验证码和可选邀请码，返回登录令牌。 */
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

    /** 设置或修改当前账号密码，入参为新密码和可选当前密码，返回操作结果。 */
    @PostMapping("/password/set")
    public AuthResponses.SimpleMessageResponse setPassword(
        @Valid @RequestBody AuthRequests.SetPasswordRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        authService.setPasswordForUser(userId, payload.newPassword(), payload.currentPassword());
        return new AuthResponses.SimpleMessageResponse("密码设置成功");
    }

    /** 查询当前登录用户资料，入参为 Authorization，返回用户基础资料。 */
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

    /** 修改当前用户昵称，入参为昵称和登录态，返回更新后的昵称。 */
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

    /** 检查当前用户昵称可用性，入参为昵称和登录态，返回可用性判断。 */
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

    /** 激活当前账号教师端权限，入参为教师邀请码，返回激活结果。 */
    @PostMapping("/me/teacher/activate")
    public AuthResponses.TeacherPortalActivateResponse activateTeacherPortal(
        @Valid @RequestBody AuthRequests.TeacherPortalActivateRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.activateTeacherPortalWithInvite(userId, payload.inviteCode());
    }

    /** 绑定当前账号手机号，入参为手机号，返回绑定后的账号信息。 */
    @PostMapping("/me/mobile/bind")
    public AuthResponses.MobileBindResponse bindMyMobile(
        @Valid @RequestBody AuthRequests.BindMyMobileRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.bindMobileForCurrentUser(userId, payload.mobile());
    }

    /** 通过短信重置当前账号密码，入参为手机号、验证码和新密码，返回操作结果。 */
    @PostMapping("/me/password/reset-by-sms")
    public AuthResponses.SimpleMessageResponse resetMyPasswordBySms(
        @Valid @RequestBody AuthRequests.ResetPasswordBySmsRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        authService.resetPasswordForUserBySms(userId, payload.mobile(), payload.code(), payload.newPassword());
        return new AuthResponses.SimpleMessageResponse("密码重置成功");
    }

    /** 检查当前账号新密码是否可用，入参为新密码，返回可用性判断。 */
    @PostMapping("/me/password/check")
    public AuthResponses.AvailabilityCheckResponse checkMyPassword(
        @Valid @RequestBody AuthRequests.CheckPasswordAvailabilityRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.checkPasswordAvailability(userId, payload.newPassword());
    }

    /** 检查短信重置场景的新密码是否可用，入参为新密码，返回可用性判断。 */
    @PostMapping("/me/password/check-for-reset")
    public AuthResponses.AvailabilityCheckResponse checkMyResetPassword(
        @Valid @RequestBody AuthRequests.CheckResetPasswordAvailabilityRequest payload,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String userId = jwtService.requireCurrentUserId(authorization);
        return authService.checkResetPasswordAvailability(userId, payload.newPassword());
    }

    /** 退出登录占位接口，无入参，返回成功消息。 */
    @PostMapping("/logout")
    public AuthResponses.SimpleMessageResponse logout() {
        return new AuthResponses.SimpleMessageResponse("退出成功");
    }

    /** 管理员批量生成邀请码，入参为生成数量、过期天数、备注和用途，返回邀请码列表。 */
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

    /** 管理员发放邀请码，入参为邀请码和手机号，返回发放后的邀请码信息。 */
    @PostMapping("/invite-codes/issue")
    public AuthResponses.InviteCodeItem issueInviteCode(
        @Valid @RequestBody AuthRequests.IssueInviteCodeRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.issueInviteCode(payload.code(), payload.mobile());
    }

    /** 管理员查询邀请码列表，入参为筛选条件和数量上限，返回分页列表。 */
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

    /** 管理员更新邀请码状态，入参为邀请码和目标状态，返回更新后的邀请码。 */
    @PostMapping("/invite-codes/update-status")
    public AuthResponses.InviteCodeItem updateInviteCodeStatus(
        @Valid @RequestBody AuthRequests.UpdateInviteCodeStatusRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.updateInviteCodeStatus(payload.code(), payload.status());
    }

    /** 管理员更新用户状态，入参为用户 ID 或手机号及目标状态，返回更新结果。 */
    @PostMapping("/users/update-status")
    public AuthResponses.UserStatusResponse updateUserStatus(
        @Valid @RequestBody AuthRequests.UpdateUserStatusRequest payload,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return authService.updateUserStatus(payload.status(), payload.userId(), payload.mobile());
    }

    /** 管理员查询昵称规则分组，入参为状态和分组类型，返回分组列表。 */
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

    /** 管理员创建昵称规则分组，入参为分组编码、名称、类型和状态，返回创建结果。 */
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

    /** 管理员创建昵称词条规则，入参为分组、词条、匹配方式和决策，返回创建结果。 */
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

    /** 管理员查询昵称词条规则，入参为分组、状态、决策、关键字和数量上限，返回分页列表。 */
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

    /** 管理员创建昵称联系方式规则，入参为正则、分组、决策和风险等级，返回创建结果。 */
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

    /** 管理员查询昵称联系方式规则，入参为分组、状态、类型、关键字和数量上限，返回分页列表。 */
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

    /** 管理员更新昵称规则状态，入参为目标类型、目标 ID 和状态，返回更新结果。 */
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

    /** 管理员查询昵称审核日志，入参为决策、场景、命中分组和数量上限，返回分页日志。 */
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

    /** 查询 AI 建档 Prompt 配置列表，入参为业务域、阶段、状态、关键词和数量上限，返回分页配置项。 */
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

    /** 管理员创建题库导入任务，入参为来源模式、题库名、入口路径和文件，返回任务详情。 */
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

    /** 管理员查询题库导入任务，入参为任务 ID，返回导入进度和结果。 */
    @GetMapping("/question-banks/import-jobs/{job_id}")
    public Map<String, Object> getQuestionBankImportJob(
        @PathVariable("job_id") long jobId,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return questionBankImportService.getImportJobDetail(jobId);
    }

    /** 更新指定 AI 建档 Prompt 配置，入参为 Prompt ID 与配置请求体，返回更新后的配置。 */
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

    /** 查询 AI 建档运行时配置，入参为管理员密钥，返回当前覆盖值和环境默认值。 */
    @GetMapping("/ai-runtime-configs")
    public Map<String, Object> listAiRuntimeConfigs(
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", aiConfigAdminService.listAiRuntimeConfigs());
        return response;
    }

    /** 更新 AI 建档运行时配置，入参为配置键与覆盖值，返回更新后的生效配置。 */
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
