<template>
  <!-- 登录页面最外层容器 -->
  <div class="login-page">
    <!-- 页面标题 -->
    <h1 class="title">用户登录</h1>

    <!-- 登录方式切换区域 -->
    <div class="tabs">
      <!-- 手机号 + 密码登录 tab -->
      <button
          class="tab-btn"
          :class="{ active: mode === 'password' }"
          @click="switchMode('password')"
      >
        手机号 + 密码
      </button>

      <!-- 手机验证码登录 tab -->
      <button
          class="tab-btn"
          :class="{ active: mode === 'sms' }"
          @click="switchMode('sms')"
      >
        手机验证码
      </button>

      <!-- 微信扫码登录 tab -->
      <button
          class="tab-btn"
          :class="{ active: mode === 'wechat' }"
          @click="switchMode('wechat')"
      >
        微信扫码登录
      </button>
    </div>

    <!-- 手机号 + 密码登录面板 -->
    <div v-if="mode === 'password'" class="panel">
      <!-- 手机号输入框 -->
      <input
          v-model.trim="passwordForm.mobile"
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
      <button
          class="submit-btn"
          :disabled="loading"
          @click="handlePasswordLogin"
      >
        {{ loading ? "登录中..." : "登录" }}
      </button>
    </div>

    <!-- 手机验证码登录面板 -->
    <div v-if="mode === 'sms'" class="panel">
      <!-- 手机号输入框 -->
      <input
          v-model.trim="smsForm.mobile"
          class="input"
          placeholder="请输入手机号"
      />

      <!-- 验证码输入框 -->
      <input
          v-model.trim="smsForm.code"
          class="input"
          placeholder="请输入验证码"
      />

      <!-- 邀请码输入框（仅新用户注册时必填） -->
      <input
          v-model.trim="smsForm.invite_code"
          class="input"
          placeholder="请输入邀请码（新用户注册时必填）"
      />

      <!-- 操作按钮行 -->
      <div class="row">
        <!-- 发送验证码 -->
        <button
            class="secondary-btn"
            :disabled="sendCodeLoading"
            @click="handleSendLoginCode"
        >
          {{ sendCodeLoading ? "发送中..." : "发送验证码" }}
        </button>

        <!-- 验证码登录 -->
        <button
            class="submit-btn"
            :disabled="loading"
            @click="handleSmsLogin"
        >
          {{ loading ? "登录中..." : "验证码登录" }}
        </button>
      </div>
    </div>

    <!-- 微信扫码登录面板 -->
    <div v-if="mode === 'wechat'" class="panel">
      <!-- 提示说明 -->
      <p class="desc">
        点击下方按钮后会跳转到微信扫码页，扫码完成后会自动返回本页面。
      </p>

      <!-- 去微信扫码 -->
      <button
          class="submit-btn"
          :disabled="loading"
          @click="handleWechatAuthorize"
      >
        {{ loading ? "跳转中..." : "去微信扫码登录" }}
      </button>
    </div>

    <!-- 微信绑定手机号面板 -->
    <div v-if="mode === 'bind_mobile'" class="panel">
      <!-- 提示说明 -->
      <p class="desc">
        微信登录成功，请先绑定手机号后再进入系统。
      </p>

      <!-- 手机号输入框 -->
      <input
          v-model.trim="bindForm.mobile"
          class="input"
          placeholder="请输入要绑定的手机号"
      />

      <!-- 验证码输入框 -->
      <input
          v-model.trim="bindForm.code"
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
        <button
            class="secondary-btn"
            :disabled="sendCodeLoading"
            @click="handleSendBindCode"
        >
          {{ sendCodeLoading ? "发送中..." : "发送绑定验证码" }}
        </button>

        <!-- 提交绑定 -->
        <button
            class="submit-btn"
            :disabled="loading"
            @click="handleBindMobile"
        >
          {{ loading ? "绑定中..." : "绑定并登录" }}
        </button>
      </div>
    </div>

    <!-- 错误提示区域 -->
    <div v-if="errorMessage" class="error-box">
      {{ errorMessage }}
    </div>
  </div>
</template>

<script setup>
// 导入 Vue API
import {onMounted, ref} from "vue";

// 导入路由实例
import {useRouter} from "vue-router";

// 导入认证接口
import {
  getWechatAuthorizeUrl,
  passwordLogin,
  sendSmsCode,
  smsLogin,
  wechatBindMobile,
  wechatLogin,
} from "../api/auth";

// 路由实例
const router = useRouter();

