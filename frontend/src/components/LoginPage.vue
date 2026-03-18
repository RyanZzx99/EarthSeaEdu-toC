<template>
  <!-- 页面最外层容器 -->
  <div class="login-page">
    <!-- 页面标题 -->
    <h1 class="title">用户登录</h1>

    <!-- 登录方式切换栏 -->
    <div class="tabs">
      <!-- 手机号密码登录 tab -->
      <button
        class="tab-btn"
        :class="{ active: mode === 'password' }"
        @click="mode = 'password'"
      >
        手机号 + 密码
      </button>

      <!-- 手机验证码登录 tab -->
      <button
        class="tab-btn"
        :class="{ active: mode === 'sms' }"
        @click="mode = 'sms'"
      >
        手机验证码
      </button>

      <!-- 微信扫码登录 tab -->
      <button
        class="tab-btn"
        :class="{ active: mode === 'wechat' }"
        @click="mode = 'wechat'"
      >
        微信扫码登录
      </button>
    </div>

    <!-- 手机号密码登录区域 -->
    <div v-if="mode === 'password'" class="panel">
      <!-- 手机号输入框 -->
      <input
        v-model="passwordForm.mobile"
        class="input"
        placeholder="请输入手机号"
      />

      <!-- 密码输入框 -->
      <input
        v-model="passwordForm.password"
        class="input"
        type="password"
        placeholder="请输入密码"
      />

      <!-- 登录按钮 -->
      <button class="submit-btn" @click="handlePasswordLogin">
        登录
      </button>
    </div>

    <!-- 手机验证码登录区域 -->
    <div v-if="mode === 'sms'" class="panel">
      <!-- 手机号输入框 -->
      <input
        v-model="smsForm.mobile"
        class="input"
        placeholder="请输入手机号"
      />

      <!-- 验证码输入框 -->
      <input
        v-model="smsForm.code"
        class="input"
        placeholder="请输入验证码"
      />

      <!-- 邀请码输入框（仅新用户注册时必填） -->
      <input
        v-model.trim="smsForm.invite_code"
        class="input"
        placeholder="请输入邀请码（新用户注册时必填）"
      />

      <!-- 按钮区域 -->
      <div class="row">
        <!-- 发送验证码按钮 -->
        <button class="secondary-btn" @click="handleSendLoginCode">
          发送验证码
        </button>

        <!-- 验证码登录按钮 -->
        <button class="submit-btn" @click="handleSmsLogin">
          验证码登录
        </button>
      </div>
    </div>

    <!-- 微信扫码登录区域 -->
    <div v-if="mode === 'wechat'" class="panel">
      <!-- 说明文字 -->
      <p class="desc">
        点击按钮后将跳转到微信扫码页，扫码完成后会自动回到本页面。
      </p>

      <!-- 跳转到微信扫码页 -->
      <button class="submit-btn" @click="handleWechatAuthorize">
        去微信扫码登录
      </button>
    </div>

    <!-- 微信绑定手机号区域 -->
    <div v-if="mode === 'bind_mobile'" class="panel">
      <!-- 说明文字 -->
      <p class="desc">
        微信登录成功，请先绑定手机号后再进入系统。
      </p>

      <!-- 手机号输入框 -->
      <input
        v-model="bindForm.mobile"
        class="input"
        placeholder="请输入要绑定的手机号"
      />

      <!-- 验证码输入框 -->
      <input
        v-model="bindForm.code"
        class="input"
        placeholder="请输入验证码"
      />

      <!-- 邀请码输入框（仅新手机号注册时必填） -->
      <input
        v-model.trim="bindForm.invite_code"
        class="input"
        placeholder="请输入邀请码（新用户注册时必填）"
      />

      <!-- 按钮区域 -->
      <div class="row">
        <!-- 发送绑定验证码 -->
        <button class="secondary-btn" @click="handleSendBindCode">
          发送绑定验证码
        </button>

        <!-- 提交绑定 -->
        <button class="submit-btn" @click="handleBindMobile">
          绑定并登录
        </button>
      </div>
    </div>

    <!-- 当前登录用户信息 -->
    <div v-if="profile" class="profile-card">
      <!-- 标题 -->
      <h3>当前登录用户</h3>

      <!-- 用户信息 -->
      <p>用户ID：{{ profile.user_id }}</p>
      <p>手机号：{{ profile.mobile || "未绑定" }}</p>
      <p>昵称：{{ profile.nickname || "未设置" }}</p>
      <p>状态：{{ profile.status }}</p>

      <!-- 设置密码区域 -->
      <div class="set-password">
        <!-- 新密码输入框 -->
        <input
          v-model="newPassword"
          class="input"
          type="password"
          placeholder="设置新密码（8-24位，至少包含2种字符类型）"
        />

        <!-- 设置密码按钮 -->
        <button class="secondary-btn" @click="handleSetPassword">
          设置密码
        </button>
      </div>

      <!-- 退出按钮 -->
      <button class="logout-btn" @click="handleLogout">
        退出登录
      </button>
    </div>
  </div>
