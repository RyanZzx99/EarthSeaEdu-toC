// 导入 axios
import axios from "axios";

// 创建 axios 实例
const request = axios.create({
  // 开发环境下这里留空，依赖 Vite 代理
  baseURL: "",
  timeout: 10000,
});

// 请求拦截器：自动加 token
request.interceptors.request.use((config) => {
  // 从本地读取 access token
  const token = localStorage.getItem("access_token");

  // 如果有 token，则自动加到请求头
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

// 发送短信验证码
export function sendSmsCode(data) {
  return request.post("/api/v1/auth/sms/send-code", data);
}

// 手机号 + 密码登录
export function passwordLogin(data) {
  return request.post("/api/v1/auth/login/password", data);
}

// 手机验证码登录
export function smsLogin(data) {
  return request.post("/api/v1/auth/login/sms", data);
}

// 获取微信扫码地址
export function getWechatAuthorizeUrl() {
  return request.get("/api/v1/auth/wechat/authorize-url");
}

// 微信登录
export function wechatLogin(data) {
  return request.post("/api/v1/auth/login/wechat", data);
}

// 微信绑定手机号
export function wechatBindMobile(data) {
  return request.post("/api/v1/auth/wechat/bind-mobile", data);
}

// 设置密码
export function setPassword(data) {
  return request.post("/api/v1/auth/password/set", data);
}

// 获取当前用户信息
export function getMe() {
  return request.get("/api/v1/auth/me");
}

// 退出登录
export function logout() {
  return request.post("/api/v1/auth/logout");
}
