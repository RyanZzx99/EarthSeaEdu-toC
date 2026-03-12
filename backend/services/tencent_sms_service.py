# 导入腾讯云认证相关类
from tencentcloud.common import credential

# 导入腾讯云异常类
from tencentcloud.common.exception.tencent_cloud_sdk_exception import TencentCloudSDKException

# 导入短信客户端
from tencentcloud.sms.v20210111 import sms_client

# 导入短信请求模型
from tencentcloud.sms.v20210111 import models

# 导入项目配置
from backend.config.db_conf import settings

# 导入通用工具
from backend.utils.common import generate_numeric_code

"""
发送短信验证码

参数：
    mobile: 手机号
    biz_type: 业务类型，login / bind_mobile

返回：
    本次发送的验证码明文
    注意：这里只返回给服务层写数据库，接口层不要把验证码返回给前端
"""
def send_sms_code(mobile: str, biz_type: str) -> str:

    # 生成 6 位验证码
    code = generate_numeric_code(6)

    # 如果开启了 mock，则不走真实短信发送
    if settings.tencentcloud_sms_mock:
        # 开发环境打印验证码，方便联调
        print(f"[MOCK SMS] mobile={mobile}, biz_type={biz_type}, code={code}")

        # 直接返回验证码
        return code

    # 根据不同业务选择不同模板
    if biz_type == "login":
        # 登录模板 ID
        template_id = settings.tencentcloud_sms_template_login
    elif biz_type == "bind_mobile":
        # 绑定手机号模板 ID
        template_id = settings.tencentcloud_sms_template_bind_mobile
    else:
        # 非法业务类型直接抛错
        raise ValueError("不支持的短信业务类型")

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

        # 创建短信请求对象
        req = models.SendSmsRequest()

        # 国内手机号格式要求：+86 开头
        req.PhoneNumberSet = [f"+86{mobile}"]

        # 短信 AppID
        req.SmsSdkAppId = settings.tencentcloud_sms_sdk_app_id

        # 短信签名
        req.SignName = settings.tencentcloud_sms_sign_name

        # 模板 ID
        req.TemplateId = template_id

        # 模板参数
        # TODO: 你的短信模板如果参数数量不同，这里要跟着改
        req.TemplateParamSet = [code, str(settings.sms_code_expire_minutes)]

        # 发送短信
        resp = client.SendSms(req)

        # 解析结果
        send_status_list = resp.SendStatusSet or []

        # 如果没有结果，视为失败
        if not send_status_list:
            raise ValueError("腾讯云短信返回为空")

        # 取第一条发送状态
        first_status = send_status_list[0]

        # Code=Ok 表示成功
        if getattr(first_status, "Code", "") != "Ok":
            raise ValueError(getattr(first_status, "Message", "短信发送失败"))

        # 成功则返回验证码
        return code

    except TencentCloudSDKException as e:
        # SDK 异常转成普通异常抛出
        raise ValueError(f"腾讯云短信发送异常: {str(e)}")
