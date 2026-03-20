<template>
  <!-- 中文注释：个人中心页面，承接首页右上角的用户入口 -->
  <div class="profile-page">
    <div class="page-head">
      <h1 class="title">用户信息</h1>
      <button class="back-btn" @click="goHome">返回首页</button>
    </div>

    <div v-if="loading" class="loading-box">
      正在加载用户信息...
    </div>

    <div v-else-if="profile" class="card">
      <h2 class="card-title">当前登录用户信息</h2>

      <div class="profile-hero">
        <img
          v-if="profile.avatar_url"
          :src="profile.avatar_url"
          alt="用户头像"
          class="avatar-image"
        />
        <div v-else class="avatar-fallback">
          {{ getDisplayInitial(profile.nickname, profile.mobile) }}
        </div>
        <div class="hero-meta">
          <div class="hero-name">{{ profile.nickname || "未设置昵称" }}</div>
          <div class="hero-sub">{{ profile.mobile || "未绑定手机号" }}</div>
        </div>
      </div>

      <div class="info-item">
        <span class="label">用户ID：</span>
        <span class="value">{{ profile.user_id }}</span>
      </div>

      <div class="info-item">
        <span class="label">手机号：</span>
        <span class="value">{{ profile.mobile || "未绑定" }}</span>
      </div>

      <div class="info-item nickname-row">
        <span class="label">昵称：</span>
        <div class="value value-block">
          <div class="nickname-actions">
            <span>{{ profile.nickname || "未设置" }}</span>
            <button
              v-if="!showNicknameEditor"
              class="secondary-btn inline-btn"
              @click="showNicknameEditor = true"
            >
              修改昵称
            </button>
          </div>

          <div v-if="showNicknameEditor" class="editor-box">
            <input
              v-model.trim="nicknameForm.nickname"
              class="input"
              type="text"
              maxlength="100"
              placeholder="请输入新昵称"
            />

            <div class="inline-actions">
              <button
                class="secondary-btn inline-btn"
                :disabled="checkNicknameLoading"
                @click="handleCheckNickname"
              >
                {{ checkNicknameLoading ? "检查中..." : "查看昵称是否可用" }}
              </button>
              <button
                class="primary-btn inline-btn"
                :disabled="updateNicknameLoading"
                @click="handleUpdateNickname"
              >
                {{ updateNicknameLoading ? "保存中..." : "保存昵称" }}
              </button>
              <button
                class="secondary-btn inline-btn"
                :disabled="updateNicknameLoading || checkNicknameLoading"
                @click="handleCancelNicknameEdit"
              >
                取消
              </button>
            </div>

            <p
              v-if="nicknameCheckMessage"
              class="check-message"
              :class="nicknameCheckAvailable ? 'check-success' : 'check-error'"
            >
              {{ nicknameCheckMessage }}
            </p>
          </div>
        </div>
      </div>

      <div class="info-item">
        <span class="label">头像：</span>
        <span class="value">{{ profile.avatar_url || "未设置" }}</span>
      </div>

      <div class="info-item">
        <span class="label">状态：</span>
        <span class="value">{{ profile.status }}</span>
      </div>
    </div>

    <div v-if="errorMessage" class="error-box">
      {{ errorMessage }}
    </div>

    <div v-if="profile" class="card">
      <h2 class="card-title">设置密码</h2>
      <p class="desc">
        如果你是通过短信登录或微信绑定手机号后首次进入系统，可以在这里设置密码。密码需为 8-24 位，且至少包含字母、数字、特殊字符中的 2 种，不能包含空格。
      </p>

      <button
        v-if="!showPasswordEditor"
        class="primary-btn"
        @click="handleOpenPasswordEditor"
      >
        {{ profile.has_password ? "修改密码" : "设置密码" }}
      </button>

      <div v-else class="editor-box">
        <input
          v-model="passwordForm.new_password"
          class="input"
          type="password"
          placeholder="请输入新密码（8-24位，至少包含2种字符类型）"
        />

        <input
          v-model="passwordForm.confirm_password"
          class="input"
          type="password"
          placeholder="请再次输入新密码"
        />

        <div class="inline-actions">
          <button
            class="secondary-btn"
            :disabled="checkPasswordLoading"
            @click="handleCheckPassword"
          >
            {{ checkPasswordLoading ? "检查中..." : "检查密码是否可用" }}
          </button>

          <button
            class="primary-btn"
            :disabled="setPasswordLoading"
            @click="handleSetPassword"
          >
            {{ setPasswordLoading ? "提交中..." : "保存密码" }}
          </button>

          <button
            class="secondary-btn"
            :disabled="setPasswordLoading || checkPasswordLoading"
            @click="handleCancelPasswordEdit"
          >
            取消
          </button>
        </div>

        <p
          v-if="passwordCheckMessage"
          class="check-message"
          :class="passwordCheckAvailable ? 'check-success' : 'check-error'"
        >
          {{ passwordCheckMessage }}
        </p>
      </div>
    </div>

    <div class="actions">
      <button
        class="secondary-btn"
        :disabled="loading"
        @click="fetchProfile"
      >
        刷新用户信息
      </button>

      <button
        class="danger-btn"
        :disabled="logoutLoading"
        @click="handleLogout"
      >
        {{ logoutLoading ? "退出中..." : "退出登录" }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  checkMyNickname,
  checkMyPassword,
  getMe,
  logout,
  setPassword,
  updateMyNickname,
} from "../api/auth";

