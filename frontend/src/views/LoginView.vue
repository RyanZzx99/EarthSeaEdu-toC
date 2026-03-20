<template>
  <!-- 中文注释：登录页按设计稿改为左右双栏布局，保留现有登录与绑定逻辑 -->
  <div class="login-screen">
    <section class="brand-panel">
      <div class="brand-glow brand-glow-top"></div>
      <div class="brand-glow brand-glow-bottom"></div>

      <div class="brand-top">
        <div class="brand-name">路途 LutoolBox</div>
      </div>

      <div class="brand-main">
        <div class="brand-copy">
          <h1>欢迎使用路途，你的留学申请工具包</h1>
          <p>备考、选校、申请、查分，一站式搞定。</p>
        </div>

        <img
          class="brand-illustration"
          src="/assets/lutoolbox-login-illustration.png"
          alt="LutoolBox Illustration"
        />

        <div class="brand-stats">
          <div class="brand-stat">
            <strong>3</strong>
            <span>登录方式</span>
          </div>
          <div class="brand-stat">
            <strong>24h</strong>
            <span>随时访问</span>
          </div>
          <div class="brand-stat">
            <strong>1站式</strong>
            <span>留学工具包</span>
          </div>
        </div>
      </div>

      <div class="brand-footer">© 2026 路途 LutoolBox</div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <div class="form-head">
          <h2>{{ panelTitle }}</h2>
          <p>{{ panelDescription }}</p>
        </div>

        <div v-if="mode !== 'bind_mobile'" class="tab-switcher">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="tab-switcher__item"
            :class="{ active: mode === tab.key }"
            @click="switchMode(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div v-if="mode === 'password'" class="form-stack">
          <div class="field-group">
            <label class="field-label">手机号</label>
            <div class="field-shell">
              <span class="field-prefix">+86</span>
              <input
                v-model.trim="passwordForm.mobile"
                class="field-input"
                type="tel"
                maxlength="11"
                placeholder="请输入手机号"
              />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">密码</label>
            <div class="field-shell">
              <input
                v-model="passwordForm.password"
                class="field-input"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
              />
              <button type="button" class="field-action" @click="showPassword = !showPassword">
                {{ showPassword ? "隐藏" : "显示" }}
              </button>
            </div>
          </div>

          <div class="inline-actions">
            <label class="remember-row">
              <input v-model="rememberLogin" type="checkbox" />
              <span>记住我</span>
            </label>
            <button type="button" class="text-action" @click="showUnavailableTip('找回密码')">
              忘记密码？
            </button>
          </div>

          <button
            type="button"
            class="primary-btn"
            :disabled="loading"
            @click="handlePasswordLogin"
          >
            {{ loading ? "登录中..." : "登录" }}
          </button>
        </div>

        <div v-else-if="mode === 'sms'" class="form-stack">
          <div class="field-group">
            <label class="field-label">手机号</label>
            <div class="field-shell">
              <span class="field-prefix">+86</span>
              <input
                v-model.trim="smsForm.mobile"
                class="field-input"
                type="tel"
                maxlength="11"
                placeholder="请输入手机号"
              />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">验证码</label>
            <div class="code-row">
              <div class="field-shell code-row__input">
                <input
                  v-model.trim="smsForm.code"
                  class="field-input"
                  type="text"
                  maxlength="6"
                  placeholder="请输入验证码"
                />
              </div>
              <button
                type="button"
                class="secondary-btn code-row__btn"
                :disabled="sendCodeLoading || smsCountdown > 0"
                @click="handleSendLoginCode"
              >
                {{ smsSendButtonText }}
              </button>
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">邀请码</label>
            <div class="field-shell">
              <input
                v-model.trim="smsForm.invite_code"
                class="field-input"
                type="text"
                placeholder="新用户注册时必填，老用户登录可留空"
              />
            </div>
          </div>

          <p class="field-tip">未注册的手机号验证成功后将自动注册，首次注册必须通过邀请码校验。</p>

          <button
            type="button"
            class="primary-btn"
            :disabled="loading"
            @click="handleSmsLogin"
          >
            {{ loading ? "提交中..." : "登录 / 注册" }}
          </button>
        </div>

        <div v-else-if="mode === 'wechat'" class="form-stack wechat-stack">
          <div class="wechat-qr-card">
            <div class="wechat-qr">
              <div class="wechat-qr__finder finder-top-left"></div>
              <div class="wechat-qr__finder finder-top-right"></div>
              <div class="wechat-qr__finder finder-bottom-left"></div>
              <div class="wechat-qr__dots"></div>
              <div class="wechat-badge">微信</div>
            </div>
          </div>

          <div class="wechat-copy">
            <p>使用微信扫描二维码登录</p>
            <span>点击下方按钮后会跳转到微信扫码页，扫码完成后自动返回本页。</span>
          </div>

          <button
            type="button"
            class="primary-btn wechat-btn"
            :disabled="loading"
            @click="handleWechatAuthorize"
          >
            {{ loading ? "跳转中..." : "去微信扫码登录" }}
          </button>

          <div class="alt-login-row">
            <button type="button" class="outline-btn" @click="switchMode('password')">密码登录</button>
            <button type="button" class="outline-btn" @click="switchMode('sms')">验证码登录</button>
          </div>
        </div>

        <div v-else class="form-stack">
          <div class="bind-tip-box">
            微信登录成功，请先绑定手机号后再进入系统。
          </div>

          <div class="field-group">
            <label class="field-label">手机号</label>
            <div class="field-shell">
              <span class="field-prefix">+86</span>
              <input
                v-model.trim="bindForm.mobile"
                class="field-input"
                type="tel"
                maxlength="11"
                placeholder="请输入要绑定的手机号"
              />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">验证码</label>
            <div class="code-row">
              <div class="field-shell code-row__input">
                <input
                  v-model.trim="bindForm.code"
                  class="field-input"
                  type="text"
                  maxlength="6"
                  placeholder="请输入验证码"
                />
              </div>
              <button
                type="button"
                class="secondary-btn code-row__btn"
                :disabled="sendCodeLoading || bindCountdown > 0"
                @click="handleSendBindCode"
              >
                {{ bindSendButtonText }}
              </button>
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">邀请码</label>
            <div class="field-shell">
              <input
                v-model.trim="bindForm.invite_code"
                class="field-input"
                type="text"
                placeholder="新手机号首次注册时必填，老账号绑定可留空"
              />
            </div>
          </div>

          <button
            type="button"
            class="primary-btn"
            :disabled="loading"
            @click="handleBindMobile"
          >
            {{ loading ? "绑定中..." : "绑定并登录" }}
          </button>

          <button type="button" class="text-action text-action--center" @click="resetBindState">
            返回其他登录方式
          </button>
        </div>

        <div v-if="errorMessage" class="feedback-box feedback-box--error">
          {{ errorMessage }}
        </div>

        <div v-if="successMessage" class="feedback-box feedback-box--success">
          {{ successMessage }}
        </div>

        <div v-if="mode !== 'wechat' && mode !== 'bind_mobile'" class="agreement-copy">
          登录即表示同意
          <button type="button" class="text-action" @click="showUnavailableTip('用户协议')">用户协议</button>
          与
          <button type="button" class="text-action" @click="showUnavailableTip('隐私政策')">隐私政策</button>
        </div>

        <div class="bottom-copy">
          还没有账号？
          <button type="button" class="text-action" @click="switchMode('sms')">免费注册</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
