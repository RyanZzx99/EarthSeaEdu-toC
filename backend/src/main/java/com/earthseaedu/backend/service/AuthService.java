package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.auth.AuthResponses;

/**
 * 认证与账号服务，负责登录、注册、邀请码和账号状态管理。
 */
public interface AuthService {

    /**
     * 生成微信登录 state，并写入短期缓存用于回调校验。
     *
     * @return 生成或解析出的字符串结果。
     */
    String createWechatState();

    /**
     * 校验邀请码管理操作使用的管理员密钥。
     *
     * @param adminKey 管理员密钥
     */
    void verifyInviteAdminKey(String adminKey);

    /**
     * 发送短信验证码，并将验证码写入缓存用于后续校验。
     *
     * @param mobile 手机号
     * @param bizType 业务场景
     * @return 生成或解析出的字符串结果。
     */
    String sendAndSaveSmsCode(String mobile, String bizType);

    /**
     * 检查手机号短信登录是否需要邀请码。
     *
     * @param mobile 手机号
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteRequirementCheckResponse checkSmsLoginInviteRequirement(String mobile);

    /**
     * 检查微信账号绑定手机号时是否需要邀请码。
     *
     * @param bindUserId 待绑定微信临时用户 ID
     * @param mobile 手机号
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteRequirementCheckResponse checkWechatBindInviteRequirement(String bindUserId, String mobile);

    /**
     * 使用手机号和密码完成登录。
     *
     * @param mobile 手机号
     * @param password 登录密码
     * @return 处理后的响应对象。
     */
    AuthResponses.LoginResponse loginByPassword(String mobile, String password);

    /**
     * 使用邀请码注册手机号账号，并在注册成功后登录。
     *
     * @param mobile 手机号
     * @param password 登录密码
     * @param inviteCode 邀请码
     * @return 处理后的响应对象。
     */
    AuthResponses.LoginResponse registerAndLoginByInvitePassword(String mobile, String password, String inviteCode);

    /**
     * 使用短信验证码完成登录，首次注册时可消费邀请码。
     *
     * @param mobile 手机号
     * @param code 验证码、邀请码或回调 code
     * @param inviteCode 邀请码
     * @return 处理后的响应对象。
     */
    AuthResponses.LoginResponse loginBySms(String mobile, String code, String inviteCode);

    /**
     * 根据微信回调 code 和 state 完成微信登录或返回后续绑定状态。
     *
     * @param code 验证码、邀请码或回调 code
     * @param state 微信登录 state
     * @return 业务处理后的响应对象。
     */
    Object loginByWechat(String code, String state);

    /**
     * 使用邀请码激活临时微信用户并完成登录。
     *
     * @param registerUserId 待激活微信临时用户 ID
     * @param inviteCode 邀请码
     * @return 处理后的响应对象。
     */
    AuthResponses.LoginResponse registerWechatUserByInvite(String registerUserId, String inviteCode);

    /**
     * 为微信临时用户绑定手机号，并在需要时消费邀请码。
     *
     * @param bindUserId 待绑定微信临时用户 ID
     * @param mobile 手机号
     * @param code 验证码、邀请码或回调 code
     * @param inviteCode 邀请码
     * @return 处理后的响应对象。
     */
    AuthResponses.LoginResponse bindMobileForWechatUser(
        String bindUserId,
        String mobile,
        String code,
        String inviteCode
    );

    /**
     * 为当前用户设置或修改登录密码。
     *
     * @param userId 用户 ID
     * @param newPassword 新密码
     * @param currentPassword 当前密码
     */
    void setPasswordForUser(String userId, String newPassword, String currentPassword);

    /**
     * 读取当前用户的账号资料。
     *
     * @param userId 用户 ID
     * @return 处理后的响应对象。
     */
    AuthResponses.UserProfileResponse getUserProfile(String userId);

    /**
     * 使用教师端邀请码激活当前用户的教师入口权限。
     *
     * @param userId 用户 ID
     * @param inviteCode 邀请码
     * @return 处理后的响应对象。
     */
    AuthResponses.TeacherPortalActivateResponse activateTeacherPortalWithInvite(String userId, String inviteCode);

    /**
     * 为当前登录用户绑定手机号。
     *
     * @param userId 用户 ID
     * @param mobile 手机号
     * @return 处理后的响应对象。
     */
    AuthResponses.MobileBindResponse bindMobileForCurrentUser(String userId, String mobile);

    /**
     * 通过短信验证码重置当前用户密码。
     *
     * @param userId 用户 ID
     * @param mobile 手机号
     * @param code 验证码、邀请码或回调 code
     * @param newPassword 新密码
     */
    void resetPasswordForUserBySms(String userId, String mobile, String code, String newPassword);

    /**
     * 检查新密码是否满足当前用户设置密码的条件。
     *
     * @param userId 用户 ID
     * @param newPassword 新密码
     * @return 处理后的响应对象。
     */
    AuthResponses.AvailabilityCheckResponse checkPasswordAvailability(String userId, String newPassword);

    /**
     * 检查短信重置密码场景下的新密码是否可用。
     *
     * @param userId 用户 ID
     * @param newPassword 新密码
     * @return 处理后的响应对象。
     */
    AuthResponses.AvailabilityCheckResponse checkResetPasswordAvailability(String userId, String newPassword);

    /**
     * 批量创建邀请码。
     *
     * @param count 创建数量
     * @param expiresDays 有效天数
     * @param note 备注
     * @param inviteScene 邀请码使用场景
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteCodeItemsResponse createInviteCodes(
        int count,
        Integer expiresDays,
        String note,
        String inviteScene
    );

    /**
     * 向指定手机号签发或绑定邀请码。
     *
     * @param code 验证码、邀请码或回调 code
     * @param mobile 手机号
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteCodeItem issueInviteCode(String code, String mobile);

    /**
     * 按筛选条件查询邀请码列表。
     *
     * @param status 状态筛选或目标状态
     * @param mobile 手机号
     * @param codeKeyword 邀请码关键字
     * @param inviteScene 邀请码使用场景
     * @param limit 最大返回条数
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteCodeListResponse listInviteCodes(
        String status,
        String mobile,
        String codeKeyword,
        String inviteScene,
        int limit
    );

    /**
     * 更新指定邀请码的状态。
     *
     * @param code 验证码、邀请码或回调 code
     * @param targetStatus 目标状态
     * @return 处理后的响应对象。
     */
    AuthResponses.InviteCodeItem updateInviteCodeStatus(String code, String targetStatus);

    /**
     * 按用户 ID 或手机号更新用户状态。
     *
     * @param targetStatus 目标状态
     * @param userId 用户 ID
     * @param mobile 手机号
     * @return 处理后的响应对象。
     */
    AuthResponses.UserStatusResponse updateUserStatus(String targetStatus, String userId, String mobile);

    /**
     * 记录一次登录尝试及其成功或失败原因。
     *
     * @param loginType 登录方式
     * @param loginIdentifier 登录标识
     * @param success 是否登录成功
     * @param userId 用户 ID
     * @param failureReason 失败原因
     * @param ip 客户端 IP
     * @param userAgent 客户端 User-Agent
     */
    void createLoginLog(
        String loginType,
        String loginIdentifier,
        boolean success,
        String userId,
        String failureReason,
        String ip,
        String userAgent
    );
}