// 当前页面模式
// 可选值：
// password / sms / wechat / bind_mobile
const mode = ref("password");

// 全局 loading
const loading = ref(false);

// 发送验证码 loading
const sendCodeLoading = ref(false);

// 错误消息
const errorMessage = ref("");

// bind token
const bindToken = ref("");

// 手机号 + 密码表单
const passwordForm = ref({
  mobile: "",
  password: "",
});

// 验证码登录表单
const smsForm = ref({
  mobile: "",
  code: "",
  // 邀请码（仅新用户注册时后端强制校验）
  invite_code: "",
});

// 绑定手机号表单
const bindForm = ref({
  mobile: "",
  code: "",
  // 邀请码（仅新手机号注册时后端强制校验）
  invite_code: "",
});

// 切换模式
function switchMode(nextMode) {
  // 切换模式时清空错误
  errorMessage.value = "";

  // 更新模式
  mode.value = nextMode;
}

// 统一提示函数
function notify(message) {
  // 当前先用 alert，后续可替换成 UI 组件消息提示
  // TODO: 后续可接入 Element Plus / Naive UI / Ant Design Vue 消息组件
  alert(message);
}

// 保存 access token
function saveAccessToken(token) {
  // 保存到浏览器本地存储
  localStorage.setItem("access_token", token);
}

// 清理 URL 中的 query 参数
function clearLoginQueryParams() {
  // 当前使用前端路由 /login
  // 这里清理 code / state / wechat_error，避免刷新重复触发
  window.history.replaceState({}, document.title, "/login");
}

// 简单手机号校验
function validateMobile(mobile) {
  // 中国大陆手机号基础校验
  return /^1\d{10}$/.test(mobile);
}

