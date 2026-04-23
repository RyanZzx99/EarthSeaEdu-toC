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
import { clearAccessToken, getAccessToken } from "../utils/authStorage";

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

const SESSION_OPTIONAL_REDIRECT_PATHS = new Set([
  "/admin-console",
  "/admin-concole",
]);

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
    const token = getAccessToken();

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
      // 当前浏览器路径
      const currentPath = window.location.pathname;

      // 管理员控制台只依赖 X-Admin-Key，不应因为 401 被强制带回登录页
      if (SESSION_OPTIONAL_REDIRECT_PATHS.has(currentPath)) {
        return Promise.reject(error);
      }

      // 清理本地 token
      clearAccessToken();

      // 中文注释：如果当前不在登录页，则带上“登录已过期”标记跳转，便于登录页展示提示
      if (currentPath !== "/login") {
        window.location.href = "/login?session_expired=1";
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
 * 检查短信验证码登录是否需要邀请码
 *
 * 作用：
 * 1. 当前端拿到手机号后，先调用这个接口判断是否为首次注册
 * 2. 如果手机号已注册，则页面不再展示邀请码输入框
 * 3. 如果手机号未注册，则页面提示首次注册需要邀请码
 */
export function checkSmsInviteRequired(data) {
  return request.post("/api/v1/auth/login/sms/invite-required", data);
}

/**
 * 手机号 + 密码登录
 */
export function passwordLogin(data) {
  return request.post("/api/v1/auth/login/password", data);
}

/**
 * 手机号 + 密码 + 邀请码临时注册并登录
 */
export function tempRegisterLogin(data) {
  return request.post("/api/v1/auth/login/temp-register", data);
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

export function wechatInviteRegister(data) {
  return request.post("/api/v1/auth/login/wechat/invite-register", data);
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
 * 检查微信绑定手机号时是否需要邀请码
 *
 * 作用：
 * 1. 用户扫码登录后进入绑定手机号界面
 * 2. 前端可先根据手机号做预检查，决定是否显示邀请码输入框
 * 3. 这样老用户绑定已有手机号时，不再被邀请码输入打断
 */
export function checkWechatBindInviteRequired(data) {
  return request.post("/api/v1/auth/wechat/bind-mobile/invite-required", data);
}

/**
 * 设置密码
 *
 * 当前要求必须在已登录状态下调用
 */
export function setPassword(data) {
  return request.post("/api/v1/auth/password/set", data);
}

export function resetMyPasswordBySms(data) {
  return request.post("/api/v1/auth/me/password/reset-by-sms", data);
}

/**
 * 修改当前登录用户昵称
 */
export function updateMyNickname(data) {
  return request.post("/api/v1/auth/me/nickname", data);
}

/**
 * 检查当前登录用户昵称是否可用
 */
export function checkMyNickname(data) {
  return request.post("/api/v1/auth/me/nickname/check", data);
}

/**
 * 检查当前登录用户新密码是否可用
 */
export function checkMyPassword(data) {
  return request.post("/api/v1/auth/me/password/check", data);
}

export function checkMyResetPassword(data) {
  return request.post("/api/v1/auth/me/password/check-for-reset", data);
}

/**
 * 获取当前登录用户信息
 */
export function getMe() {
  return request.get("/api/v1/auth/me");
}

export function bindMyMobile(data) {
  return request.post("/api/v1/auth/me/mobile/bind", data);
}

export function activateTeacherPortal(data) {
  return request.post("/api/v1/auth/me/teacher/activate", data);
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

/**
 * 修改用户状态（管理员）
 *
 * 说明：
 * 1. 需要在请求头中传 X-Admin-Key
 * 2. data 示例：{ user_id: "550e8400-e29b-41d4-a716-446655440000", mobile: "13800138000", status: "disabled" }
 * 3. user_id 与 mobile 至少传一个
 */
export function updateUserStatus(data, adminKey) {
  return request.post("/api/v1/auth/users/update-status", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 查询昵称规则分组列表（管理员）
 */
export function listNicknameRuleGroups(params, adminKey) {
  return request.get("/api/v1/auth/nickname/rule-groups", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 创建昵称规则分组（管理员）
 */
export function createNicknameRuleGroup(data, adminKey) {
  return request.post("/api/v1/auth/nickname/rule-groups", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 查询昵称词条规则列表（管理员）
 */
export function listNicknameWordRules(params, adminKey) {
  return request.get("/api/v1/auth/nickname/word-rules", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 创建昵称词条规则（管理员）
 */
export function createNicknameWordRule(data, adminKey) {
  return request.post("/api/v1/auth/nickname/word-rules", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 查询昵称联系方式规则列表（管理员）
 */
export function listNicknameContactPatterns(params, adminKey) {
  return request.get("/api/v1/auth/nickname/contact-patterns", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 创建昵称联系方式规则（管理员）
 */
export function createNicknameContactPattern(data, adminKey) {
  return request.post("/api/v1/auth/nickname/contact-patterns", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 修改昵称规则目标状态（管理员）
 */
export function updateNicknameRuleTargetStatus(data, adminKey) {
  return request.post("/api/v1/auth/nickname/rules/update-status", data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

/**
 * 查询昵称审核日志（管理员）
 */
export function listNicknameAuditLogs(params, adminKey) {
  return request.get("/api/v1/auth/nickname/audit-logs", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function listAiPromptConfigs(params, adminKey) {
  return request.get("/api/v1/auth/ai-prompts", {
    params,
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function updateAiPromptConfig(promptId, data, adminKey) {
  return request.post(`/api/v1/auth/ai-prompts/${promptId}/update`, data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function listAiRuntimeConfigs(adminKey) {
  return request.get("/api/v1/auth/ai-runtime-configs", {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function updateAiRuntimeConfig(configKey, data, adminKey) {
  return request.post(`/api/v1/auth/ai-runtime-configs/${configKey}/update`, data, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function importQuestionBank(formData, adminKey, options = {}) {
  return request.post("/api/v1/auth/question-banks/import", formData, {
    headers: {
      "X-Admin-Key": adminKey,
    },
    timeout: 0,
    onUploadProgress: options.onUploadProgress,
  });
}

export function getQuestionBankImportJob(jobId, adminKey) {
  return request.get(`/api/v1/auth/question-banks/import-jobs/${jobId}`, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}

export function importAlevelSourceFiles(formData, adminKey, options = {}) {
  return request.post("/api/v1/auth/alevel/source-files/import", formData, {
    headers: {
      "X-Admin-Key": adminKey,
    },
    timeout: 0,
    onUploadProgress: options.onUploadProgress,
  });
}

export function getAlevelSourceFileImportJob(jobId, adminKey) {
  return request.get(`/api/v1/auth/alevel/source-files/import-jobs/${jobId}`, {
    headers: {
      "X-Admin-Key": adminKey,
    },
  });
}
