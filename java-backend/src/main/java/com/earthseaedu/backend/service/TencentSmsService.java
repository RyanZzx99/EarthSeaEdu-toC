package com.earthseaedu.backend.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20190711.SmsClient;
import com.tencentcloudapi.sms.v20190711.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20190711.models.SendSmsResponse;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TencentSmsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TencentSmsService.class);

    private final EarthSeaProperties properties;

    public TencentSmsService(EarthSeaProperties properties) {
        this.properties = properties;
    }

    public String sendSmsCode(String mobile, String bizType) {
        String code = RandomUtil.randomNumbers(6);

        if (properties.getTencentcloud().isSmsMock()) {
            LOGGER.info("[MOCK SMS] mobile={}, bizType={}, code={}", mobile, bizType, code);
            return code;
        }

        String templateId = resolveTemplateId(bizType);
        if (CharSequenceUtil.isBlank(properties.getTencentcloud().getSecretId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信 SecretId 未配置");
        }
        if (CharSequenceUtil.isBlank(properties.getTencentcloud().getSecretKey())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信 SecretKey 未配置");
        }
        if (CharSequenceUtil.isBlank(properties.getTencentcloud().getSmsSdkAppId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信 SmsSdkAppId 未配置");
        }
        if (CharSequenceUtil.isBlank(properties.getTencentcloud().getSmsSignName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信签名未配置");
        }
        if (CharSequenceUtil.isBlank(templateId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信模板 ID 未配置");
        }

        try {
            Credential credential = new Credential(
                properties.getTencentcloud().getSecretId(),
                properties.getTencentcloud().getSecretKey()
            );

            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setReqMethod("POST");
            httpProfile.setConnTimeout(60);
            httpProfile.setEndpoint("sms.tencentcloudapi.com");

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setSignMethod("HmacSHA256");
            clientProfile.setHttpProfile(httpProfile);

            SmsClient client = new SmsClient(credential, properties.getTencentcloud().getSmsRegion(), clientProfile);
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumberSet(new String[] {"+86" + mobile});
            request.setSmsSdkAppid(properties.getTencentcloud().getSmsSdkAppId());
            request.setSign(properties.getTencentcloud().getSmsSignName());
            request.setTemplateID(templateId);
            request.setTemplateParamSet(
                new String[] {code, String.valueOf(properties.getSmsCodeExpireMinutes())}
            );

            SendSmsResponse response = client.SendSms(request);
            if (response.getSendStatusSet() == null || response.getSendStatusSet().length == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信返回结果为空");
            }
            String statusCode = response.getSendStatusSet()[0].getCode();
            String statusMessage = response.getSendStatusSet()[0].getMessage();
            if (!Objects.equals(statusCode, "Ok")) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "腾讯云短信发送失败: " + CharSequenceUtil.blankToDefault(statusMessage, statusCode)
                );
            }
            return code;
        } catch (TencentCloudSDKException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "腾讯云短信发送异常: " + exception.getMessage());
        }
    }

    private String resolveTemplateId(String bizType) {
        return switch (bizType) {
            case "login" -> properties.getTencentcloud().getSmsTemplateLogin();
            case "bind_mobile" -> properties.getTencentcloud().getSmsTemplateBindMobile();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的短信业务类型");
        };
    }
}
