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

# 导入 SQLAlchemy Session
from sqlalchemy.orm import Session

# 导入数据库依赖
from backend.config.db_conf import get_db

# 导入项目配置
from backend.config.db_conf import settings

# 导入 schema
from backend.schemas.auth import LoginResponse
from backend.schemas.auth import PasswordLoginRequest
from backend.schemas.auth import SendSmsCodeRequest
from backend.schemas.auth import SetPasswordRequest
from backend.schemas.auth import SmsLoginRequest
from backend.schemas.auth import UserProfileResponse
from backend.schemas.auth import WechatBindMobileRequest
from backend.schemas.auth import WechatLoginRequest

# 导入 service
from backend.services.auth_service import bind_mobile_for_wechat_user
from backend.services.auth_service import create_login_log
from backend.services.auth_service import create_wechat_state
from backend.services.auth_service import get_user_profile
from backend.services.auth_service import login_by_password
from backend.services.auth_service import login_by_sms
from backend.services.auth_service import login_by_wechat
from backend.services.auth_service import send_and_save_sms_code
from backend.services.auth_service import set_password_for_user

# 导入微信服务
from backend.services.wechat_service import build_wechat_qr_authorize_url

# 导入 token 工具
from backend.utils.security import decode_token_safe


# 创建路由对象
router = APIRouter()


def extract_bearer_token(authorization: str | None) -> str | None:
    """
    从 Authorization 请求头中提取 Bearer Token
    """
    # 如果请求头为空，则返回 None
    if not authorization:
        return None

    # 如果不是 Bearer 开头，则格式不合法
    if not authorization.startswith("Bearer "):
        return None

    # 去掉 Bearer 前缀后返回 token
    return authorization.replace("Bearer ", "", 1).strip()


def get_current_user_id(authorization: str | None) -> int:
    """
    从 access token 中获取当前用户 ID
    """
    # 提取 token
    token = extract_bearer_token(authorization)

    # token 不存在
    if not token:
        raise HTTPException(status_code=401, detail="未登录或 token 缺失")

    # 解码 token
    payload = decode_token_safe(token)

    # token 无效
    if not payload:
        raise HTTPException(status_code=401, detail="token 无效或已过期")

    # token 类型必须是 access
    if payload.get("token_use") != "access":
        raise HTTPException(status_code=401, detail="token 类型错误")

    # 取用户 ID
    user_id = payload.get("sub")

    # 如果没有用户 ID
    if not user_id:
        raise HTTPException(status_code=401, detail="token 缺少用户信息")

    # 转成 int 返回
    return int(user_id)


@router.get("/wechat/authorize-url")
def get_wechat_authorize_url(db: Session = Depends(get_db)):
    """
    获取微信扫码登录地址

    流程：
    1 后端生成 state
    2 state 入库
    3 拼接微信扫码地址
    4 返回给前端
    """
    # 创建 state 并写入数据库
    state = create_wechat_state(db)

    # 构造微信扫码地址
    url = build_wechat_qr_authorize_url(state)

    # 返回给前端
    return {
        "authorize_url": url,
        "state": state,
    }


@router.get("/wechat/callback")
def wechat_callback(
    code: str | None = Query(default=None, description="微信回调 code"),
    state: str | None = Query(default=None, description="微信回调 state"),
    error: str | None = Query(default=None, description="微信回调错误信息"),
):
    """
    微信扫码登录回调中转接口

    说明：
    1 这个地址要配置到微信开放平台后台
    2 微信扫码成功后会回调到这里
    3 这个接口不直接完成登录
    4 这个接口只负责把 code/state 302 跳转给前端登录页
    5 前端拿到 code/state 后，再调用 /login/wechat 完成登录

    为什么这样做：
    - 微信平台通常更适合回调后端地址
    - 后端更容易统一处理日志、错误和安全控制
    - 前端只负责后续页面交互
    """

    # 如果微信返回 error，说明用户拒绝授权或扫码异常
    if error:
        # 拼接前端错误跳转地址
        redirect_url = (
            f"{settings.frontend_login_page_url}"
            f"?wechat_error={error}"
        )

        # 302 跳回前端
        return RedirectResponse(url=redirect_url, status_code=302)

    # 如果没有 code 或 state，说明回调参数不完整
    if not code or not state:
        # 把错误信息传给前端
        redirect_url = (
            f"{settings.frontend_login_page_url}"
            f"?wechat_error=missing_code_or_state"
        )

        # 重定向到前端
        return RedirectResponse(url=redirect_url, status_code=302)

    # 把 code 和 state 安全拼到前端地址
    # 注意：这里不建议做复杂逻辑，只做中转
    query_string = urlencode(
        {
            "code": code,
            "state": state,
        }
    )

    # 组装前端跳转地址
    redirect_url = f"{settings.frontend_login_page_url}?{query_string}"

    # 302 跳转到前端登录页
    return RedirectResponse(url=redirect_url, status_code=302)


