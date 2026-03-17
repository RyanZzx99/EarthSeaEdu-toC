/**
 * 认证模块前端接口封装
 *
 * 主要职责：
 * 1. 统一封装认证相关接口请求
 * 2. 自动给请求附带 access_token
 * 3. 给前端页面提供清晰的 API 调用方法
 *
 * 重要说明：
 * 1. 当前开发环境依赖 Vite 代理
 * 2. 所以这里的 baseURL 保持空字符串即可
 * 3. 只要请求路径是 /api 开头，Vite 就会帮你转发到后端
 * 4. 如果后续改成生产环境独立域名，也可以在这里统一切换
 */

 // 导入 axios
import axios from "axios";

/**
 * 创建 axios 实例
 *
 * 说明：
 * 1. 当前 baseURL 留空，依赖 vite.config.js 中的 /api 代理
 * 2. timeout 可根据实际需要调整
 */
const request = axios.create({
  baseURL: "",
  timeout: 10000,
});

/**
 * 请求拦截器
 *
 * 作用：
 * 1. 自动从 localStorage 读取 access_token
 * 2. 如果存在，则统一追加到 Authorization 请求头
 * 3. 后端 router 中会从 Bearer token 里解析当前用户
 */
request.interceptors.request.use(
  (config) => {
    // 从本地存储读取 access token
    const token = localStorage.getItem("access_token");

    // 如果存在 token，则自动带到请求头
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 返回处理后的配置
    return config;
  },
  (error) => {
    // 请求发起前出错时，直接抛出
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器
 *
 * 作用：
 * 1. 统一处理 401 未登录 / token 失效
 * 2. 避免每个页面都手动重复判断
 *
 * 当前策略：
 * 1. 如果返回 401，则清理本地 token
 * 2. 再根据当前页面决定是否跳转到 /login
 *
 * TODO：
 * 如果后续你接入消息组件或全局状态管理，可以把提示逻辑集中写在这里
 */
request.interceptors.response.use(
  (response) => {
    // 正常响应直接返回
    return response;
  },
  (error) => {
    // 取响应对象
    const response = error?.response;

    // 如果是 401，说明未登录或 token 失效
    if (response?.status === 401) {
      // 清理本地 token
      localStorage.removeItem("access_token");

      // 当前浏览器路径
      const currentPath = window.location.pathname;

      // 如果当前不在登录页，则跳转到登录页
      if (currentPath !== "/login") {
        window.location.href = "/login";
      }
    }

    // 把错误继续抛给页面层处理
    return Promise.reject(error);
  }
);

/**
 * 发送短信验证码
 *
 * 适用场景：
 * 1. 手机验证码登录 -> biz_type=login
 * 2. 微信登录后绑定手机号 -> biz_type=bind_mobile
 */
export function sendSmsCode(data) {
  return request.post("/api/v1/auth/sms/send-code", data);
}

/**
 * 手机号 + 密码登录
 */
export function passwordLogin(data) {
  return request.post("/api/v1/auth/login/password", data);
}

/**
 * 手机验证码登录
 */
export function smsLogin(data) {
  return request.post("/api/v1/auth/login/sms", data);
}

/**
 * 获取微信扫码登录地址
 *
 * 前端点击“去微信扫码登录”时调用
 */
export function getWechatAuthorizeUrl() {
  return request.get("/api/v1/auth/wechat/authorize-url");
}

/**
 * 微信扫码登录
 *
 * 前端从地址栏拿到 code / state 后调用
 */
export function wechatLogin(data) {
  return request.post("/api/v1/auth/login/wechat", data);
}

/**
 * 微信绑定手机号
 *
 * 前端提交：
 * - bind_token
 * - mobile
 * - code
 */
export function wechatBindMobile(data) {
  return request.post("/api/v1/auth/wechat/bind-mobile", data);
}

/**
 * 设置密码
 *
 * 当前要求必须在已登录状态下调用
 */
export function setPassword(data) {
  return request.post("/api/v1/auth/password/set", data);
}

/**
 * 获取当前登录用户信息
 */
export function getMe() {
  return request.get("/api/v1/auth/me");
}

/**
 * 退出登录
 *
 * 当前后端是无状态 JWT
 * 所以前端仍需自行清理本地 token
 */
export function logout() {
  return request.post("/api/v1/auth/logout");
}

/**
 * 生成邀请码（管理员）
 *
 * 说明：
 * 1. 需要在请求头中传 X-Admin-Key
 * 2. data 示例：{ count: 20, expires_days: 30, note: "活动批次" }
 */
export function generateInviteCodes(data, adminKey) {
  return request.post("/api/v1/auth/invite-codes/generate", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 发放邀请码（管理员）
 *
 * 说明：
 * 1. 需要在请求头中传 X-Admin-Key
 * 2. data 示例：{ code: "ABCD2345JK", mobile: "13800138000" }
 */
export function issueInviteCode(data, adminKey) {
  return request.post("/api/v1/auth/invite-codes/issue", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 查询邀请码列表（管理员）
 *
 * 说明：
 * 1. 需要在请求头中传 X-Admin-Key
 * 2. params 示例：{ status: "unused", mobile: "13800138000", code_keyword: "ABC", limit: 50 }
 */
export function listInviteCodes(params, adminKey) {
  return request.get("/api/v1/auth/invite-codes", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 修改邀请码状态（管理员）
 *
 * 说明：
 * 1. 需要在请求头中传 X-Admin-Key
 * 2. data 示例：{ code: "ABCD2345JK", status: "3" }
 */
export function updateInviteCodeStatus(data, adminKey) {
  return request.post("/api/v1/auth/invite-codes/update-status", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}
