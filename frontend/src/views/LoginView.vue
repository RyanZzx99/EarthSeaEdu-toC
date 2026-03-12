<template>
  <div class="login-page">
    <h1 class="title">用户登录</h1>

    <div class="tabs">
      <button
        class="tab-btn"
        :class="{ active: mode === 'password' }"
        @click="mode = 'password'"
      >
        手机号 + 密码
      </button>

      <button
        class="tab-btn"
        :class="{ active: mode === 'sms' }"
        @click="mode = 'sms'"
      >
        手机验证码
      </button>

      <button
        class="tab-btn"
        :class="{ active: mode === 'wechat' }"
        @click="mode = 'wechat'"
      >
        微信扫码登录
      </button>
    </div>

    <div v-if="mode === 'password'" class="panel">
      <input
        v-model="passwordForm.mobile"
        class="input"
        placeholder="请输入手机号"
      />
      <input
        v-model="passwordForm.password"
        class="input"
        type="password"
        placeholder="请输入密码"
      />
      <button class="submit-btn" @click="handlePasswordLogin">
        登录
      </button>
    </div>

    <div v-if="mode === 'sms'" class="panel">
      <input
        v-model="smsForm.mobile"
        class="input"
        placeholder="请输入手机号"
      />
      <input
        v-model="smsForm.code"
        class="input"
        placeholder="请输入验证码"
      />
      <div class="row">
        <button class="secondary-btn" @click="handleSendLoginCode">
          发送验证码
        </button>
        <button class="submit-btn" @click="handleSmsLogin">
          验证码登录
        </button>
      </div>
    </div>

    <div v-if="mode === 'wechat'" class="panel">
      <p class="desc">
        点击后会跳转到微信扫码页，扫码完成后自动回到本登录页。
      </p>
      <button class="submit-btn" @click="handleWechatAuthorize">
        去微信扫码登录
      </button>
    </div>

    <div v-if="mode === 'bind_mobile'" class="panel">
      <p class="desc">
        微信登录成功，请先绑定手机号。
      </p>
      <input
        v-model="bindForm.mobile"
        class="input"
        placeholder="请输入要绑定的手机号"
      />
      <input
        v-model="bindForm.code"
        class="input"
        placeholder="请输入验证码"
      />
      <div class="row">
        <button class="secondary-btn" @click="handleSendBindCode">
          发送绑定验证码
        </button>
        <button class="submit-btn" @click="handleBindMobile">
          绑定并登录
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 导入 Vue API
import { onMounted, ref } from "vue";

// 导入路由
import { useRouter } from "vue-router";

// 导入接口
import {
  getWechatAuthorizeUrl,
  passwordLogin,
  sendSmsCode,
  smsLogin,
  wechatBindMobile,
  wechatLogin,
} from "../api/auth";

// 获取 router 实例
const router = useRouter();

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
});

// 绑定手机号表单
const bindForm = ref({
  mobile: "",
  code: "",
});

// bind token
const bindToken = ref("");

// 保存 access token
function saveAccessToken(token) {
  localStorage.setItem("access_token", token);
}

// 统一提示
function notify(message) {
  alert(message);
}

// 密码登录
async function handlePasswordLogin() {
  try {
    const res = await passwordLogin({
      mobile: passwordForm.value.mobile,
      password: passwordForm.value.password,
    });

    saveAccessToken(res.data.access_token);
    notify("登录成功");
    router.push("/");
  } catch (error) {
    notify(error?.response?.data?.detail || "登录失败");
  }
}

// 发送登录验证码
async function handleSendLoginCode() {
  try {
    await sendSmsCode({
      mobile: smsForm.value.mobile,
      biz_type: "login",
    });

    notify("验证码已发送");
  } catch (error) {
    notify(error?.response?.data?.detail || "发送失败");
  }
}

// 验证码登录
async function handleSmsLogin() {
  try {
    const res = await smsLogin({
      mobile: smsForm.value.mobile,
      code: smsForm.value.code,
    });

    saveAccessToken(res.data.access_token);
    notify("登录成功");
    router.push("/");
  } catch (error) {
    notify(error?.response?.data?.detail || "登录失败");
  }
}

// 获取微信地址并跳转
async function handleWechatAuthorize() {
  try {
    const res = await getWechatAuthorizeUrl();

    window.location.href = res.data.authorize_url;
  } catch (error) {
    notify(error?.response?.data?.detail || "获取微信登录地址失败");
  }
}

// 发送绑定验证码
async function handleSendBindCode() {
  try {
    await sendSmsCode({
      mobile: bindForm.value.mobile,
      biz_type: "bind_mobile",
    });

    notify("验证码已发送");
  } catch (error) {
    notify(error?.response?.data?.detail || "发送失败");
  }
}

// 绑定手机号
async function handleBindMobile() {
  try {
    const res = await wechatBindMobile({
      bind_token: bindToken.value,
      mobile: bindForm.value.mobile,
      code: bindForm.value.code,
    });

    saveAccessToken(res.data.access_token);
    notify("绑定成功，已登录");
    router.push("/");
  } catch (error) {
    notify(error?.response?.data?.detail || "绑定失败");
  }
}

// 处理微信回调后的自动登录
async function handleWechatCallbackLogin() {
  // 读取当前地址
  const currentUrl = new URL(window.location.href);

  // 获取 code
  const code = currentUrl.searchParams.get("code");

  // 获取 state
  const state = currentUrl.searchParams.get("state");

  // 获取错误信息
  const wechatError = currentUrl.searchParams.get("wechat_error");

  // 有微信错误时提示并清理地址栏
  if (wechatError) {
    notify(`微信登录失败：${wechatError}`);
    window.history.replaceState({}, document.title, "/login");
    return;
  }

  // 没有 code/state，说明不是微信回调场景
  if (!code || !state) {
    return;
  }

  try {
    const res = await wechatLogin({
      code,
      state,
    });

    // 已绑定手机号，直接登录
    if (res.data.access_token) {
      saveAccessToken(res.data.access_token);
      notify("微信登录成功");
      window.history.replaceState({}, document.title, "/login");
      router.push("/");
      return;
    }

    // 需要绑定手机号
    if (res.data.next_step === "bind_mobile") {
      bindToken.value = res.data.bind_token;
      mode.value = "bind_mobile";
      notify(res.data.message || "请先绑定手机号");
      window.history.replaceState({}, document.title, "/login");
    }
  } catch (error) {
    notify(error?.response?.data?.detail || "微信登录失败");
    window.history.replaceState({}, document.title, "/login");
  }
}

// 页面挂载时执行
onMounted(async () => {
  await handleWechatCallbackLogin();
});
</script>

<style scoped>
.login-page {
  width: 420px;
  margin: 40px auto;
  padding: 24px;
  border: 1px solid #ddd;
  border-radius: 12px;
  background: #fff;
  font-family: Arial, sans-serif;
}

.title {
  text-align: center;
  margin-bottom: 20px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  cursor: pointer;
  border: 1px solid #ccc;
  background: #f7f7f7;
}

.tab-btn.active {
  background: #222;
  color: #fff;
  border-color: #222;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
}

.row {
  display: flex;
  gap: 10px;
}

.submit-btn,
.secondary-btn {
  padding: 10px;
  cursor: pointer;
}

.desc {
  color: #666;
  font-size: 14px;
}
</style>
