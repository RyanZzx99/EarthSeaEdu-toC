"""
认证模块路由定义

重要说明：
1. 本文件按你当前真实表结构对应的 service 逻辑编写
2. 数据库真实字段差异已经封装在 models / services 中
3. router 层主要负责：
   - 接收入参
   - 调 service
   - 做登录态解析
   - 返回标准响应
   - 记录接口层面的错误响应
4. 当前已支持三种登录方式：
   - 手机号 + 密码登录
   - 手机验证码登录
   - 微信 PC 网站扫码登录
5. 微信首次登录必须绑定手机号
"""

# 导入 FastAPI 路由相关对象
from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from fastapi import Request

# 导入重定向响应
from fastapi.responses import RedirectResponse

# 导入 URL 编码与解析工具
from urllib.parse import urlencode
from urllib.parse import urlparse

# 导入数据库 Session
from sqlalchemy.orm import Session

# 导入数据库依赖
from backend.config.db_conf import get_db

# 导入系统配置
from backend.config.db_conf import settings

# 导入请求/响应 schema
from backend.schemas.auth import BindMobileRequiredResponse
from backend.schemas.auth import AvailabilityCheckResponse
from backend.schemas.auth import CheckNicknameAvailabilityRequest
from backend.schemas.auth import CheckPasswordAvailabilityRequest
from backend.schemas.auth import CheckResetPasswordAvailabilityRequest
from backend.schemas.auth import InviteRequirementCheckResponse
from backend.schemas.auth import LoginResponse
from backend.schemas.auth import PasswordLoginRequest
from backend.schemas.auth import SendSmsCodeRequest
from backend.schemas.auth import SetPasswordRequest
from backend.schemas.auth import ResetPasswordBySmsRequest
from backend.schemas.auth import SmsInviteRequirementCheckRequest
from backend.schemas.auth import SmsLoginRequest
from backend.schemas.auth import GenerateInviteCodesRequest
from backend.schemas.auth import IssueInviteCodeRequest
from backend.schemas.auth import UpdateNicknameRequest
from backend.schemas.auth import CreateNicknameRuleGroupRequest
from backend.schemas.auth import CreateNicknameWordRuleRequest
from backend.schemas.auth import CreateNicknameContactPatternRequest
from backend.schemas.auth import UpdateNicknameRuleTargetStatusRequest
from backend.schemas.auth import UpdateInviteCodeStatusRequest
from backend.schemas.auth import UpdateUserStatusRequest
from backend.schemas.auth import UserProfileResponse
from backend.schemas.auth import WechatBindInviteRequirementCheckRequest
from backend.schemas.auth import WechatBindMobileRequest
from backend.schemas.auth import WechatLoginRequest

# 导入 service 层方法
from backend.services.auth_service import bind_mobile_for_wechat_user
from backend.services.auth_service import check_sms_login_invite_requirement
from backend.services.auth_service import check_wechat_bind_invite_requirement
from backend.services.auth_service import create_login_log
from backend.services.auth_service import create_wechat_state
from backend.services.auth_service import get_user_profile
from backend.services.auth_service import login_by_password
from backend.services.auth_service import login_by_sms
from backend.services.auth_service import login_by_wechat
from backend.services.auth_service import send_and_save_sms_code
from backend.services.auth_service import check_nickname_availability
from backend.services.auth_service import check_password_availability
from backend.services.auth_service import check_reset_password_availability
from backend.services.auth_service import set_password_for_user
from backend.services.auth_service import reset_password_for_user_by_sms
from backend.services.auth_service import update_nickname_for_user
from backend.services.auth_service import create_invite_codes
from backend.services.auth_service import issue_invite_code
from backend.services.auth_service import list_invite_codes
from backend.services.auth_service import update_invite_code_status
from backend.services.auth_service import update_user_status
from backend.services.nickname_guard_service import create_nickname_contact_pattern
from backend.services.nickname_guard_service import create_nickname_rule_group
from backend.services.nickname_guard_service import create_nickname_word_rule
from backend.services.nickname_guard_service import list_nickname_audit_logs
from backend.services.nickname_guard_service import list_nickname_contact_patterns
from backend.services.nickname_guard_service import list_nickname_rule_groups
from backend.services.nickname_guard_service import list_nickname_word_rules
from backend.services.nickname_guard_service import update_nickname_rule_target_status