// 中文注释：登录页复用现有认证接口，只替换页面视觉与交互样式
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  getWechatAuthorizeUrl,
  passwordLogin,
  sendSmsCode,
  smsLogin,
  wechatBindMobile,
  wechatLogin,
} from "../api/auth";

const router = useRouter();

// 中文注释：bind_mobile 不是常驻 tab，只在微信未绑定手机号时进入
const mode = ref("password");
const loading = ref(false);
const sendCodeLoading = ref(false);
const errorMessage = ref("");
const successMessage = ref("");
const bindToken = ref("");
const showPassword = ref(false);
const rememberLogin = ref(false);
const smsCountdown = ref(0);
const bindCountdown = ref(0);
let smsCountdownTimer = null;
let bindCountdownTimer = null;

const tabs = [
  { key: "password", label: "密码登录" },
  { key: "sms", label: "验证码登录" },
  { key: "wechat", label: "微信扫码" },
];

const passwordForm = ref({
  mobile: "",
  password: "",
});

const smsForm = ref({
  mobile: "",
  code: "",
  invite_code: "",
});

const bindForm = ref({
  mobile: "",
  code: "",
  invite_code: "",
});

const panelTitle = computed(() => {
  if (mode.value === "bind_mobile") {
    return "绑定手机号";
  }
  return "欢迎回来";
});

