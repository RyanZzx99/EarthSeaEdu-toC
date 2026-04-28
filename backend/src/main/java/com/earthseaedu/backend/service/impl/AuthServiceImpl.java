package com.earthseaedu.backend.service.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.dto.auth.AuthResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.InviteCodeMapper;
import com.earthseaedu.backend.mapper.UserAuthIdentityMapper;
import com.earthseaedu.backend.mapper.UserLoginLogMapper;
import com.earthseaedu.backend.mapper.UserMapper;
import com.earthseaedu.backend.model.auth.InviteCode;
import com.earthseaedu.backend.model.auth.User;
import com.earthseaedu.backend.model.auth.UserAuthIdentity;
import com.earthseaedu.backend.model.auth.UserLoginLog;
import com.earthseaedu.backend.service.AuthService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.SmsCodeCacheService;
import com.earthseaedu.backend.service.TencentSmsService;
import com.earthseaedu.backend.service.WechatService;
import com.earthseaedu.backend.support.PasswordRules;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String REGISTER_INVITE_SCENE = "register";
    private static final String TEACHER_PORTAL_INVITE_SCENE = "teacher_portal";
    private static final String WECHAT_OPEN_IDENTITY_TYPE = "wechat_open";

    private final UserMapper userMapper;
    private final UserAuthIdentityMapper userAuthIdentityMapper;
    private final UserLoginLogMapper userLoginLogMapper;
    private final InviteCodeMapper inviteCodeMapper;
    private final JwtService jwtService;
    private final SmsCodeCacheService smsCodeCacheService;
    private final TencentSmsService tencentSmsService;
    private final WechatService wechatService;
    private final EarthSeaProperties properties;

    /**
     * 创建 AuthServiceImpl 实例。
     */
    public AuthServiceImpl(
        UserMapper userMapper,
        UserAuthIdentityMapper userAuthIdentityMapper,
        UserLoginLogMapper userLoginLogMapper,
        InviteCodeMapper inviteCodeMapper,
        JwtService jwtService,
        SmsCodeCacheService smsCodeCacheService,
        TencentSmsService tencentSmsService,
        WechatService wechatService,
        EarthSeaProperties properties
    ) {
        this.userMapper = userMapper;
        this.userAuthIdentityMapper = userAuthIdentityMapper;
        this.userLoginLogMapper = userLoginLogMapper;
        this.inviteCodeMapper = inviteCodeMapper;
        this.jwtService = jwtService;
        this.smsCodeCacheService = smsCodeCacheService;
        this.tencentSmsService = tencentSmsService;
        this.wechatService = wechatService;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    public String createWechatState() {
        String state = RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 16);
        smsCodeCacheService.createWechatLoginState(state, 10);
        return state;
    }

    /**
     * {@inheritDoc}
     */
    public void verifyInviteAdminKey(String adminKey) {
        if (CharSequenceUtil.isBlank(properties.getInviteAdminKey())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "系统未配置邀请码管理密钥");
        }
        if (!properties.getInviteAdminKey().equals(adminKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员密钥错误");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String sendAndSaveSmsCode(String mobile, String bizType) {
        validateMobile(mobile);
        if (!"login".equals(bizType) && !"bind_mobile".equals(bizType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "biz_type 仅支持 login 或 bind_mobile");
        }
        smsCodeCacheService.validateSmsSendAllowed(mobile, bizType);
        String code = tencentSmsService.sendSmsCode(mobile, bizType);
        smsCodeCacheService.saveSmsCode(mobile, bizType, code);
        return code;
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.InviteRequirementCheckResponse checkSmsLoginInviteRequirement(String mobile) {
        validateMobile(mobile);
        User user = getActiveUserByMobile(mobile);
        if (user != null) {
            return new AuthResponses.InviteRequirementCheckResponse(
                false,
                true,
                "该手机号已注册，可直接使用验证码登录"
            );
        }
        return new AuthResponses.InviteRequirementCheckResponse(
            true,
            false,
            "该手机号尚未注册，首次登录需要填写邀请码"
        );
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.InviteRequirementCheckResponse checkWechatBindInviteRequirement(String bindUserId, String mobile) {
        validateMobile(mobile);
        User currentUser = getActiveUserById(bindUserId);
        if (currentUser == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "绑定用户不存在");
        }
        if (CharSequenceUtil.isNotBlank(currentUser.getMobile())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前微信账号已绑定手机号");
        }

        User existingUser = getActiveUserByMobile(mobile);
        if (existingUser != null) {
            return new AuthResponses.InviteRequirementCheckResponse(
                false,
                true,
                "该手机号已注册，绑定时无需填写邀请码"
            );
        }
        return new AuthResponses.InviteRequirementCheckResponse(
            true,
            false,
            "该手机号尚未注册，首次绑定需要填写邀请码"
        );
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.LoginResponse loginByPassword(String mobile, String password) {
        validateMobile(mobile);
        User user = getActiveUserByMobile(mobile);
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户已被禁用");
        }
        if (CharSequenceUtil.isBlank(user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该账号尚未设置密码，请先使用短信登录");
        }
        PasswordRules.ensureBcryptPasswordLength(password);
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码错误");
        }
        return buildLoginResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.LoginResponse registerAndLoginByInvitePassword(String mobile, String password, String inviteCode) {
        validateMobile(mobile);
        User existingUser = getActiveUserByMobile(mobile);
        if (existingUser != null) {
            if (!"active".equals(existingUser.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号对应账号已被禁用");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号已注册，请直接使用密码登录或短信登录");
        }

        try {
            PasswordRules.validatePasswordStrength(password);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        InviteCode inviteRow = consumeInviteCodeForRegister(inviteCode, mobile);

        User user = new User();
        user.setId(IdUtil.randomUUID());
        user.setMobile(mobile);
        user.setMobileVerified(0);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setStatus("active");
        user.setIsTempWechatUser(0);
        user.setIsTeacher("0");
        user.setDeleteFlag("1");
        user.setCreateTime(nowUtc());
        user.setUpdateTime(nowUtc());
        userMapper.insert(user);

        inviteRow.setUsedByUserId(user.getId());
        inviteRow.setUpdateTime(nowUtc());
        inviteCodeMapper.update(inviteRow);

        return buildLoginResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.LoginResponse loginBySms(String mobile, String code, String inviteCode) {
        validateMobile(mobile);
        User user = getActiveUserByMobile(mobile);

        if (user == null) {
            precheckInviteCodeForRegister(inviteCode, mobile);
        }

        if (!smsCodeCacheService.verifySmsCode(mobile, "login", code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }

        if (user == null) {
            InviteCode inviteRow = consumeInviteCodeForRegister(inviteCode, mobile);
            user = registerUserByMobile(mobile);
            inviteRow.setUsedByUserId(user.getId());
            inviteRow.setUpdateTime(nowUtc());
            inviteCodeMapper.update(inviteRow);
        }

        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户已被禁用");
        }
        if (!Integer.valueOf(1).equals(user.getMobileVerified())) {
            user.setMobileVerified(1);
            user.setUpdateTime(nowUtc());
            userMapper.update(user);
        }

        return buildLoginResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Object loginByWechat(String code, String state) {
        if (!smsCodeCacheService.consumeWechatLoginState(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信登录 state 无效或已过期");
        }

        WechatService.WechatAccessInfo accessInfo = wechatService.getWechatAccessInfoByCode(code);
        String openid = accessInfo.openid();
        User user = getActiveUserByWechatOpenId(openid);

        if (user == null) {
            WechatService.WechatUserInfo userInfo = CharSequenceUtil.isBlank(accessInfo.accessToken())
                ? WechatService.WechatUserInfo.empty()
                : wechatService.getWechatUserInfo(accessInfo.accessToken(), openid);

            user = new User();
            user.setId(IdUtil.randomUUID());
            user.setMobile(null);
            user.setMobileVerified(0);
            user.setPasswordHash(null);
            user.setStatus("active");
            user.setIsTempWechatUser(1);
            user.setIsTeacher("0");
            user.setDeleteFlag("1");
            applyWechatProfileToUser(user, userInfo);
            user.setCreateTime(nowUtc());
            user.setUpdateTime(nowUtc());
            userMapper.insert(user);

            UserAuthIdentity identity = new UserAuthIdentity();
            identity.setUserId(user.getId());
            identity.setIdentityType(WECHAT_OPEN_IDENTITY_TYPE);
            identity.setIdentityKey(openid);
            identity.setIdentityExtra(accessInfo.unionid());
            identity.setDeleteFlag("1");
            identity.setCreateTime(nowUtc());
            identity.setUpdateTime(nowUtc());
            userAuthIdentityMapper.insert(identity);
        }

        if (CharSequenceUtil.isNotBlank(accessInfo.accessToken())) {
            WechatService.WechatUserInfo latestUserInfo = wechatService.getWechatUserInfo(accessInfo.accessToken(), openid);
            applyWechatProfileToUser(user, latestUserInfo);
            user.setUpdateTime(nowUtc());
            userMapper.update(user);
        }

        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户已被禁用");
        }

        if (!Integer.valueOf(1).equals(user.getIsTempWechatUser())) {
            return buildLoginResponse(user);
        }

        return new AuthResponses.WechatInviteRegisterRequiredResponse(
            "wechat_invite_register",
            jwtService.createWechatRegisterToken(user.getId(), openid),
            "微信扫码成功，请先填写邀请码完成注册"
        );
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.LoginResponse registerWechatUserByInvite(String registerUserId, String inviteCode) {
        User user = requireActiveUser(registerUserId);
        if (!Integer.valueOf(1).equals(user.getIsTempWechatUser())) {
            return buildLoginResponse(user);
        }

        UserAuthIdentity currentIdentity = getActiveWechatIdentityByUserId(user.getId());
        if (currentIdentity == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前微信身份不存在");
        }

        InviteCode inviteRow = consumeInviteCodeForWechatRegister(inviteCode);
        user.setIsTempWechatUser(0);
        user.setUpdateTime(nowUtc());
        userMapper.update(user);

        inviteRow.setUsedByUserId(user.getId());
        inviteRow.setUpdateTime(nowUtc());
        inviteCodeMapper.update(inviteRow);

        return buildLoginResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.LoginResponse bindMobileForWechatUser(
        String bindUserId,
        String mobile,
        String code,
        String inviteCode
    ) {
        validateMobile(mobile);
        User currentUser = getActiveUserById(bindUserId);
        if (currentUser == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "绑定用户不存在");
        }
        if (CharSequenceUtil.isNotBlank(currentUser.getMobile())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前微信账号已绑定手机号");
        }

        UserAuthIdentity currentIdentity = getActiveWechatIdentityByUserId(currentUser.getId());
        if (currentIdentity == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前微信身份不存在");
        }

        User existingMobileUser = getActiveUserByMobile(mobile);
        if (existingMobileUser == null) {
            precheckInviteCodeForRegister(inviteCode, mobile);
        }

        if (!smsCodeCacheService.verifySmsCode(mobile, "bind_mobile", code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }

        if (existingMobileUser == null) {
            InviteCode inviteRow = consumeInviteCodeForRegister(inviteCode, mobile);
            currentUser.setMobile(mobile);
            currentUser.setMobileVerified(1);
            currentUser.setIsTempWechatUser(0);
            currentUser.setUpdateTime(nowUtc());
            userMapper.update(currentUser);

            inviteRow.setUsedByUserId(currentUser.getId());
            inviteRow.setUpdateTime(nowUtc());
            inviteCodeMapper.update(inviteRow);

            return buildLoginResponse(currentUser);
        }

        if (!"active".equals(existingMobileUser.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号对应账号已被禁用");
        }

        if (existingMobileUser.getId().equals(currentUser.getId())) {
            currentUser.setMobileVerified(1);
            currentUser.setIsTempWechatUser(0);
            currentUser.setUpdateTime(nowUtc());
            userMapper.update(currentUser);
            return buildLoginResponse(currentUser);
        }

        UserAuthIdentity existingWechatIdentity = getActiveWechatIdentityByUserId(existingMobileUser.getId());
        if (existingWechatIdentity != null
            && !CharSequenceUtil.equals(existingWechatIdentity.getIdentityKey(), currentIdentity.getIdentityKey())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号已绑定其他微信账号，无法再次绑定");
        }

        currentIdentity.setUserId(existingMobileUser.getId());
        currentIdentity.setUpdateTime(nowUtc());
        userAuthIdentityMapper.update(currentIdentity);

        existingMobileUser.setMobileVerified(1);
        if (CharSequenceUtil.isBlank(existingMobileUser.getNickname()) && CharSequenceUtil.isNotBlank(currentUser.getNickname())) {
            existingMobileUser.setNickname(currentUser.getNickname());
        }
        if (CharSequenceUtil.isBlank(existingMobileUser.getAvatarUrl()) && CharSequenceUtil.isNotBlank(currentUser.getAvatarUrl())) {
            existingMobileUser.setAvatarUrl(currentUser.getAvatarUrl());
        }
        if (existingMobileUser.getSex() == null && currentUser.getSex() != null) {
            existingMobileUser.setSex(currentUser.getSex());
        }
        if (CharSequenceUtil.isBlank(existingMobileUser.getProvince()) && CharSequenceUtil.isNotBlank(currentUser.getProvince())) {
            existingMobileUser.setProvince(currentUser.getProvince());
        }
        if (CharSequenceUtil.isBlank(existingMobileUser.getCity()) && CharSequenceUtil.isNotBlank(currentUser.getCity())) {
            existingMobileUser.setCity(currentUser.getCity());
        }
        if (CharSequenceUtil.isBlank(existingMobileUser.getCountry()) && CharSequenceUtil.isNotBlank(currentUser.getCountry())) {
            existingMobileUser.setCountry(currentUser.getCountry());
        }
        existingMobileUser.setUpdateTime(nowUtc());
        userMapper.update(existingMobileUser);

        currentUser.setStatus("disabled");
        currentUser.setDeleteFlag("0");
        currentUser.setUpdateTime(nowUtc());
        userMapper.update(currentUser);

        return buildLoginResponse(existingMobileUser);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public void setPasswordForUser(String userId, String newPassword, String currentPassword) {
        User user = requireActiveUser(userId);
        if (CharSequenceUtil.isNotBlank(user.getPasswordHash())) {
            String normalizedCurrentPassword = CharSequenceUtil.nullToEmpty(currentPassword).trim();
            if (CharSequenceUtil.isBlank(normalizedCurrentPassword)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请输入当前密码");
            }
            PasswordRules.ensureBcryptPasswordLength(normalizedCurrentPassword);
            if (!BCrypt.checkpw(normalizedCurrentPassword, user.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "当前密码错误");
            }
        }
        if (CharSequenceUtil.isNotBlank(user.getPasswordHash()) && BCrypt.checkpw(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        try {
            PasswordRules.validatePasswordStrength(newPassword);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        user.setPasswordHash(BCrypt.hashpw(newPassword));
        user.setUpdateTime(nowUtc());
        userMapper.update(user);
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.UserProfileResponse getUserProfile(String userId) {
        User user = getActiveUserById(userId);
        if (user == null) {
            return null;
        }
        return new AuthResponses.UserProfileResponse(
            user.getId(),
            user.getMobile(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getSex(),
            user.getProvince(),
            user.getCity(),
            user.getCountry(),
            user.getStatus(),
            "1".equals(user.getIsTeacher()),
            CharSequenceUtil.isNotBlank(user.getPasswordHash())
        );
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.TeacherPortalActivateResponse activateTeacherPortalWithInvite(String userId, String inviteCode) {
        User user = requireActiveUser(userId);
        if ("1".equals(user.getIsTeacher())) {
            return new AuthResponses.TeacherPortalActivateResponse("教师端已开通", user.getId(), true);
        }

        String normalizedCode = normalizeInviteCode(inviteCode, "请输入教师邀请码");
        InviteCode row = requireInviteCode(normalizedCode, "教师邀请码不存在");
        if (!TEACHER_PORTAL_INVITE_SCENE.equals(normalizeInviteScene(row.getInviteScene()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前邀请码不能用于教师端");
        }
        if (!"1".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "教师邀请码已使用或已失效");
        }
        if (isExpired(row.getExpiresTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "教师邀请码已过期");
        }
        if (CharSequenceUtil.isNotBlank(row.getIssuedToMobile())
            && CharSequenceUtil.isNotBlank(user.getMobile())
            && !CharSequenceUtil.equals(row.getIssuedToMobile(), user.getMobile())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "教师邀请码与当前账号手机号不匹配");
        }

        user.setIsTeacher("1");
        user.setUpdateTime(nowUtc());
        userMapper.update(user);

        row.setStatus("2");
        row.setUsedByUserId(user.getId());
        row.setUsedTime(nowUtc());
        row.setUpdateTime(nowUtc());
        inviteCodeMapper.update(row);

        return new AuthResponses.TeacherPortalActivateResponse("教师端已开通", user.getId(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.MobileBindResponse bindMobileForCurrentUser(String userId, String mobile) {
        validateMobile(mobile);
        User user = requireActiveUser(userId);
        User existingUser = getActiveUserByMobile(mobile);
        if (existingUser != null && !existingUser.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号已被其他账号使用");
        }
        user.setMobile(mobile);
        user.setMobileVerified(1);
        user.setIsTempWechatUser(0);
        user.setUpdateTime(nowUtc());
        userMapper.update(user);
        return new AuthResponses.MobileBindResponse("手机号保存成功", user.getId(), user.getMobile());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordForUserBySms(String userId, String mobile, String code, String newPassword) {
        validateMobile(mobile);
        User user = requireActiveUser(userId);
        if (CharSequenceUtil.isBlank(user.getMobile())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前账号未绑定手机号，无法通过短信重置密码");
        }
        if (!CharSequenceUtil.equals(user.getMobile(), mobile)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手机号与当前账号不一致");
        }
        if (!smsCodeCacheService.verifySmsCode(mobile, "login", code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
        if (CharSequenceUtil.isNotBlank(user.getPasswordHash()) && BCrypt.checkpw(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        try {
            PasswordRules.validatePasswordStrength(newPassword);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        user.setPasswordHash(BCrypt.hashpw(newPassword));
        user.setUpdateTime(nowUtc());
        userMapper.update(user);
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.AvailabilityCheckResponse checkPasswordAvailability(String userId, String newPassword) {
        User user = requireActiveUser(userId);
        if (CharSequenceUtil.isNotBlank(user.getPasswordHash()) && BCrypt.checkpw(newPassword, user.getPasswordHash())) {
            return new AuthResponses.AvailabilityCheckResponse(false, "新密码不能与当前密码相同");
        }
        try {
            PasswordRules.validatePasswordStrength(newPassword);
            return new AuthResponses.AvailabilityCheckResponse(true, "该密码可以使用");
        } catch (IllegalArgumentException exception) {
            return new AuthResponses.AvailabilityCheckResponse(false, exception.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.AvailabilityCheckResponse checkResetPasswordAvailability(String userId, String newPassword) {
        return checkPasswordAvailability(userId, newPassword);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.InviteCodeItemsResponse createInviteCodes(
        int count,
        Integer expiresDays,
        String note,
        String inviteScene
    ) {
        String normalizedInviteScene = normalizeInviteScene(inviteScene);
        if (!REGISTER_INVITE_SCENE.equals(normalizedInviteScene)
            && !TEACHER_PORTAL_INVITE_SCENE.equals(normalizedInviteScene)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码用途仅支持 register 或 teacher_portal");
        }

        List<AuthResponses.InviteCodeItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String codeValue = generateUniqueInviteCode();
            InviteCode row = new InviteCode();
            row.setCode(codeValue);
            row.setInviteScene(normalizedInviteScene);
            row.setStatus("1");
            row.setIssuedByUserId(null);
            row.setIssuedTime(nowUtc());
            row.setUsedTime(null);
            row.setExpiresTime(expiresDays == null ? null : nowUtc().plusDays(expiresDays));
            row.setNote(CharSequenceUtil.isBlank(note) ? null : note.trim());
            row.setDeleteFlag("1");
            row.setCreateTime(nowUtc());
            row.setUpdateTime(nowUtc());
            inviteCodeMapper.insert(row);
            items.add(toInviteCodeItem(row));
        }
        return new AuthResponses.InviteCodeItemsResponse(items);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.InviteCodeItem issueInviteCode(String code, String mobile) {
        validateMobile(mobile);
        InviteCode row = requireInviteCode(normalizeInviteCode(code, "邀请码不能为空"), "邀请码不存在");
        if (!"1".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码不可发放（已使用或已禁用）");
        }
        row.setIssuedToMobile(mobile);
        row.setIssuedByUserId(null);
        row.setIssuedTime(nowUtc());
        row.setUpdateTime(nowUtc());
        inviteCodeMapper.update(row);
        return toInviteCodeItem(row);
    }

    /**
     * {@inheritDoc}
     */
    public AuthResponses.InviteCodeListResponse listInviteCodes(
        String status,
        String mobile,
        String codeKeyword,
        String inviteScene,
        int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<InviteCode> rows = inviteCodeMapper.list(
            blankToNull(status),
            blankToNull(mobile),
            blankToNull(codeKeyword),
            blankToNull(inviteScene),
            safeLimit
        );
        long total = inviteCodeMapper.count(
            blankToNull(status),
            blankToNull(mobile),
            blankToNull(codeKeyword),
            blankToNull(inviteScene)
        );
        List<AuthResponses.InviteCodeItem> items = rows.stream().map(this::toInviteCodeItem).toList();
        return new AuthResponses.InviteCodeListResponse(total, items);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.InviteCodeItem updateInviteCodeStatus(String code, String targetStatus) {
        if (!List.of("1", "2", "3").contains(targetStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "状态仅支持 1、2、3");
        }
        InviteCode row = requireInviteCode(normalizeInviteCode(code, "邀请码不能为空"), "邀请码不存在");
        row.setStatus(targetStatus);
        if ("1".equals(targetStatus)) {
            row.setUsedByUserId(null);
            row.setUsedTime(null);
        } else if ("2".equals(targetStatus) && row.getUsedTime() == null) {
            row.setUsedTime(nowUtc());
        }
        row.setUpdateTime(nowUtc());
        inviteCodeMapper.update(row);
        return toInviteCodeItem(row);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.UserStatusResponse updateUserStatus(String targetStatus, String userId, String mobile) {
        if (!List.of("active", "disabled").contains(targetStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户状态仅支持 active 或 disabled");
        }
        User user = null;
        if (CharSequenceUtil.isNotBlank(userId)) {
            user = getActiveUserById(userId);
        } else if (CharSequenceUtil.isNotBlank(mobile)) {
            user = getActiveUserByMobile(mobile.trim());
        }
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
        }
        user.setStatus(targetStatus);
        user.setUpdateTime(nowUtc());
        userMapper.update(user);
        return new AuthResponses.UserStatusResponse(user.getId(), user.getMobile(), user.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    public void createLoginLog(
        String loginType,
        String loginIdentifier,
        boolean success,
        String userId,
        String failureReason,
        String ip,
        String userAgent
    ) {
        UserLoginLog log = new UserLoginLog();
        log.setUserId(userId);
        log.setLoginType(loginType);
        log.setLoginIdentifier(loginIdentifier);
        log.setSuccess(success ? 1 : 0);
        log.setFailureReason(failureReason);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setCreateTime(nowUtc());
        log.setUpdateTime(nowUtc());
        log.setDeleteFlag("1");
        userLoginLogMapper.insert(log);
    }

    private User getActiveUserById(String userId) {
        return CharSequenceUtil.isBlank(userId) ? null : userMapper.findActiveById(userId);
    }

    private User getActiveUserByMobile(String mobile) {
        return CharSequenceUtil.isBlank(mobile) ? null : userMapper.findActiveByMobile(mobile);
    }

    private User requireActiveUser(String userId) {
        User user = getActiveUserById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户已被禁用");
        }
        return user;
    }

    private User getActiveUserByWechatOpenId(String openid) {
        UserAuthIdentity identity = userAuthIdentityMapper.findActiveByTypeAndKey(WECHAT_OPEN_IDENTITY_TYPE, openid);
        if (identity == null) {
            return null;
        }
        return getActiveUserById(identity.getUserId());
    }

    private UserAuthIdentity getActiveWechatIdentityByUserId(String userId) {
        return userAuthIdentityMapper.findActiveByUserIdAndType(userId, WECHAT_OPEN_IDENTITY_TYPE);
    }

    private void applyWechatProfileToUser(User user, WechatService.WechatUserInfo userInfo) {
        if (userInfo == null) {
            return;
        }
        if (CharSequenceUtil.isNotBlank(userInfo.nickname())) {
            user.setNickname(userInfo.nickname());
        }
        if (CharSequenceUtil.isNotBlank(userInfo.headimgurl())) {
            user.setAvatarUrl(userInfo.headimgurl());
        }
        if (userInfo.sex() != null) {
            user.setSex(userInfo.sex());
        }
        if (CharSequenceUtil.isNotBlank(userInfo.province())) {
            user.setProvince(userInfo.province());
        }
        if (CharSequenceUtil.isNotBlank(userInfo.city())) {
            user.setCity(userInfo.city());
        }
        if (CharSequenceUtil.isNotBlank(userInfo.country())) {
            user.setCountry(userInfo.country());
        }
    }

    private InviteCode precheckInviteCodeForRegister(String code, String registerMobile) {
        String normalizedCode = normalizeInviteCode(code, "新用户注册需要填写邀请码");
        InviteCode row = requireInviteCode(normalizedCode, "邀请码不存在");
        if (!REGISTER_INVITE_SCENE.equals(normalizeInviteScene(row.getInviteScene()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该邀请码不能用于注册");
        }
        if (!"1".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码已使用或已失效");
        }
        if (isExpired(row.getExpiresTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码已过期");
        }
        if (CharSequenceUtil.isNotBlank(row.getIssuedToMobile())
            && !CharSequenceUtil.equals(row.getIssuedToMobile(), registerMobile)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码与手机号不匹配");
        }
        return row;
    }

    private InviteCode consumeInviteCodeForRegister(String code, String registerMobile) {
        InviteCode row = precheckInviteCodeForRegister(code, registerMobile);
        if (CharSequenceUtil.isBlank(row.getIssuedToMobile())) {
            row.setIssuedToMobile(registerMobile);
        }
        row.setStatus("2");
        row.setUsedTime(nowUtc());
        row.setUpdateTime(nowUtc());
        return row;
    }

    private InviteCode consumeInviteCodeForWechatRegister(String code) {
        String normalizedCode = normalizeInviteCode(code, "请填写邀请码");
        InviteCode row = requireInviteCode(normalizedCode, "邀请码不存在");
        if (!REGISTER_INVITE_SCENE.equals(normalizeInviteScene(row.getInviteScene()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前邀请码不能用于微信注册");
        }
        if (!"1".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码已使用或已失效");
        }
        if (isExpired(row.getExpiresTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邀请码已过期");
        }
        if (CharSequenceUtil.isNotBlank(row.getIssuedToMobile())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该邀请码已绑定手机号，请使用未发放手机号的邀请码");
        }
        row.setStatus("2");
        row.setUsedTime(nowUtc());
        row.setUpdateTime(nowUtc());
        return row;
    }

    private InviteCode requireInviteCode(String code, String notFoundMessage) {
        InviteCode row = inviteCodeMapper.findActiveByCode(code);
        if (row == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, notFoundMessage);
        }
        return row;
    }

    private User registerUserByMobile(String mobile) {
        User user = new User();
        user.setId(IdUtil.randomUUID());
        user.setMobile(mobile);
        user.setMobileVerified(1);
        user.setStatus("active");
        user.setIsTempWechatUser(0);
        user.setIsTeacher("0");
        user.setDeleteFlag("1");
        user.setCreateTime(nowUtc());
        user.setUpdateTime(nowUtc());
        userMapper.insert(user);
        return user;
    }

    private AuthResponses.LoginResponse buildLoginResponse(User user) {
        return new AuthResponses.LoginResponse(
            jwtService.createAccessToken(user.getId()),
            "bearer",
            user.getId(),
            user.getMobile()
        );
    }

    private void validateMobile(String mobile) {
        if (CharSequenceUtil.isBlank(mobile) || !Validator.isMobile(mobile)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入正确的手机号");
        }
    }

    private String normalizeInviteCode(String inviteCode, String emptyMessage) {
        if (CharSequenceUtil.isBlank(inviteCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, emptyMessage);
        }
        return inviteCode.trim().toUpperCase();
    }

    private String normalizeInviteScene(String inviteScene) {
        return CharSequenceUtil.isBlank(inviteScene) ? REGISTER_INVITE_SCENE : inviteScene.trim();
    }

    private boolean isExpired(LocalDateTime expiresTime) {
        return expiresTime != null && expiresTime.isBefore(nowUtc());
    }

    private String blankToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }

    private String generateUniqueInviteCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            String candidate = RandomUtil.randomString(alphabet, 10);
            if (inviteCodeMapper.findActiveByCode(candidate) == null) {
                return candidate;
            }
        }
    }

    private AuthResponses.InviteCodeItem toInviteCodeItem(InviteCode row) {
        return new AuthResponses.InviteCodeItem(
            row.getCode(),
            row.getInviteScene(),
            row.getStatus(),
            row.getIssuedToMobile(),
            row.getUsedByUserId(),
            row.getIssuedTime(),
            row.getUsedTime(),
            row.getExpiresTime(),
            row.getNote()
        );
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