# 导入微信服务
from backend.services.wechat_service import build_wechat_qr_authorize_url

# 导入 token 工具
from backend.utils.security import decode_token_safe


# 创建路由对象
# 注意：
# 1. 这里不再额外写 prefix
# 2. 由 main.py 统一 include_router 时挂到 /api/v1/auth
router = APIRouter()


def _build_login_page_url_from_redirect_uri() -> str | None:
    """
    根据微信后端回调地址推导前端登录页地址。

    规则：
    1. 只取 redirect_uri 的协议 + host + port
    2. 统一拼成 /login
    3. 只在 redirect_uri 可解析时返回
    """
    redirect_uri = (settings.wechat_open_redirect_uri or "").strip()
    if not redirect_uri:
        return None

    parsed = urlparse(redirect_uri)
    if not parsed.scheme or not parsed.netloc:
        return None

    return f"{parsed.scheme}://{parsed.netloc}/login"


def _resolve_frontend_login_page_url() -> str:
    """
    解析微信登录回跳到前端时应使用的登录页地址。

    优先级：
    1. 如果显式配置了非 localhost 的 FRONTEND_LOGIN_PAGE_URL，则直接使用
    2. 否则优先根据 WECHAT_OPEN_REDIRECT_URI 自动推导同域 /login
    3. 最后再回退到配置值或默认 localhost
    """
    configured_login_url = (settings.frontend_login_page_url or "").strip()
    derived_login_url = _build_login_page_url_from_redirect_uri()

    if configured_login_url and "localhost" not in configured_login_url and "127.0.0.1" not in configured_login_url:
        return configured_login_url.rstrip("/")

    if derived_login_url:
        return derived_login_url.rstrip("/")

    if configured_login_url:
        return configured_login_url.rstrip("/")

    return "http://localhost:5173/login"


def verify_invite_admin_key(x_admin_key: str | None) -> None:
    """
    校验邀请码管理接口管理员密钥

    说明：
    1. 只用于保护邀请码生成/发放接口
    2. 密钥从配置 invite_admin_key 读取
    """
    if not settings.invite_admin_key:
        raise HTTPException(status_code=500, detail="系统未配置邀请码管理密钥")

    if x_admin_key != settings.invite_admin_key:
        raise HTTPException(status_code=401, detail="管理员密钥错误")


def extract_bearer_token(authorization: str | None) -> str | None:
    """
    从 Authorization 请求头中提取 Bearer Token

    参数示例：
        Authorization: Bearer xxxxxxx

    返回：
    1. 提取成功则返回 token 字符串
    2. 如果请求头为空或格式不对，则返回 None
    """
    # 如果请求头不存在，则直接返回 None
    if not authorization:
        return None

    # 如果不是 Bearer 开头，说明格式不符合约定
    if not authorization.startswith("Bearer "):
        return None

    # 去掉 Bearer 前缀并返回真实 token
    return authorization.replace("Bearer ", "", 1).strip()


def get_current_user_id(authorization: str | None) -> str:
    """
    从 access token 中解析当前登录用户 ID

    说明：
    1. 当前系统使用 JWT 无状态登录
    2. token 中的 sub 存的是 user_id
    3. 这里只解析正式 access token，不接受 bind_token
    """
    # 先从请求头中提取 Bearer token
    token = extract_bearer_token(authorization)

    # token 不存在则直接未登录
    if not token:
        raise HTTPException(status_code=401, detail="未登录或 token 缺失")

    # 安全解码 token
    payload = decode_token_safe(token)

    # 解码失败说明 token 无效或过期
    if not payload:
        raise HTTPException(status_code=401, detail="token 无效或已过期")

    # 校验 token 类型
    # 这里必须是 access token
    if payload.get("token_use") != "access":
        raise HTTPException(status_code=401, detail="token 类型错误")

    # 取出 sub 字段
    user_id = payload.get("sub")

    # 没取到用户信息，说明 token 结构不完整
    if not user_id:
        raise HTTPException(status_code=401, detail="token 缺少用户信息")

    # 中文注释：当前用户主键已切换为 UUID 字符串，因此这里直接返回原始 sub
    return str(user_id)