const panelDescription = computed(() => {
  if (mode.value === "bind_mobile") {
    return "完成手机号绑定后即可进入系统。";
  }
  return "登录您的学习账户，继续探索留学之路。";
});

const smsSendButtonText = computed(() => {
  if (sendCodeLoading.value && mode.value === "sms") {
    return "发送中...";
  }
  if (smsCountdown.value > 0) {
    return `${smsCountdown.value}s 后重发`;
  }
  return "获取验证码";
});

const bindSendButtonText = computed(() => {
  if (sendCodeLoading.value && mode.value === "bind_mobile") {
    return "发送中...";
  }
  if (bindCountdown.value > 0) {
    return `${bindCountdown.value}s 后重发`;
  }
  return "获取验证码";
});

function notify(message) {
  window.alert(message);
}

function saveAccessToken(token) {
  localStorage.setItem("access_token", token);
}

function clearLoginQueryParams() {
  window.history.replaceState({}, document.title, "/login");
}

function clearMessages() {
  errorMessage.value = "";
  successMessage.value = "";
}

function switchMode(nextMode) {
  clearMessages();
  mode.value = nextMode;
}

function showUnavailableTip(label) {
  notify(`${label}功能暂未接入`);
}

function validateMobile(mobile) {
  return /^1\d{10}$/.test(mobile);
}

function startCountdown(target) {
  if (target === "sms") {
    clearInterval(smsCountdownTimer);
    smsCountdown.value = 60;
    smsCountdownTimer = window.setInterval(() => {
      if (smsCountdown.value <= 1) {
        clearInterval(smsCountdownTimer);
        smsCountdown.value = 0;
        return;
      }
      smsCountdown.value -= 1;
    }, 1000);
    return;
  }

  clearInterval(bindCountdownTimer);
  bindCountdown.value = 60;
  bindCountdownTimer = window.setInterval(() => {
    if (bindCountdown.value <= 1) {
      clearInterval(bindCountdownTimer);
      bindCountdown.value = 0;
      return;
    }
    bindCountdown.value -= 1;
  }, 1000);
}

function resetBindState() {
  clearMessages();
  bindToken.value = "";
  bindForm.value = {
    mobile: "",
    code: "",
    invite_code: "",
  };
  mode.value = "password";
}

function handleSessionExpiredTip() {
  const currentUrl = new URL(window.location.href);
  const sessionExpired = currentUrl.searchParams.get("session_expired");

  if (sessionExpired === "1") {
    errorMessage.value = "登录已过期，请重新登录";
    clearLoginQueryParams();
  }
}