</template>

<script setup>
// 导入 Vue API
import { onMounted, ref } from "vue";

// 导入认证接口
import {
  getMe,
  getWechatAuthorizeUrl,
  logout,
  passwordLogin,
  sendSmsCode,
  setPassword,
  smsLogin,
  wechatBindMobile,
  wechatLogin,
} from "../api/auth";

// 当前模式
const mode = ref("password");

// 手机号密码表单
const passwordForm = ref({
  mobile: "",
  password: "",
});

// 短信登录表单
const smsForm = ref({
  mobile: "",
  code: "",
  // 邀请码（仅新用户注册时后端强制校验）
  invite_code: "",
});

// 微信绑定手机号表单
const bindForm = ref({
  mobile: "",
  code: "",
  // 邀请码（仅新手机号注册时后端强制校验）
  invite_code: "",
});

// 当前 bind token
const bindToken = ref("");

// 当前用户资料
const profile = ref(null);

// 新密码
const newPassword = ref("");

// 密码规则：8-24 位、至少包含字母/数字/特殊字符中的 2 种、不能有空格
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 24;

// bcrypt 密码最大只支持 72 bytes（UTF-8）
const BCRYPT_PASSWORD_MAX_BYTES = 72;

// 保存 access token
function saveAccessToken(token) {
  // 保存到浏览器本地存储
  localStorage.setItem("access_token", token);
}

// 清除 access token
function clearAccessToken() {
  // 从本地存储删除
  localStorage.removeItem("access_token");
}

// 统一消息提示
function notify(message) {
  // 当前先用最简单 alert
  // TODO: 后面可接入 UI 组件库消息提示
  alert(message);
}

// 计算字符串 UTF-8 字节长度
function getUtf8ByteLength(value) {
  return new TextEncoder().encode(value).length;
}

// 校验密码是否符合当前页面规则
function validatePassword(value) {
  // 中文注释：密码长度必须在 8-24 位之间
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return "密码长度需为 8-24 位";
  }

  // 中文注释：不允许空格、Tab、换行等空白字符
  if (/\s/.test(value)) {
    return "密码不能包含空格或其他空白字符";
  }

  // 中文注释：至少包含字母、数字、特殊字符中的两类
  const hasLetter = /[A-Za-z]/.test(value);
  const hasDigit = /\d/.test(value);
  const hasSpecial = /[^A-Za-z0-9\s]/.test(value);
  const matchedTypes = [hasLetter, hasDigit, hasSpecial].filter(Boolean).length;

  if (matchedTypes < 2) {
    return "密码至少需包含字母、数字、特殊字符中的 2 种";
  }

  // 中文注释：同步校验 bcrypt 72 字节限制
  if (getUtf8ByteLength(value) > BCRYPT_PASSWORD_MAX_BYTES) {
    return "密码长度不能超过 72 字节（英文约 72 位，中文约 24 位）";
  }

  return "";
}

