"""
腾讯云短信服务模块

主要职责：
1. 发送短信验证码
2. 支持开发环境 mock 模式
3. 按业务类型选择不同短信模板

重要说明：
1. 当前支持的业务类型：
   - login = 登录验证码
   - bind_mobile = 绑定手机号验证码
2. 当前整个登录系统中：
   - 真实发送验证码由这里负责
   - 验证码入库、限流、每日次数限制由 auth_service.py 负责
3. 本模块返回验证码明文给 service 层，仅用于立即做哈希入库
4. 接口层绝对不能把验证码明文返回给前端
"""

import logging

# 导入腾讯云认证对象
from tencentcloud.common import credential

# 导入腾讯云 SDK 异常类
from tencentcloud.common.exception.tencent_cloud_sdk_exception import TencentCloudSDKException

# 导入短信客户端
from tencentcloud.sms.v20210111 import sms_client

# 导入短信请求模型
from tencentcloud.sms.v20210111 import models

# 导入项目配置
from backend.config.db_conf import settings

# 导入生成短信验证码工具
from backend.utils.common import generate_numeric_code


logger = logging.getLogger(__name__)


def send_sms_code(mobile: str, biz_type: str) -> str:
    """
    发送短信验证码

    参数：
        mobile: 手机号
        biz_type: 业务类型
                  当前支持：
                  - login
                  - bind_mobile

    返回：
        本次发送的验证码明文

    说明：
    1. 如果开启了 mock 模式，不会真实发送短信
    2. mock 模式下会直接打印验证码并返回
    3. 真实模式下会调用腾讯云短信接口
    4. 返回验证码明文仅用于服务层立即做哈希入库，不可直接返回前端
    """

    # 先生成 6 位数字验证码
    code = str(generate_numeric_code(6))

    # 如果是开发联调模式，则不走真实腾讯云短信接口
    if settings.tencentcloud_sms_mock:
        logger.info(
            "[MOCK SMS] mobile=%s, biz_type=%s, code=%s",
            mobile,
            biz_type,
            code,
        )
        # 在控制台打印，方便你本地联调
        print(f"[MOCK SMS] mobile={mobile}, biz_type={biz_type}, code={code}", flush=True)

        # 直接返回验证码明文，供上层做哈希入库
        return code

    # 根据业务类型选择短信模板 ID
    if biz_type == "login":
        template_id = settings.tencentcloud_sms_template_login
    elif biz_type == "bind_mobile":
        template_id = settings.tencentcloud_sms_template_bind_mobile
    else:
        # 非法业务类型直接抛错
        raise ValueError("不支持的短信业务类型")

    # 配置项校验
    # TODO:
    # 如果你后续要做更严格的启动前校验，可以把这些检查移到应用启动阶段
    if not settings.tencentcloud_secret_id:
        raise ValueError("腾讯云短信 SecretId 未配置")

    if not settings.tencentcloud_secret_key:
        raise ValueError("腾讯云短信 SecretKey 未配置")

    if not settings.tencentcloud_sms_sdk_app_id:
        raise ValueError("腾讯云短信 SmsSdkAppId 未配置")

    if not settings.tencentcloud_sms_sign_name:
        raise ValueError("腾讯云短信签名未配置")

    if not template_id:
        raise ValueError("腾讯云短信模板 ID 未配置")

    try:
        # 创建腾讯云认证对象
        cred = credential.Credential(
            settings.tencentcloud_secret_id,
            settings.tencentcloud_secret_key,
        )

        # 创建短信客户端
        client = sms_client.SmsClient(
            cred,
            settings.tencentcloud_sms_region,
        )

        # 创建发送短信请求对象
        req = models.SendSmsRequest()

        # 国内手机号格式要求：
        # 必须使用 E.164 格式，中国大陆手机号通常写成 +86xxxxxxxxxxx
        req.PhoneNumberSet = [f"+86{mobile}"]

        # 设置短信应用 ID
        req.SmsSdkAppId = settings.tencentcloud_sms_sdk_app_id

        # 设置短信签名
        req.SignName = settings.tencentcloud_sms_sign_name

        # 设置模板 ID
        req.TemplateId = template_id

        # 设置模板参数
        # 这里默认约定模板参数顺序为：
        # 1. 验证码
        # 2. 有效期（分钟）
        #
        # 例如模板内容可能是：
        # “您的验证码为 {1}，{2} 分钟内有效，请勿泄露。”
        #
        # TODO:
        # 如果你的腾讯云短信模板参数顺序或数量不同，这里必须同步调整
        req.TemplateParamSet = [
            code,
            str(settings.sms_code_expire_minutes),
        ]

        # 发起发送短信请求
        resp = client.SendSms(req)

        # 读取发送结果列表
        send_status_list = resp.SendStatusSet or []

        # 如果返回为空，则认为发送失败
        if not send_status_list:
            raise ValueError("腾讯云短信返回结果为空")

        # 取第一条结果
        first_status = send_status_list[0]

        # 腾讯云成功时通常 Code = "Ok"
        status_code = getattr(first_status, "Code", "")
        status_message = getattr(first_status, "Message", "")

        # 如果不是成功码，则抛异常
        if status_code != "Ok":
            raise ValueError(f"腾讯云短信发送失败: {status_message or status_code}")

        # 发送成功，返回验证码明文
        return code

    except TencentCloudSDKException as e:
        # SDK 异常统一转成普通业务异常，方便上层处理
        raise ValueError(f"腾讯云短信发送异常: {str(e)}")

    except Exception as e:
        # 兜底异常处理
        raise ValueError(f"短信发送失败: {str(e)}")