async function handlePasswordLogin() {
  clearMessages();

  if (!passwordForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  if (!validateMobile(passwordForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  if (!passwordForm.value.password) {
    errorMessage.value = "请输入密码";
    return;
  }

  try {
    loading.value = true;
    const res = await passwordLogin({
      mobile: passwordForm.value.mobile,
      password: passwordForm.value.password,
    });

    saveAccessToken(res.data.access_token);
    successMessage.value = "登录成功，正在跳转...";
    router.push("/");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "登录失败";
  } finally {
    loading.value = false;
  }
}

async function handleSendLoginCode() {
  clearMessages();

  if (!smsForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  if (!validateMobile(smsForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    sendCodeLoading.value = true;
    await sendSmsCode({
      mobile: smsForm.value.mobile,
      biz_type: "login",
    });
    successMessage.value = "验证码已发送，请注意查收";
    startCountdown("sms");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "验证码发送失败";
  } finally {
    sendCodeLoading.value = false;
  }
}

async function handleSmsLogin() {
  clearMessages();

  if (!smsForm.value.mobile) {
    errorMessage.value = "请输入手机号";
    return;
  }

  if (!validateMobile(smsForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  if (!smsForm.value.code) {
    errorMessage.value = "请输入验证码";
    return;
  }

  try {
    loading.value = true;
    const res = await smsLogin({
      mobile: smsForm.value.mobile,
      code: smsForm.value.code,
      invite_code: smsForm.value.invite_code || null,
    });

    saveAccessToken(res.data.access_token);
    successMessage.value = "登录成功，正在跳转...";
    router.push("/");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "登录失败";
  } finally {
    loading.value = false;
  }
}

async function handleWechatAuthorize() {
  clearMessages();

  try {
    loading.value = true;
    const res = await getWechatAuthorizeUrl();
    window.location.href = res.data.authorize_url;
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "获取微信登录地址失败";
  } finally {
    loading.value = false;
  }
}

async function handleSendBindCode() {
  clearMessages();

  if (!bindForm.value.mobile) {
    errorMessage.value = "请输入要绑定的手机号";
    return;
  }

  if (!validateMobile(bindForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    sendCodeLoading.value = true;
    await sendSmsCode({
      mobile: bindForm.value.mobile,
      biz_type: "bind_mobile",
    });
    successMessage.value = "验证码已发送，请注意查收";
    startCountdown("bind");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "验证码发送失败";
  } finally {
    sendCodeLoading.value = false;
  }
}

async function handleBindMobile() {
  clearMessages();

  if (!bindToken.value) {
    errorMessage.value = "绑定令牌不存在，请重新走微信登录流程";
    return;
  }

  if (!bindForm.value.mobile) {
    errorMessage.value = "请输入要绑定的手机号";
    return;
  }

  if (!validateMobile(bindForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  if (!bindForm.value.code) {
    errorMessage.value = "请输入验证码";
    return;
  }

  try {
    loading.value = true;
    const res = await wechatBindMobile({
      bind_token: bindToken.value,
      mobile: bindForm.value.mobile,
      code: bindForm.value.code,
      invite_code: bindForm.value.invite_code || null,
    });

    saveAccessToken(res.data.access_token);
    successMessage.value = "绑定成功，正在跳转...";
    bindToken.value = "";
    router.push("/");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "绑定失败";
  } finally {
    loading.value = false;
  }
}

async function handleWechatCallbackLogin() {
  const currentUrl = new URL(window.location.href);
  const code = currentUrl.searchParams.get("code");
  const state = currentUrl.searchParams.get("state");
  const wechatError = currentUrl.searchParams.get("wechat_error");

  if (wechatError) {
    errorMessage.value = `微信登录失败：${wechatError}`;
    clearLoginQueryParams();
    return;
  }

  if (!code || !state) {
    return;
  }

  try {
    loading.value = true;
    const res = await wechatLogin({
      code,
      state,
    });

    if (res.data.access_token) {
      saveAccessToken(res.data.access_token);
      clearLoginQueryParams();
      successMessage.value = "微信登录成功，正在跳转...";
      router.push("/");
      return;
    }

    if (res.data.next_step === "bind_mobile") {
      bindToken.value = res.data.bind_token;
      mode.value = "bind_mobile";
      successMessage.value = res.data.message || "请先绑定手机号";
      clearLoginQueryParams();
      return;
    }

    errorMessage.value = "微信登录返回结果异常";
    clearLoginQueryParams();
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "微信登录失败";
    clearLoginQueryParams();
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  handleSessionExpiredTip();
  await handleWechatCallbackLogin();
});

onBeforeUnmount(() => {
  clearInterval(smsCountdownTimer);
  clearInterval(bindCountdownTimer);
});
</script>

<style scoped>
/* 中文注释：整体按设计稿做双栏布局，同时兼顾当前项目的响应式需求 */
.login-screen {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1.1fr) minmax(360px, 0.9fr);
  background: #ffffff;
}

.brand-panel {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 36px 44px;
  background: linear-gradient(135deg, #0a1220 0%, #111d34 48%, #16223f 100%);
  color: #ffffff;
}

.brand-glow {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(8px);
}

.brand-glow-top {
  top: 8%;
  left: 18%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(75, 139, 245, 0.28) 0%, rgba(75, 139, 245, 0) 70%);
}

.brand-glow-bottom {
  right: 6%;
  bottom: 12%;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(54, 199, 171, 0.18) 0%, rgba(54, 199, 171, 0) 72%);
}

.brand-top,
.brand-main,
.brand-footer {
  position: relative;
  z-index: 1;
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: 28px;
}

.brand-copy h1 {
  margin: 0 0 12px;
  font-size: 46px;
  line-height: 1.28;
  letter-spacing: -0.02em;
}

.brand-copy p {
  margin: 0;
  max-width: 520px;
  font-size: 20px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.74);
}

.brand-illustration {
  width: min(78%, 500px);
  max-width: 500px;
  object-fit: contain;
  filter: drop-shadow(0 18px 50px rgba(0, 0, 0, 0.2));
}

.brand-stats {
  display: flex;
  gap: 30px;
  flex-wrap: wrap;
}

.brand-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-stat strong {
  font-size: 24px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.95);
}

.brand-stat span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.48);
}

.brand-footer {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.35);
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 28px;
  background: #ffffff;
}

.form-card {
  width: 100%;
  max-width: 392px;
}

.form-head {
  margin-bottom: 26px;
}

.form-head h2 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.form-head p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.tab-switcher {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 5px;
  margin-bottom: 26px;
  border-radius: 14px;
  background: #f3f4f6;
}

.tab-switcher__item {
  padding: 10px 8px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #6b7280;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-switcher__item.active {
  background: #ffffff;
  color: #1a2744;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field-label {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.field-shell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 50px;
  padding: 0 16px;
  border: 1.5px solid #e5e7eb;
  border-radius: 14px;
  background: #fafafa;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.field-shell:focus-within {
  border-color: #2c4a8a;
  box-shadow: 0 0 0 3px rgba(44, 74, 138, 0.08);
}

.field-prefix {
  padding-right: 12px;
  border-right: 1px solid #e5e7eb;
  color: #9ca3af;
  font-size: 14px;
}

.field-input {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #111827;
  font-size: 14px;
}

.field-input::placeholder {
  color: #9ca3af;
}

.field-action {
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -2px;
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
}

.primary-btn,
.secondary-btn,
.outline-btn {
  border-radius: 14px;
  font-weight: 600;
  transition: transform 0.16s ease, box-shadow 0.16s ease, opacity 0.16s ease, background 0.16s ease;
}

.primary-btn {
  min-height: 50px;
  border: none;
  background: linear-gradient(135deg, #1a2744 0%, #2c4a8a 100%);
  color: #ffffff;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(26, 39, 68, 0.2);
}

.primary-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(26, 39, 68, 0.3);
}

.primary-btn:disabled,
.secondary-btn:disabled,
.outline-btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  box-shadow: none;
}

.secondary-btn,
.outline-btn {
  min-height: 50px;
  border: 1.5px solid #dbe2ef;
  background: #ffffff;
  color: #2c4a8a;
  font-size: 13px;
  cursor: pointer;
}

.secondary-btn:hover:not(:disabled),
.outline-btn:hover:not(:disabled) {
  background: #f5f8ff;
}

.code-row {
  display: flex;
  gap: 12px;
}

.code-row__input {
  flex: 1;
}

.code-row__btn {
  width: 116px;
  flex: 0 0 116px;
}

.field-tip {
  margin: -4px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.55;
}

.wechat-stack {
  align-items: center;
}

.wechat-qr-card {
  padding: 18px;
  border: 1.5px solid #e5e7eb;
  border-radius: 22px;
  background: #fafafa;
}

.wechat-qr {
  position: relative;
  width: 188px;
  height: 188px;
  border-radius: 20px;
  background:
    linear-gradient(90deg, #111827 8px, transparent 8px) 0 0/24px 24px,
    linear-gradient(#111827 8px, transparent 8px) 0 0/24px 24px,
    #ffffff;
  overflow: hidden;
}

.wechat-qr__finder {
  position: absolute;
  width: 46px;
  height: 46px;
  border: 8px solid #111827;
  border-radius: 8px;
  background: #ffffff;
}

.finder-top-left {
  top: 12px;
  left: 12px;
}

.finder-top-right {
  top: 12px;
  right: 12px;
}

.finder-bottom-left {
  bottom: 12px;
  left: 12px;
}

.wechat-qr__dots {
  position: absolute;
  inset: 62px 26px 26px 62px;
  background:
    radial-gradient(circle at 10px 10px, #111827 0 4px, transparent 4.5px) 0 0/26px 26px,
    radial-gradient(circle at 16px 16px, #111827 0 4px, transparent 4.5px) 0 0/26px 26px;
  opacity: 0.96;
}

.wechat-badge {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: #07c160;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  transform: translate(-50%, -50%);
  box-shadow: 0 10px 18px rgba(7, 193, 96, 0.3);
}

.wechat-copy {
  text-align: center;
}

.wechat-copy p {
  margin: 0 0 6px;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.wechat-copy span {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.6;
}

.wechat-btn {
  width: 100%;
}

.alt-login-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.bind-tip-box {
  padding: 13px 14px;
  border: 1px solid #d8e4ff;
  border-radius: 14px;
  background: #f4f8ff;
  color: #355387;
  font-size: 13px;
  line-height: 1.65;
}

.feedback-box {
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.feedback-box--error {
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
}

.feedback-box--success {
  border: 1px solid #bfe4cb;
  background: #f2fbf5;
  color: #24744c;
}

.agreement-copy,
.bottom-copy {
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}

.agreement-copy {
  margin-top: 4px;
}

.bottom-copy {
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #f3f4f6;
}

.text-action {
  border: none;
  background: transparent;
  color: #2c4a8a;
  cursor: pointer;
  font-size: inherit;
  font-weight: 600;
}

.text-action--center {
  align-self: center;
}

@media (max-width: 1080px) {
  .login-screen {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: 380px;
    padding: 30px 26px;
  }

  .brand-copy h1 {
    font-size: 34px;
  }

  .brand-copy p {
    font-size: 17px;
  }

  .brand-illustration {
    width: min(68%, 360px);
  }
}

@media (max-width: 640px) {
  .brand-panel {
    min-height: auto;
  }

  .brand-stats {
    gap: 18px;
  }

  .form-panel {
    padding: 24px 16px 36px;
  }

  .form-card {
    max-width: none;
  }

  .tab-switcher {
    grid-template-columns: 1fr;
  }

  .code-row,
  .alt-login-row {
    grid-template-columns: 1fr;
    display: grid;
  }

  .code-row__btn {
    width: 100%;
    flex: 1 1 auto;
  }
}
</style>