@router.get("/wechat/authorize-url")
def get_wechat_authorize_url(db: Session = Depends(get_db)):
    """
    获取微信 PC 网站扫码登录地址

    流程：
    1. 后端生成 state
    2. state 入库（wechat_login_states）
    3. 拼接微信扫码地址
    4. 返回给前端
    """
    # 创建微信扫码 state，并写入数据库
    state = create_wechat_state(db)

    # 构造微信扫码地址
    authorize_url = build_wechat_qr_authorize_url(state)

    # 返回扫码地址给前端
    return {
        "authorize_url": authorize_url,
        "state": state,
    }


@router.get("/wechat/callback")
def wechat_callback(
    code: str | None = Query(default=None, description="微信回调 code"),
    state: str | None = Query(default=None, description="微信回调 state"),
    error: str | None = Query(default=None, description="微信回调错误参数"),
):
    """
    微信扫码登录回调中转接口

    说明：
    1. 该地址配置给微信开放平台
    2. 微信扫码成功后会回调这个接口
    3. 这个接口不直接完成登录
    4. 它的职责只是：
       - 接住微信回调参数
       - 再 302 重定向到前端登录页
    5. 前端登录页拿到 code/state 后，再调用 /login/wechat 完成真正登录
    """

    frontend_login_page_url = _resolve_frontend_login_page_url()

    # 如果微信回调带了 error，说明授权失败或用户取消
    if error:
        # 拼接前端错误回跳地址
        redirect_url = f"{frontend_login_page_url}?wechat_error={error}"

        # 302 跳转到前端
        return RedirectResponse(url=redirect_url, status_code=302)

    # 如果 code 或 state 缺失，说明微信回调参数不完整
    if not code or not state:
        # 给前端一个可识别的错误参数
        redirect_url = (
            f"{frontend_login_page_url}"
            f"?wechat_error=missing_code_or_state"
        )

        # 重定向回前端
        return RedirectResponse(url=redirect_url, status_code=302)

    # 把 code / state 安全编码成 query string
    query_string = urlencode(
        {
            "code": code,
            "state": state,
        }
    )

    # 组装前端登录页地址
    redirect_url = f"{frontend_login_page_url}?{query_string}"

    # 返回 302 给浏览器，跳到前端登录页
    return RedirectResponse(url=redirect_url, status_code=302)


@router.post("/sms/send-code")
def send_sms_code_api(
    payload: SendSmsCodeRequest,
    db: Session = Depends(get_db),
):
    """
    发送短信验证码接口

    当前支持业务类型：
    1. login = 手机验证码登录
    2. bind_mobile = 微信登录后绑定手机号
    """
    try:
        # 做业务类型校验，避免传入非法值
        if payload.biz_type not in ["login", "bind_mobile"]:
            raise ValueError("biz_type 仅支持 login 或 bind_mobile")

        # 调 service 发送并保存验证码
        code = send_and_save_sms_code(
            db=db,
            mobile=payload.mobile,
            biz_type=payload.biz_type,
        )

        # 中文注释：
        # 本地 mock 联调时，PyCharm + uvicorn --reload 的多进程模式下控制台不一定稳定显示验证码。
        # 因此在 debug + mock 场景下，把验证码明文附在响应里，方便开发时直接从 Network 查看。
        response_payload = {"message": "验证码已发送"}
        if settings.app_debug and settings.tencentcloud_sms_mock:
            response_payload["debug_code"] = code
        return response_payload

    except ValueError as e:
        # 业务异常返回 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/sms/invite-required", response_model=InviteRequirementCheckResponse)