const router = useRouter();
const loading = ref(false);
const setPasswordLoading = ref(false);
const logoutLoading = ref(false);
const updateNicknameLoading = ref(false);
const checkNicknameLoading = ref(false);
const checkPasswordLoading = ref(false);
const showNicknameEditor = ref(false);
const showPasswordEditor = ref(false);
const nicknameCheckMessage = ref("");
const nicknameCheckAvailable = ref(false);
const passwordCheckMessage = ref("");
const passwordCheckAvailable = ref(false);
const profile = ref(null);
const errorMessage = ref("");
const passwordForm = ref({
  new_password: "",
  confirm_password: "",
});
const nicknameForm = ref({
  nickname: "",
});

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 24;
const BCRYPT_PASSWORD_MAX_BYTES = 72;

function notify(message) {
  alert(message);
}

function getUtf8ByteLength(value) {
  return new TextEncoder().encode(value).length;
}

function getDisplayInitial(nickname, mobile) {
  const source = (nickname || mobile || "U").trim();
  return source.charAt(0).toUpperCase();
}

function resetNicknameCheckResult() {
  nicknameCheckMessage.value = "";
  nicknameCheckAvailable.value = false;
}

function resetPasswordCheckResult() {
  passwordCheckMessage.value = "";
  passwordCheckAvailable.value = false;
}

function validatePassword(value) {
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return "密码长度需为 8-24 位";
  }

  if (/\s/.test(value)) {
    return "密码不能包含空格或其他空白字符";
  }

  const hasLetter = /[A-Za-z]/.test(value);
  const hasDigit = /\d/.test(value);
  const hasSpecial = /[^A-Za-z0-9\s]/.test(value);
  const matchedTypes = [hasLetter, hasDigit, hasSpecial].filter(Boolean).length;

  if (matchedTypes < 2) {
    return "密码至少需包含字母、数字、特殊字符中的 2 种";
  }

  if (getUtf8ByteLength(value) > BCRYPT_PASSWORD_MAX_BYTES) {
    return "密码长度不能超过 72 字节（英文约 72 位，中文约 24 位）";
  }

  return "";
}

function clearLoginState() {
  localStorage.removeItem("access_token");
}

function goHome() {
  router.push("/");
}

function handleOpenPasswordEditor() {
  showPasswordEditor.value = true;
  resetPasswordCheckResult();
}

function handleCancelPasswordEdit() {
  showPasswordEditor.value = false;
  passwordForm.value.new_password = "";
  passwordForm.value.confirm_password = "";
  resetPasswordCheckResult();
}

function handleCancelNicknameEdit() {
  showNicknameEditor.value = false;
  nicknameForm.value.nickname = profile.value?.nickname || "";
  resetNicknameCheckResult();
}

async function fetchProfile() {
  errorMessage.value = "";

  try {
    loading.value = true;
    const res = await getMe();
    profile.value = res.data;

    // 中文注释：每次刷新资料时同步更新编辑器默认值，并按是否已设置密码决定密码区默认展开态
    nicknameForm.value.nickname = res.data.nickname || "";
    showPasswordEditor.value = !res.data.has_password;
    resetNicknameCheckResult();
    resetPasswordCheckResult();
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "获取用户信息失败";

    if (error?.response?.status === 401) {
      clearLoginState();
      router.push("/login");
    }
  } finally {
    loading.value = false;
  }
}

async function handleCheckNickname() {
  errorMessage.value = "";
  resetNicknameCheckResult();

  const normalizedNickname = nicknameForm.value.nickname.trim();
  if (!normalizedNickname) {
    nicknameCheckMessage.value = "请输入昵称";
    return;
  }

  if (normalizedNickname.length > 100) {
    nicknameCheckMessage.value = "昵称长度不能超过 100 位";
    return;
  }

  try {
    checkNicknameLoading.value = true;
    const res = await checkMyNickname({
      nickname: normalizedNickname,
    });
    nicknameCheckAvailable.value = res.data.available;
    nicknameCheckMessage.value = res.data.message;
  } catch (error) {
    nicknameCheckAvailable.value = false;
    nicknameCheckMessage.value = error?.response?.data?.detail || "昵称检查失败，请稍后重试";
  } finally {
    checkNicknameLoading.value = false;
  }
}

async function handleUpdateNickname() {
  errorMessage.value = "";

  const normalizedNickname = nicknameForm.value.nickname.trim();
  if (!normalizedNickname) {
    errorMessage.value = "请输入昵称";
    return;
  }

  if (normalizedNickname.length > 100) {
    errorMessage.value = "昵称长度不能超过 100 位";
    return;
  }

  try {
    updateNicknameLoading.value = true;
    await updateMyNickname({
      nickname: normalizedNickname,
    });

    if (profile.value) {
      profile.value.nickname = normalizedNickname;
    }

    nicknameForm.value.nickname = normalizedNickname;
    nicknameCheckAvailable.value = true;
    nicknameCheckMessage.value = "昵称修改成功";
    showNicknameEditor.value = false;
    notify("昵称修改成功");
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "修改昵称失败";
    nicknameCheckAvailable.value = false;
    nicknameCheckMessage.value = error?.response?.data?.detail || "修改昵称失败";
  } finally {
    updateNicknameLoading.value = false;
  }
}