@router.post("/sms/send-code")
def send_sms_code_api(payload: SendSmsCodeRequest, db: Session = Depends(get_db)):
    """
    发送短信验证码
    """
    try:
        # 校验业务类型
        if payload.biz_type not in ["login", "bind_mobile"]:
            raise ValueError("biz_type 仅支持 login 或 bind_mobile")

        # 调 service 发送并保存验证码
        send_and_save_sms_code(db, payload.mobile, payload.biz_type)

        # 返回成功信息
        return {"message": "验证码已发送"}

    except ValueError as e:
        # 业务异常转为 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/password", response_model=LoginResponse)
def password_login_api(
    payload: PasswordLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    手机号 + 密码登录
    """
    try:
        # 执行登录
        result = login_by_password(db, payload.mobile, payload.password)

        # 记录成功日志
        create_login_log(
            db=db,
            login_type="password",
            login_identifier=payload.mobile,
            success=True,
            user_id=result["user_id"],
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回结果
        return result

    except ValueError as e:
        # 记录失败日志
        create_login_log(
            db=db,
            login_type="password",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 抛 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/sms", response_model=LoginResponse)
def sms_login_api(
    payload: SmsLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    手机验证码登录
    """
    try:
        # 调 service 执行登录
        result = login_by_sms(db, payload.mobile, payload.code)

        # 写成功日志
        create_login_log(
            db=db,
            login_type="sms",
            login_identifier=payload.mobile,
            success=True,
            user_id=result["user_id"],
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 返回结果
        return result

    except ValueError as e:
        # 写失败日志
        create_login_log(
            db=db,
            login_type="sms",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 抛 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login/wechat")
def wechat_login_api(
    payload: WechatLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    微信扫码登录

    说明：
    1 前端从地址栏拿到后端回跳带来的 code/state
    2 前端调这个接口
    3 后端在这里真正处理微信登录
    """
    try:
        # 调 service 登录
        result = login_by_wechat(db, payload.code, payload.state)

        # 如果返回的是正式 token，说明已完成登录
        if "access_token" in result:
            create_login_log(
                db=db,
                login_type="wechat_open",
                login_identifier="wechat_openid_hidden",
                success=True,
                user_id=result["user_id"],
                ip=request.client.host if request.client else None,
                user_agent=request.headers.get("user-agent"),
            )

        # 返回结果
        return result

    except ValueError as e:
        # 写失败日志
        create_login_log(
            db=db,
            login_type="wechat_open",
            login_identifier=None,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 抛 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/wechat/bind-mobile", response_model=LoginResponse)
def wechat_bind_mobile_api(
    payload: WechatBindMobileRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    """
    微信账号绑定手机号
    """
    # 解码 bind token
    bind_payload = decode_token_safe(payload.bind_token)

    # token 无效
    if not bind_payload:
        raise HTTPException(status_code=401, detail="bind_token 无效或已过期")

    # token 类型校验
    if bind_payload.get("token_use") != "bind_mobile":
        raise HTTPException(status_code=401, detail="bind_token 类型错误")

    # 取当前绑定用户 ID
    bind_user_id = bind_payload.get("sub")

    # 没取到用户信息
    if not bind_user_id:
        raise HTTPException(status_code=401, detail="bind_token 缺少用户信息")

    try:
        # 调 service 绑定手机号
        result = bind_mobile_for_wechat_user(
            db=db,
            bind_user_id=int(bind_user_id),
            mobile=payload.mobile,
            code=payload.code,
        )

        # 写成功日志
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
        # 写失败日志
        create_login_log(
            db=db,
            login_type="wechat_bind_mobile",
            login_identifier=payload.mobile,
            success=False,
            failure_reason=str(e),
            ip=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        # 抛 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/password/set")
def set_password_api(
    payload: SetPasswordRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    当前登录用户设置密码
    """
    # 获取当前用户 ID
    user_id = get_current_user_id(authorization)

    try:
        # 设置密码
        set_password_for_user(db, user_id, payload.new_password)

        # 返回成功
        return {"message": "密码设置成功"}

    except ValueError as e:
        # 抛 HTTP 400
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/me", response_model=UserProfileResponse)
def me_api(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """
    获取当前用户信息
    """
    # 取当前用户 ID
    user_id = get_current_user_id(authorization)

    # 查用户信息
    user = get_user_profile(db, user_id)

    # 用户不存在
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    # 返回用户信息
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
    退出登录

    说明：
    当前 JWT 为无状态 token
    前端删除本地 token 即可
    TODO: 后续如需更高安全性，可加 token 黑名单
    """
    return {"message": "退出成功"}