def check_sms_login_invite_required_api(
    payload: SmsInviteRequirementCheckRequest,
    db: Session = Depends(get_db),
):
    """
    检查短信验证码登录是否需要邀请码

    接口作用：
    1. 给前端一个“手机号预检查”能力
    2. 当前端识别到手机号已经注册过时，不再展示邀请码输入框
    3. 当前端识别到手机号尚未注册时，再提示用户填写邀请码

    说明：
    1. 这个接口只做轻量判断，不发送短信，也不真正执行登录
    2. 真正的登录合法性仍以后续 /login/sms 接口为准
    """
    try:
        return check_sms_login_invite_requirement(
            db=db,
            mobile=payload.mobile,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/wechat/bind-mobile/invite-required", response_model=InviteRequirementCheckResponse)
def check_wechat_bind_invite_required_api(
    payload: WechatBindInviteRequirementCheckRequest,
    db: Session = Depends(get_db),
):
    """
    检查微信绑定手机号时是否需要邀请码

    接口作用：
    1. 当前端处于微信绑定手机号界面时，可先根据手机号判断是否需要邀请码
    2. 如果目标手机号已注册，则前端不再强制展示邀请码输入框
    3. 如果目标手机号未注册，则前端展示邀请码输入框，引导用户完成首次注册
    """
    # 中文注释：这里沿用正式绑定接口里的 bind_token 解析规则，确保前端预检查和正式提交使用同一套身份校验口径。
    bind_payload = decode_token_safe(payload.bind_token)

    if not bind_payload:
        raise HTTPException(status_code=401, detail="bind_token 无效或已过期")

    if bind_payload.get("token_use") != "bind_mobile":
        raise HTTPException(status_code=401, detail="bind_token 类型错误")

    bind_user_id = bind_payload.get("sub")

    if not bind_user_id:
        raise HTTPException(status_code=401, detail="bind_token 缺少用户信息")

    try:
        return check_wechat_bind_invite_requirement(
            db=db,
            bind_user_id=str(bind_user_id),
            mobile=payload.mobile,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/password", response_model=LoginResponse)
def password_login_api(
    payload: PasswordLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    手机号 + 密码登录接口
    """
    try:
        # 调 service 执行登录
        result = login_by_password(
            db=db,
            mobile=payload.mobile,
            password=payload.password,
        )

        # 登录成功写日志
        create_login_log(
            db=db,
            login_type="password",
            login_identifier=payload.mobile,
            success=True,
            user_id=result["user_id"],
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回登录结果
        return result

    except ValueError as e:
        # 登录失败也记录日志
        create_login_log(
            db=db,
            login_type="password",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回业务错误
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/sms", response_model=LoginResponse)
def sms_login_api(
    payload: SmsLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    手机验证码登录接口
    """
    try:
        # 调 service 执行短信登录
        result = login_by_sms(
            db=db,
            mobile=payload.mobile,
            code=payload.code,
            invite_code=payload.invite_code,
        )

        # 登录成功记录日志
        create_login_log(
            db=db,
            login_type="sms",
            login_identifier=payload.mobile,
            success=True,
            user_id=result["user_id"],
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回登录结果
        return result

    except ValueError as e:
        # 登录失败也记录日志
        create_login_log(
            db=db,
            login_type="sms",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回业务错误
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/wechat", response_model=LoginResponse | BindMobileRequiredResponse)
def wechat_login_api(
    payload: WechatLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    微信扫码登录接口

    说明：
    1. 前端从 /login 页面地址栏中拿到 code 和 state
    2. 再调用本接口
    3. 本接口真正处理微信登录逻辑
    4. 返回两种可能：
       - 已绑定手机号：直接返回正式登录结果
       - 未绑定手机号：返回 bind_token，要求继续绑定手机号
    """
    try:
        # 调 service 执行微信登录
        result = login_by_wechat(
            db=db,
            code=payload.code,
            state=payload.state,
        )

        # 如果结果中包含 access_token，说明已经完成正式登录
        if "access_token" in result:
            create_login_log(
                db=db,
                login_type="wechat_open",
                # 这里不建议直接落明文 openid
                # TODO：后续如需更细日志，可改成脱敏后的 openid
                login_identifier="wechat_openid_hidden",
                success=True,
                user_id=result["user_id"],
                ip=request.client.host if request.client else None,
                user_agent=request.headers.get("user-agent"),
            )

        # 返回 service 的结果
        return result

    except ValueError as e:
        # 微信登录失败记录日志
        create_login_log(
            db=db,
            login_type="wechat_open",
            login_identifier=None,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回业务异常
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/wechat/bind-mobile", response_model=LoginResponse)
def wechat_bind_mobile_api(
    payload: WechatBindMobileRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    微信登录后绑定手机号接口

    流程：
    1. 前端提交 bind_token + mobile + code
    2. 后端先校验 bind_token
    3. 再校验 bind_mobile 场景验证码
    4. 再根据手机号是否已有老账号，决定：
       - 直接绑定
       - 合并到老账号
       - 或拒绝绑定
    """
    # 先解码 bind_token
    bind_payload = decode_token_safe(payload.bind_token)

    # token 无效或过期
    if not bind_payload:
        raise HTTPException(status_code=401, detail="bind_token 无效或已过期")

    # token 类型必须是 bind_mobile
    if bind_payload.get("token_use") != "bind_mobile":
        raise HTTPException(status_code=401, detail="bind_token 类型错误")

    # 取绑定用户 ID
    bind_user_id = bind_payload.get("sub")

    # 如果取不到用户信息，说明 token 数据不完整
    if not bind_user_id:
        raise HTTPException(status_code=401, detail="bind_token 缺少用户信息")

    try:
        # 调 service 执行绑定手机号
        result = bind_mobile_for_wechat_user(
            db=db,
            bind_user_id=str(bind_user_id),
            mobile=payload.mobile,
            code=payload.code,
            invite_code=payload.invite_code,
        )

        # 成功日志
        create_login_log(
            db=db,
            login_type="wechat_bind_mobile",
            login_identifier=payload.mobile,
            success=True,
            user_id=result["user_id"],
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回正式登录结果
        return result

    except ValueError as e:
        # 失败日志
        create_login_log(
            db=db,
            login_type="wechat_bind_mobile",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回业务异常
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/password/set")
def set_password_api(
    payload: SetPasswordRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户设置密码接口

    使用场景：
    1. 手机验证码登录成功后首次设置密码
    2. 微信绑定手机号成功后设置密码
    3. 已登录用户修改密码
    """
    # 先从 access token 中解析用户 ID
    user_id = get_current_user_id(authorization)

    try:
        # 调 service 设置密码
        set_password_for_user(
            db=db,
            user_id=user_id,
            new_password=payload.new_password,
            current_password=payload.current_password,
        )

        # 返回成功
        return {"message": "密码设置成功"}

    except ValueError as e:
        # 返回业务异常
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/me", response_model=UserProfileResponse)
def me_api(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    获取当前登录用户信息接口
    """
    # 从 access token 中解析用户 ID
    user_id = get_current_user_id(authorization)

    # 查询当前用户
    user = get_user_profile(db, user_id)

    # 用户不存在
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    # 返回用户资料
    return {
        "user_id": user.id,
        "mobile": user.mobile,
        "nickname": user.nickname,
        "avatar_url": user.avatar_url,
        "sex": user.sex,
        "province": user.province,
        "city": user.city,
        "country": user.country,
        "status": user.status,
        # 中文注释：前端据此判断密码区域默认显示“设置/修改密码”按钮还是直接展开表单
        "has_password": bool(user.password_hash),
    }


@router.post("/me/password/reset-by-sms")
def reset_my_password_by_sms_api(
    payload: ResetPasswordBySmsRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户通过短信验证码重置密码。
    """
    user_id = get_current_user_id(authorization)

    try:
        reset_password_for_user_by_sms(
            db=db,
            user_id=user_id,
            mobile=payload.mobile,
            code=payload.code,
            new_password=payload.new_password,
        )
        return {"message": "密码重置成功"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/me/nickname")
def update_my_nickname_api(
    payload: UpdateNicknameRequest,
    request: Request,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户修改昵称接口
    """
    # 中文注释：昵称修改属于登录态内操作，因此需要先解析当前用户 ID
    user_id = get_current_user_id(authorization)

    try:
        # 中文注释：昵称风控日志需要带上请求来源信息，因此这里把 IP 和 User-Agent 透传到 service 层
        user = update_nickname_for_user(
            db=db,
            user_id=user_id,
            nickname=payload.nickname,
            client_ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )
        return {
            "user_id": user.id,
            "nickname": user.nickname,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/me/nickname/check", response_model=AvailabilityCheckResponse)
def check_my_nickname_api(
    payload: CheckNicknameAvailabilityRequest,
    request: Request,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户检查昵称是否可用接口
    """
    user_id = get_current_user_id(authorization)

    try:
        # 中文注释：昵称“检查”同样记录审核日志，便于区分用户只是试探还是最终真的修改成功
        available, message = check_nickname_availability(
            db=db,
            user_id=user_id,
            nickname=payload.nickname,
            client_ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )
        return {
            "available": available,
            "message": message,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/me/password/check", response_model=AvailabilityCheckResponse)
def check_my_password_api(
    payload: CheckPasswordAvailabilityRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户检查新密码是否可用接口
    """
    user_id = get_current_user_id(authorization)

    try:
        available, message = check_password_availability(
            db=db,
            user_id=user_id,
            new_password=payload.new_password,
            current_password=payload.current_password,
        )
        return {
            "available": available,
            "message": message,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/me/password/check-for-reset", response_model=AvailabilityCheckResponse)
def check_my_reset_password_api(
    payload: CheckResetPasswordAvailabilityRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户在忘记密码场景下检查新密码是否可用
    """
    user_id = get_current_user_id(authorization)

    try:
        available, message = check_reset_password_availability(
            db=db,
            user_id=user_id,
            new_password=payload.new_password,
        )
        return {
            "available": available,
            "message": message,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/logout")
def logout_api():
    """
    退出登录接口

    说明：
    1. 当前 JWT 是无状态 token
    2. 这里主要提供一个统一退出接口给前端调用
    3. 实际退出动作目前主要由前端删除本地 access_token 完成

    TODO：
    1. 如果后续你要做 refresh_token
    2. 或 token 黑名单
    3. 可以在这里继续扩展
    """
    return {"message": "退出成功"}


@router.post("/invite-codes/generate")
def generate_invite_codes_api(
    payload: GenerateInviteCodesRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    生成邀请码接口（管理员）
    """
    # 先校验管理员密钥
    verify_invite_admin_key(x_admin_key)

    try:
        rows = create_invite_codes(
            db=db,
            count=payload.count,
            expires_days=payload.expires_days,
            note=payload.note,
            issued_by_user_id=None,
        )

        return {
            "items": [
                {
                    "code": row.code,
                    "status": row.status,
                    "issued_to_mobile": row.issued_to_mobile,
                    "used_by_user_id": row.used_by_user_id,
                    "issued_time": row.issued_time,
                    "used_time": row.used_time,
                    "expires_time": row.expires_time,
                    "note": row.note,
                }
                for row in rows
            ]
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/invite-codes/issue")
def issue_invite_code_api(
    payload: IssueInviteCodeRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    发放邀请码接口（管理员）
    """
    # 先校验管理员密钥
    verify_invite_admin_key(x_admin_key)

    try:
        row = issue_invite_code(
            db=db,
            code=payload.code,
            mobile=payload.mobile,
            issued_by_user_id=None,
        )

        return {
            "code": row.code,
            "status": row.status,
            "issued_to_mobile": row.issued_to_mobile,
            "used_by_user_id": row.used_by_user_id,
            "issued_time": row.issued_time,
            "used_time": row.used_time,
            "expires_time": row.expires_time,
            "note": row.note,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/invite-codes")
def list_invite_codes_api(
    status: str | None = Query(default=None, description="状态筛选：1=未使用，2=已使用，3=已禁用"),
    mobile: str | None = Query(default=None, description="发放手机号筛选"),
    code_keyword: str | None = Query(default=None, description="邀请码关键字筛选"),
    limit: int = Query(default=50, ge=1, le=200, description="返回数量上限（1~200）"),
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    查询邀请码列表接口（管理员）
    """
    # 先校验管理员密钥
    verify_invite_admin_key(x_admin_key)

    rows, total = list_invite_codes(
        db=db,
        status=status,
        mobile=mobile,
        code_keyword=code_keyword,
        limit=limit,
    )

    return {
        "total": total,
        "items": [
            {
                "code": row.code,
                "status": row.status,
                "issued_to_mobile": row.issued_to_mobile,
                "used_by_user_id": row.used_by_user_id,
                "issued_time": row.issued_time,
                "used_time": row.used_time,
                "expires_time": row.expires_time,
                "note": row.note,
            }
            for row in rows
        ],
    }


@router.post("/invite-codes/update-status")
def update_invite_code_status_api(
    payload: UpdateInviteCodeStatusRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    修改邀请码状态接口（管理员）
    """
    # 先校验管理员密钥
    verify_invite_admin_key(x_admin_key)

    try:
        row = update_invite_code_status(
            db=db,
            code=payload.code,
            target_status=payload.status,
        )

        return {
            "code": row.code,
            "status": row.status,
            "issued_to_mobile": row.issued_to_mobile,
            "used_by_user_id": row.used_by_user_id,
            "issued_time": row.issued_time,
            "used_time": row.used_time,
            "expires_time": row.expires_time,
            "note": row.note,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/users/update-status")
def update_user_status_api(
    payload: UpdateUserStatusRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    修改用户状态接口（管理员）
    """
    # 中文注释：用户状态修改属于后台管理能力，必须校验管理员密钥
    verify_invite_admin_key(x_admin_key)

    try:
        user = update_user_status(
            db=db,
            target_status=payload.status,
            user_id=payload.user_id,
            mobile=payload.mobile,
        )
        return {
            "user_id": user.id,
            "mobile": user.mobile,
            "status": user.status,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/nickname/rule-groups")
def list_nickname_rule_groups_api(
    status: str | None = Query(default=None, description="状态筛选：draft/active/disabled"),
    group_type: str | None = Query(default=None, description="分组类型筛选"),
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    查询昵称规则分组列表接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    rows = list_nickname_rule_groups(
        db=db,
        status=status,
        group_type=group_type,
    )
    return {
        "items": [
            {
                "id": row.id,
                "group_code": row.group_code,
                "group_name": row.group_name,
                "group_type": row.group_type,
                "scope": row.scope,
                "status": row.status,
                "priority": row.priority,
                "description": row.description,
                "version_no": row.version_no,
                "create_time": row.create_time,
                "update_time": row.update_time,
            }
            for row in rows
        ]
    }


@router.post("/nickname/rule-groups")
def create_nickname_rule_group_api(
    payload: CreateNicknameRuleGroupRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    创建昵称规则分组接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    try:
        row = create_nickname_rule_group(
            db=db,
            group_code=payload.group_code,
            group_name=payload.group_name,
            group_type=payload.group_type,
            scope=payload.scope,
            status=payload.status,
            priority=payload.priority,
            description=payload.description,
        )
        return {
            "id": row.id,
            "group_code": row.group_code,
            "group_name": row.group_name,
            "group_type": row.group_type,
            "scope": row.scope,
            "status": row.status,
            "priority": row.priority,
            "description": row.description,
            "version_no": row.version_no,
            "create_time": row.create_time,
            "update_time": row.update_time,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/nickname/word-rules")
def create_nickname_word_rule_api(
    payload: CreateNicknameWordRuleRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    创建昵称词条规则接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    try:
        row = create_nickname_word_rule(
            db=db,
            group_id=payload.group_id,
            word=payload.word,
            match_type=payload.match_type,
            decision=payload.decision,
            status=payload.status,
            priority=payload.priority,
            risk_level=payload.risk_level,
            source=payload.source,
            note=payload.note,
        )
        return {
            "id": row.id,
            "group_id": row.group_id,
            "word": row.word,
            "normalized_word": row.normalized_word,
            "match_type": row.match_type,
            "decision": row.decision,
            "status": row.status,
            "priority": row.priority,
            "risk_level": row.risk_level,
            "source": row.source,
            "note": row.note,
            "version_no": row.version_no,
            "create_time": row.create_time,
            "update_time": row.update_time,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/nickname/word-rules")
def list_nickname_word_rules_api(
    group_id: int | None = Query(default=None, ge=1, description="规则分组ID筛选"),
    status: str | None = Query(default=None, description="状态筛选：draft/active/disabled"),
    decision: str | None = Query(default=None, description="决策筛选：pass/reject/review"),
    keyword: str | None = Query(default=None, description="词条关键字筛选"),
    limit: int = Query(default=50, ge=1, le=200, description="返回数量上限（1~200）"),
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    查询昵称词条规则列表接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    rows, total = list_nickname_word_rules(
        db=db,
        group_id=group_id,
        status=status,
        decision=decision,
        keyword=keyword,
        limit=limit,
    )
    return {
        "total": total,
        "items": [
            {
                "id": row.id,
                "group_id": row.group_id,
                "word": row.word,
                "normalized_word": row.normalized_word,
                "match_type": row.match_type,
                "decision": row.decision,
                "status": row.status,
                "priority": row.priority,
                "risk_level": row.risk_level,
                "source": row.source,
                "note": row.note,
                "version_no": row.version_no,
                "create_time": row.create_time,
                "update_time": row.update_time,
            }
            for row in rows
        ],
    }


@router.post("/nickname/contact-patterns")
def create_nickname_contact_pattern_api(
    payload: CreateNicknameContactPatternRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    创建昵称联系方式规则接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    try:
        row = create_nickname_contact_pattern(
            db=db,
            group_id=payload.group_id,
            pattern_name=payload.pattern_name,
            pattern_type=payload.pattern_type,
            pattern_regex=payload.pattern_regex,
            decision=payload.decision,
            status=payload.status,
            priority=payload.priority,
            risk_level=payload.risk_level,
            normalized_hint=payload.normalized_hint,
            note=payload.note,
        )
        return {
            "id": row.id,
            "group_id": row.group_id,
            "pattern_name": row.pattern_name,
            "pattern_type": row.pattern_type,
            "pattern_regex": row.pattern_regex,
            "decision": row.decision,
            "status": row.status,
            "priority": row.priority,
            "risk_level": row.risk_level,
            "normalized_hint": row.normalized_hint,
            "note": row.note,
            "version_no": row.version_no,
            "create_time": row.create_time,
            "update_time": row.update_time,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/nickname/contact-patterns")
def list_nickname_contact_patterns_api(
    group_id: int | None = Query(default=None, ge=1, description="规则分组ID筛选"),
    status: str | None = Query(default=None, description="状态筛选：draft/active/disabled"),
    pattern_type: str | None = Query(default=None, description="规则类型筛选"),
    keyword: str | None = Query(default=None, description="规则名称或正则关键字筛选"),
    limit: int = Query(default=50, ge=1, le=200, description="返回数量上限（1~200）"),
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    查询昵称联系方式规则列表接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    rows, total = list_nickname_contact_patterns(
        db=db,
        group_id=group_id,
        status=status,
        pattern_type=pattern_type,
        keyword=keyword,
        limit=limit,
    )
    return {
        "total": total,
        "items": [
            {
                "id": row.id,
                "group_id": row.group_id,
                "pattern_name": row.pattern_name,
                "pattern_type": row.pattern_type,
                "pattern_regex": row.pattern_regex,
                "decision": row.decision,
                "status": row.status,
                "priority": row.priority,
                "risk_level": row.risk_level,
                "normalized_hint": row.normalized_hint,
                "note": row.note,
                "version_no": row.version_no,
                "create_time": row.create_time,
                "update_time": row.update_time,
            }
            for row in rows
        ],
    }


@router.post("/nickname/rules/update-status")
def update_nickname_rule_target_status_api(
    payload: UpdateNicknameRuleTargetStatusRequest,
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    修改昵称规则目标状态接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    try:
        row = update_nickname_rule_target_status(
            db=db,
            target_type=payload.target_type,
            target_id=payload.target_id,
            status=payload.status,
        )
        return {
            "id": row.id,
            "status": row.status,
            "update_time": row.update_time,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/nickname/audit-logs")
def list_nickname_audit_logs_api(
    decision: str | None = Query(default=None, description="结果筛选：pass/reject/review"),
    scene: str | None = Query(default=None, description="场景筛选：check/update"),
    hit_group_code: str | None = Query(default=None, description="规则分组编码筛选"),
    limit: int = Query(default=50, ge=1, le=200, description="返回数量上限（1~200）"),
    x_admin_key: str | None = Header(default=None, alias="X-Admin-Key"),
    db: Session = Depends(get_db),
):
    """
    查询昵称审核日志接口（管理员）
    """
    verify_invite_admin_key(x_admin_key)

    rows, total = list_nickname_audit_logs(
        db=db,
        decision=decision,
        scene=scene,
        hit_group_code=hit_group_code,
        limit=limit,
    )
    return {
        "total": total,
        "items": [
            {
                "id": row.id,
                "trace_id": row.trace_id,
                "user_id": row.user_id,
                "scene": row.scene,
                "raw_nickname": row.raw_nickname,
                "normalized_nickname": row.normalized_nickname,
                "decision": row.decision,
                "hit_source": row.hit_source,
                "hit_rule_id": row.hit_rule_id,
                "hit_pattern_id": row.hit_pattern_id,
                "hit_group_code": row.hit_group_code,
                "hit_content": row.hit_content,
                "message": row.message,
                "client_ip": row.client_ip,
                "user_agent": row.user_agent,
                "app_version": row.app_version,
                "rule_version_batch": row.rule_version_batch,
                "extra_json": row.extra_json,
                "create_time": row.create_time,
            }
            for row in rows
        ],
    }
