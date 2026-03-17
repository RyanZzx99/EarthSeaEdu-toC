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

# 导入 URL 编码工具
from urllib.parse import urlencode

# 导入数据库 Session
from sqlalchemy.orm import Session

# 导入数据库依赖
from backend.config.db_conf import get_db

# 导入系统配置
from backend.config.db_conf import settings

# 导入请求/响应 schema
from backend.schemas.auth import BindMobileRequiredResponse
from backend.schemas.auth import LoginResponse
from backend.schemas.auth import PasswordLoginRequest
from backend.schemas.auth import SendSmsCodeRequest
from backend.schemas.auth import SetPasswordRequest
from backend.schemas.auth import SmsLoginRequest
from backend.schemas.auth import GenerateInviteCodesRequest
from backend.schemas.auth import IssueInviteCodeRequest
from backend.schemas.auth import UpdateInviteCodeStatusRequest
from backend.schemas.auth import UpdateUserStatusRequest
from backend.schemas.auth import UserProfileResponse
from backend.schemas.auth import WechatBindMobileRequest
from backend.schemas.auth import WechatLoginRequest

# 导入 service 层方法
from backend.services.auth_service import bind_mobile_for_wechat_user
from backend.services.auth_service import create_login_log
from backend.services.auth_service import create_wechat_state
from backend.services.auth_service import get_user_profile
from backend.services.auth_service import login_by_password
from backend.services.auth_service import login_by_sms
from backend.services.auth_service import login_by_wechat
from backend.services.auth_service import send_and_save_sms_code
from backend.services.auth_service import set_password_for_user
from backend.services.auth_service import create_invite_codes
from backend.services.auth_service import issue_invite_code
from backend.services.auth_service import list_invite_codes
from backend.services.auth_service import update_invite_code_status
from backend.services.auth_service import update_user_status

# 导入微信服务
from backend.services.wechat_service import build_wechat_qr_authorize_url

# 导入 token 工具
from backend.utils.security import decode_token_safe


# 创建路由对象
# 注意：
# 1. 这里不再额外写 prefix
# 2. 由 main.py 统一 include_router 时挂到 /api/v1/auth
router = APIRouter()


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


def get_current_user_id(authorization: str | None) -> int:
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

    # 返回 int 类型用户 ID
    return int(user_id)


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

    # 如果微信回调带了 error，说明授权失败或用户取消
    if error:
        # 拼接前端错误回跳地址
        redirect_url = f"{settings.frontend_login_page_url}?wechat_error={error}"

        # 302 跳转到前端
        return RedirectResponse(url=redirect_url, status_code=302)

    # 如果 code 或 state 缺失，说明微信回调参数不完整
    if not code or not state:
        # 给前端一个可识别的错误参数
        redirect_url = (
            f"{settings.frontend_login_page_url}"
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
    redirect_url = f"{settings.frontend_login_page_url}?{query_string}"

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
        send_and_save_sms_code(
            db=db,
            mobile=payload.mobile,
            biz_type=payload.biz_type,
        )

        # 返回成功
        return {"message": "验证码已发送"}

    except ValueError as e:
        # 业务异常返回 400
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
            bind_user_id=int(bind_user_id),
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
        "status": user.status,
    }


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