async function handleCheckPassword() {
  errorMessage.value = "";
  resetPasswordCheckResult();

  if (!passwordForm.value.new_password) {
    passwordCheckMessage.value = "请输入新密码";
    return;
  }

  const passwordError = validatePassword(passwordForm.value.new_password);
  if (passwordError) {
    passwordCheckMessage.value = passwordError;
    return;
  }

  try {
    checkPasswordLoading.value = true;
    const res = await checkMyPassword({
      new_password: passwordForm.value.new_password,
    });
    passwordCheckAvailable.value = res.data.available;
    passwordCheckMessage.value = res.data.message;
  } catch (error) {
    passwordCheckAvailable.value = false;
    passwordCheckMessage.value = error?.response?.data?.detail || "密码检查失败，请稍后重试";
  } finally {
    checkPasswordLoading.value = false;
  }
}

async function handleSetPassword() {
  errorMessage.value = "";

  if (!passwordForm.value.new_password) {
    errorMessage.value = "请输入新密码";
    return;
  }

  if (!passwordForm.value.confirm_password) {
    errorMessage.value = "请再次输入新密码";
    return;
  }

  // 中文注释：前端先做二次输入一致性校验，避免无效请求提交到后端
  if (passwordForm.value.new_password !== passwordForm.value.confirm_password) {
    errorMessage.value = "两次输入的密码不一致";
    passwordCheckAvailable.value = false;
    passwordCheckMessage.value = "两次输入的密码不一致";
    return;
  }

  const passwordError = validatePassword(passwordForm.value.new_password);
  if (passwordError) {
    errorMessage.value = passwordError;
    passwordCheckAvailable.value = false;
    passwordCheckMessage.value = passwordError;
    return;
  }

  try {
    setPasswordLoading.value = true;
    await setPassword({
      new_password: passwordForm.value.new_password,
    });
    notify("密码设置成功");

    // 中文注释：设置成功后回写 has_password，并把编辑器重新收起为按钮态
    if (profile.value) {
      profile.value.has_password = true;
    }

    passwordCheckAvailable.value = true;
    passwordCheckMessage.value = "该密码可以使用";
    handleCancelPasswordEdit();
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "设置密码失败";
    passwordCheckAvailable.value = false;
    passwordCheckMessage.value = error?.response?.data?.detail || "设置密码失败";
  } finally {
    setPasswordLoading.value = false;
  }
}

async function handleLogout() {
  errorMessage.value = "";

  try {
    logoutLoading.value = true;
    await logout();
  } catch (error) {
    // 中文注释：后端退出失败不影响前端清理本地登录态
  } finally {
    clearLoginState();
    logoutLoading.value = false;
    notify("已退出登录");
    router.push("/login");
  }
}

onMounted(async () => {
  await fetchProfile();
});
</script>

<style scoped>
.profile-page {
  width: 760px;
  max-width: calc(100vw - 32px);
  margin: 32px auto;
  font-family: Arial, sans-serif;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.title {
  margin: 0;
}

.back-btn,
.primary-btn,
.secondary-btn,
.danger-btn {
  padding: 10px 16px;
  cursor: pointer;
}

.card {
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 12px;
  background: #fff;
  margin-bottom: 20px;
}

.card-title {
  margin-top: 0;
  margin-bottom: 16px;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.avatar-image,
.avatar-fallback {
  width: 64px;
  height: 64px;
  border-radius: 50%;
}

.avatar-image {
  object-fit: cover;
  border: 1px solid #ddd;
}

.avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2937;
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.hero-name {
  font-size: 18px;
  font-weight: 700;
}

.hero-sub {
  margin-top: 4px;
  color: #666;
  font-size: 14px;
}

.info-item {
  display: flex;
  margin-bottom: 10px;
  line-height: 1.8;
}

.nickname-row {
  align-items: flex-start;
}

.label {
  width: 100px;
  color: #666;
}

.value {
  flex: 1;
  word-break: break-all;
}

.value-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nickname-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.inline-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.inline-btn {
  padding: 6px 12px;
  white-space: nowrap;
}

.desc {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 12px;
}

.input {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  margin-bottom: 12px;
}

.check-message {
  margin: 0;
  font-size: 14px;
}

.check-success {
  color: #1f8f4d;
}

.check-error {
  color: #c0392b;
}

.actions {
  display: flex;
  gap: 12px;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.danger-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-box {
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
  border-radius: 8px;
}

.loading-box {
  padding: 16px;
  border: 1px solid #ddd;
  background: #fafafa;
  border-radius: 8px;
  text-align: center;
  margin-bottom: 20px;
}
</style>