// 获取当前登录用户信息
async function fetchProfile() {
  try {
    // 请求后端 /me
    const res = await getMe();

    // 保存用户信息
    profile.value = res.data;
  } catch (error) {
    // 未登录时清空 profile
    profile.value = null;
  }
}

// 手机号 + 密码登录
async function handlePasswordLogin() {
  try {
    // 调登录接口
    const res = await passwordLogin({
      mobile: passwordForm.value.mobile,
      password: passwordForm.value.password,
    });

    // 保存 token
    saveAccessToken(res.data.access_token);

    // 提示成功
    notify("登录成功");

    // 获取用户资料
    await fetchProfile();
  } catch (error) {
    // 错误提示
    notify(error?.response?.data?.detail || "登录失败");
  }
}

// 发送登录验证码
async function handleSendLoginCode() {
  try {
    // 调发送短信接口
    await sendSmsCode({
      mobile: smsForm.value.mobile,
      biz_type: "login",
    });

    // 提示发送成功
    notify("验证码已发送");
  } catch (error) {
    // 错误提示
    notify(error?.response?.data?.detail || "发送失败");
  }
}

// 手机验证码登录
async function handleSmsLogin() {
  try {
    // 调验证码登录接口
    const res = await smsLogin({
      mobile: smsForm.value.mobile,
      code: smsForm.value.code,
      // 老用户登录可不填，后端仅在新注册场景强制
      invite_code: smsForm.value.invite_code || null,
    });

    // 保存 token
    saveAccessToken(res.data.access_token);

    // 提示成功
    notify("登录成功");

    // 获取资料
    await fetchProfile();
  } catch (error) {
    // 错误提示
    notify(error?.response?.data?.detail || "登录失败");
  }
}

// 获取微信扫码地址并跳转
async function handleWechatAuthorize() {
  try {
    // 调后端获取扫码地址
    const res = await getWechatAuthorizeUrl();

    // 浏览器直接跳转到微信扫码页
    window.location.href = res.data.authorize_url;
  } catch (error) {
    // 提示错误
    notify(error?.response?.data?.detail || "获取微信登录地址失败");
  }
}

// 发送绑定手机号验证码
async function handleSendBindCode() {
  try {
    // 调后端发送绑定短信验证码
    await sendSmsCode({
      mobile: bindForm.value.mobile,
      biz_type: "bind_mobile",
    });

    // 提示成功
    notify("验证码已发送");
  } catch (error) {
    // 提示失败
    notify(error?.response?.data?.detail || "发送失败");
  }
}

// 微信绑定手机号
async function handleBindMobile() {
  try {
    // 调绑定接口
    const res = await wechatBindMobile({
      bind_token: bindToken.value,
      mobile: bindForm.value.mobile,
      code: bindForm.value.code,
      // 老用户合并账号可不填，后端仅在新注册场景强制
      invite_code: bindForm.value.invite_code || null,
    });

    // 保存正式登录 token
    saveAccessToken(res.data.access_token);

    // 清空 bind token
    bindToken.value = "";

    // 切回普通模式
    mode.value = "password";

    // 提示成功
    notify("手机号绑定成功，已登录");

    // 获取当前用户资料
    await fetchProfile();
  } catch (error) {
    // 提示失败
    notify(error?.response?.data?.detail || "绑定失败");
  }
}

// 设置密码
async function handleSetPassword() {
  // 基础校验：密码不能为空
  if (!newPassword.value) {
    notify("请输入新密码");
    return;
  }

  // 中文注释：统一走当前页面密码规则校验
  const passwordError = validatePassword(newPassword.value);
  if (passwordError) {
    notify(passwordError);
    return;
  }

  try {
    // 调设置密码接口
    await setPassword({
      new_password: newPassword.value,
    });

    // 提示成功
    notify("密码设置成功");
    newPassword.value = "";
  } catch (error) {
    // 错误提示
    notify(error?.response?.data?.detail || "设置密码失败");
  }
}