// 手机号 + 密码登录
async function handlePasswordLogin() {
  // 清空错误
  errorMessage.value = "";

  // 基础校验：手机号不能为空
  if (!passwordForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  // 基础校验：手机号格式
  if (!validateMobile(passwordForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  // 基础校验：密码不能为空
  if (!passwordForm.value.password) {
    errorMessage.value = "请输入密码";
    return;
  }

  try {
    // 打开 loading
    loading.value = true;

    // 调后端密码登录接口
    const res = await passwordLogin({
      mobile: passwordForm.value.mobile,
      password: passwordForm.value.password,
    });

    // 保存 access token
    saveAccessToken(res.data.access_token);

    // 提示成功
    notify("登录成功");

    // 跳转首页
    router.push("/");
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "登录失败";
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

// 发送登录验证码
async function handleSendLoginCode() {
  // 清空错误
  errorMessage.value = "";

  // 校验手机号
  if (!smsForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  // 校验手机号格式
  if (!validateMobile(smsForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    // 打开发送验证码 loading
    sendCodeLoading.value = true;

    // 调发送验证码接口
    await sendSmsCode({
      mobile: smsForm.value.mobile,
      biz_type: "login",
    });

    // 提示发送成功
    notify("验证码已发送");
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "验证码发送失败";
  } finally {
    // 关闭发送验证码 loading
    sendCodeLoading.value = false;
  }
}

// 手机验证码登录
async function handleSmsLogin() {
  // 清空错误
  errorMessage.value = "";

  // 校验手机号
  if (!smsForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  // 校验手机号格式
  if (!validateMobile(smsForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  // 校验验证码
  if (!smsForm.value.code) {
    errorMessage.value = "请输入验证码";
    return;
  }

  try {
    // 打开 loading
    loading.value = true;

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

    // 跳转首页
    router.push("/");
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "登录失败";
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

// 获取微信扫码地址并跳转
async function handleWechatAuthorize() {
  // 清空错误
  errorMessage.value = "";

  try {
    // 打开 loading
    loading.value = true;

    // 调后端接口获取微信扫码地址
    const res = await getWechatAuthorizeUrl();

    // 浏览器直接跳转到微信扫码页
    window.location.href = res.data.authorize_url;
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "获取微信登录地址失败";
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

// 发送绑定手机号验证码
async function handleSendBindCode() {
  // 清空错误
  errorMessage.value = "";

  // 校验手机号
  if (!bindForm.value.mobile) {
    errorMessage.value = "请输入要绑定的手机号";
    return;
  }

  // 校验手机号格式
  if (!validateMobile(bindForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    // 打开发送验证码 loading
    sendCodeLoading.value = true;

    // 调发送绑定验证码接口
    await sendSmsCode({
      mobile: bindForm.value.mobile,
      biz_type: "bind_mobile",
    });

    // 提示成功
    notify("验证码已发送");
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "验证码发送失败";
  } finally {
    // 关闭发送验证码 loading
    sendCodeLoading.value = false;
  }
}

// 提交绑定手机号
async function handleBindMobile() {
  // 清空错误
  errorMessage.value = "";

  // bind token 必须存在
  if (!bindToken.value) {
    errorMessage.value = "绑定令牌不存在，请重新走微信登录流程";
    return;
  }

  // 校验手机号
  if (!bindForm.value.mobile) {
    errorMessage.value = "请输入要绑定的手机号";
    return;
  }

  // 校验手机号格式
  if (!validateMobile(bindForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  // 校验验证码
  if (!bindForm.value.code) {
    errorMessage.value = "请输入验证码";
    return;
  }

  try {
    // 打开 loading
    loading.value = true;

    // 调绑定手机号接口
    const res = await wechatBindMobile({
      bind_token: bindToken.value,
      mobile: bindForm.value.mobile,
      code: bindForm.value.code,
      // 老用户合并账号可不填，后端仅在新注册场景强制
      invite_code: bindForm.value.invite_code || null,
    });

    // 保存正式登录 token
    saveAccessToken(res.data.access_token);

    // 清理 bind token
    bindToken.value = "";

    // 提示成功
    notify("绑定成功，已登录");

    // 跳转首页
    router.push("/");
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "绑定失败";
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

// 处理微信扫码回跳后的自动登录
async function handleWechatCallbackLogin() {
  // 读取当前页面 URL
  const currentUrl = new URL(window.location.href);

  // 读取微信回调 code
  const code = currentUrl.searchParams.get("code");

  // 读取微信回调 state
  const state = currentUrl.searchParams.get("state");

  // 读取微信回调错误
  const wechatError = currentUrl.searchParams.get("wechat_error");

  // 如果微信回调带了错误参数
  if (wechatError) {
    // 显示错误
    errorMessage.value = `微信登录失败：${wechatError}`;

    // 清理地址栏
    clearLoginQueryParams();

    return;
  }

  // 如果没有 code 或 state，说明当前不是微信回跳场景
  if (!code || !state) {
    return;
  }

  try {
    // 打开 loading
    loading.value = true;

    // 调后端微信登录接口
    const res = await wechatLogin({
      code,
      state,
    });

    // 情况 1：微信账号已经绑定手机号，直接返回 access_token
    if (res.data.access_token) {
      // 保存 token
      saveAccessToken(res.data.access_token);

      // 提示成功
      notify("微信登录成功");

      // 清理地址栏
      clearLoginQueryParams();

      // 跳转首页
      router.push("/");

      return;
    }

    // 情况 2：微信账号未绑定手机号，需要继续绑定
    if (res.data.next_step === "bind_mobile") {
      // 保存 bind token
      bindToken.value = res.data.bind_token;

      // 切换到绑定手机号模式
      mode.value = "bind_mobile";

      // 提示用户
      notify(res.data.message || "请先绑定手机号");

      // 清理地址栏
      clearLoginQueryParams();

      return;
    }

    // 理论上不会走到这里，做兜底
    errorMessage.value = "微信登录返回结果异常";
    clearLoginQueryParams();
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "微信登录失败";

    // 清理地址栏
    clearLoginQueryParams();
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

// 页面挂载后自动执行
onMounted(async () => {
  // 先处理微信扫码登录回跳逻辑
  await handleWechatCallbackLogin();
});
</script>

<style scoped>
/* 页面整体容器 */
.login-page {
  width: 420px;
  margin: 40px auto;
  padding: 24px;
  border: 1px solid #ddd;
  border-radius: 12px;
  background: #fff;
  font-family: Arial, sans-serif;
  box-sizing: border-box;
}

/* 标题 */
.title {
  text-align: center;
  margin-bottom: 20px;
}

/* tab 容器 */
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

/* 激活状态 tab */
.tab-btn.active {
  background: #222;
  color: #fff;
  border-color: #222;
}

/* 面板区域 */
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

/* 横向按钮区域 */
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

/* 禁用态按钮 */
.submit-btn:disabled,
.secondary-btn:disabled,
.tab-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 描述文本 */
.desc {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
}

/* 错误提示框 */
.error-box {
  margin-top: 16px;
  padding: 10px 12px;
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
  border-radius: 8px;
  font-size: 14px;
}
</style>