// 退出登录
async function handleLogout() {
  try {
    // 调退出接口
    await logout();
  } catch (error) {
    // 退出接口失败也不影响前端清理 token
  }

  // 删除本地 token
  clearAccessToken();

  // 清空用户信息
  profile.value = null;

  // 提示
  notify("已退出登录");
}

// 处理微信扫码回跳后的登录逻辑
async function handleWechatCallbackLogin() {
  // 读取当前页面 URL
  const currentUrl = new URL(window.location.href);

  // 读取 query 参数里的 code
  const code = currentUrl.searchParams.get("code");

  // 读取 query 参数里的 state
  const state = currentUrl.searchParams.get("state");

  // 读取 query 参数里的错误信息
  const wechatError = currentUrl.searchParams.get("wechat_error");

  // 如果微信回跳带了错误信息，直接提示并清理地址栏
  if (wechatError) {
    // 提示错误
    notify(`微信登录失败：${wechatError}`);

    // 清理地址栏，防止刷新重复提示
    window.history.replaceState({}, document.title, window.location.pathname);

    // 直接结束
    return;
  }

  // 如果没有 code 或 state，说明当前不是微信回跳
  if (!code || !state) {
    return;
  }

  try {
    // 调用后端微信登录接口
    const res = await wechatLogin({
      code,
      state,
    });

    // 如果直接返回 access_token，说明该微信已绑定手机号
    if (res.data.access_token) {
      // 保存正式 token
      saveAccessToken(res.data.access_token);

      // 提示成功
      notify("微信登录成功");

      // 获取用户信息
      await fetchProfile();
    } else if (res.data.next_step === "bind_mobile") {
      // 如果后端要求绑定手机号，则保存 bind token
      bindToken.value = res.data.bind_token;

      // 切换到绑定手机号模式
      mode.value = "bind_mobile";

      // 提示用户操作
      notify(res.data.message || "请先绑定手机号");
    }

    // 无论成功还是进入绑定流程，都清理地址栏参数
    window.history.replaceState({}, document.title, window.location.pathname);
  } catch (error) {
    // 提示失败
    notify(error?.response?.data?.detail || "微信登录失败");

    // 清理地址栏参数，避免重复触发
    window.history.replaceState({}, document.title, window.location.pathname);
  }
}

// 页面挂载时执行
onMounted(async () => {
  // 先处理微信扫码回跳
  await handleWechatCallbackLogin();

  // 再尝试拉取当前用户信息
  await fetchProfile();
});
</script>

<style scoped>
/* 页面容器 */
.login-page {
  width: 420px;
  margin: 40px auto;
  padding: 24px;
  border: 1px solid #ddd;
  border-radius: 12px;
  font-family: Arial, sans-serif;
  background: #fff;
}

/* 标题 */
.title {
  text-align: center;
  margin-bottom: 20px;
}

/* tab 区域 */
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

/* tab 按钮 */
.tab-btn {
  flex: 1;
  padding: 10px;
  cursor: pointer;
  border: 1px solid #ccc;
  background: #f7f7f7;
}

/* 当前激活的 tab */
.tab-btn.active {
  background: #222;
  color: #fff;
  border-color: #222;
}

/* 表单面板 */
.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 输入框 */
.input {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
}

/* 行按钮区域 */
.row {
  display: flex;
  gap: 10px;
}

/* 主按钮 */
.submit-btn {
  padding: 10px;
  cursor: pointer;
}

/* 次按钮 */
.secondary-btn {
  padding: 10px;
  cursor: pointer;
}

/* 描述文字 */
.desc {
  color: #666;
  font-size: 14px;
}

/* 用户信息卡片 */
.profile-card {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

/* 设置密码区域 */
.set-password {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

/* 退出按钮 */
.logout-btn {
  margin-top: 16px;
  padding: 10px;
  cursor: pointer;
}
</style>
