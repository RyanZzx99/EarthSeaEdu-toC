<template>
  <!-- 首页整体容器 -->
  <div class="home-page">
    <!-- 页面标题 -->
    <h1 class="title">首页</h1>

    <!-- 加载中状态 -->
    <div v-if="loading" class="loading-box">
      正在加载用户信息...
    </div>

    <!-- 用户信息卡片 -->
    <div v-else-if="profile" class="card">
      <!-- 卡片标题 -->
      <h2 class="card-title">当前登录用户信息</h2>

      <!-- 用户信息明细 -->
      <div class="info-item">
        <span class="label">用户ID：</span>
        <span class="value">{{ profile.user_id }}</span>
      </div>

      <div class="info-item">
        <span class="label">手机号：</span>
        <span class="value">{{ profile.mobile || "未绑定" }}</span>
      </div>

      <div class="info-item">
        <span class="label">昵称：</span>
        <span class="value">{{ profile.nickname || "未设置" }}</span>
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

    <!-- 错误提示 -->
    <div v-if="errorMessage" class="error-box">
      {{ errorMessage }}
    </div>

    <!-- 设置密码区域 -->
    <div class="card">
      <!-- 区块标题 -->
      <h2 class="card-title">设置密码</h2>

      <!-- 提示说明 -->
      <p class="desc">
        如果你是通过短信登录或微信绑定手机号后首次进入系统，可以在这里设置密码。
      </p>

      <!-- 新密码输入框 -->
      <input
        v-model="passwordForm.new_password"
        class="input"
        type="password"
        placeholder="请输入新密码（至少6位）"
      />

      <!-- 设置密码按钮 -->
      <button
        class="primary-btn"
        :disabled="setPasswordLoading"
        @click="handleSetPassword"
      >
        {{ setPasswordLoading ? "提交中..." : "设置密码" }}
      </button>
    </div>

    <!-- 操作区域 -->
    <div class="actions">
      <!-- 刷新当前用户信息 -->
      <button
        class="secondary-btn"
        :disabled="loading"
        @click="fetchProfile"
      >
        刷新用户信息
      </button>

      <!-- 退出登录 -->
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
/**
 * 首页页面
 *
 * 主要职责：
 * 1. 展示当前登录用户信息
 * 2. 提供设置密码功能
 * 3. 提供退出登录功能
 * 4. 用作前后端联调验证页
 */

 // 导入 Vue API
import { onMounted, ref } from "vue";

// 导入路由
import { useRouter } from "vue-router";

// 导入接口
import { getMe, logout, setPassword } from "../api/auth";

// 获取路由实例
const router = useRouter();

// 是否处于页面加载中
const loading = ref(false);

// 是否处于设置密码提交中
const setPasswordLoading = ref(false);

// 是否处于退出登录中
const logoutLoading = ref(false);

// 当前用户资料
const profile = ref(null);

// 错误消息
const errorMessage = ref("");

// 设置密码表单
const passwordForm = ref({
  new_password: "",
});

/**
 * 统一提示函数
 *
 * 当前先用浏览器 alert
 * TODO: 后续可替换为 UI 组件库消息提示
 */
function notify(message) {
  alert(message);
}

/**
 * 清除本地登录态
 *
 * 当前只需要清除 access_token
 */
function clearLoginState() {
  localStorage.removeItem("access_token");
}

/**
 * 获取当前登录用户资料
 *
 * 调用后端：
 * GET /api/v1/auth/me
 */
async function fetchProfile() {
  // 清空错误
  errorMessage.value = "";

  try {
    // 打开 loading
    loading.value = true;

    // 请求当前用户资料
    const res = await getMe();

    // 保存到页面状态
    profile.value = res.data;
  } catch (error) {
    // 接口失败，展示错误信息
    errorMessage.value = error?.response?.data?.detail || "获取用户信息失败";

    // 如果已经未登录，则清理 token 并跳转登录页
    if (error?.response?.status === 401) {
      clearLoginState();
      router.push("/login");
    }
  } finally {
    // 关闭 loading
    loading.value = false;
  }
}

/**
 * 设置密码
 *
 * 调用后端：
 * POST /api/v1/auth/password/set
 */
async function handleSetPassword() {
  // 清空错误
  errorMessage.value = "";

  // 基础校验：密码不能为空
  if (!passwordForm.value.new_password) {
    errorMessage.value = "请输入新密码";
    return;
  }

  // 基础校验：长度至少 6 位
  if (passwordForm.value.new_password.length < 6) {
    errorMessage.value = "密码长度不能少于 6 位";
    return;
  }

  try {
    // 打开 loading
    setPasswordLoading.value = true;

    // 调设置密码接口
    await setPassword({
      new_password: passwordForm.value.new_password,
    });

    // 提示成功
    notify("密码设置成功");

    // 清空输入框
    passwordForm.value.new_password = "";
  } catch (error) {
    // 显示错误
    errorMessage.value = error?.response?.data?.detail || "设置密码失败";
  } finally {
    // 关闭 loading
    setPasswordLoading.value = false;
  }
}

/**
 * 退出登录
 *
 * 调用后端：
 * POST /api/v1/auth/logout
 *
 * 说明：
 * 1. 当前后端 JWT 是无状态的
 * 2. 所以后端退出接口更多是语义上的统一出口
 * 3. 真正清理登录态仍然需要前端删除本地 token
 */
async function handleLogout() {
  // 清空错误
  errorMessage.value = "";

  try {
    // 打开 loading
    logoutLoading.value = true;

    // 调退出接口
    await logout();
  } catch (error) {
    // 即便接口报错，也继续清理本地 token
  } finally {
    // 无论成功失败，都清理前端登录态
    clearLoginState();

    // 关闭 loading
    logoutLoading.value = false;

    // 提示成功
    notify("已退出登录");

    // 跳转登录页
    router.push("/login");
  }
}

/**
 * 页面挂载后自动获取当前用户信息
 */
onMounted(async () => {
  await fetchProfile();
});
</script>

<style scoped>
/* 页面最外层容器 */
.home-page {
  width: 720px;
  margin: 40px auto;
  font-family: Arial, sans-serif;
}

/* 页面标题 */
.title {
  text-align: center;
  margin-bottom: 24px;
}

/* 卡片公共样式 */
.card {
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 12px;
  background: #fff;
  margin-bottom: 20px;
}

/* 卡片标题 */
.card-title {
  margin-top: 0;
  margin-bottom: 16px;
}

/* 信息项 */
.info-item {
  display: flex;
  margin-bottom: 10px;
  line-height: 1.8;
}

/* 左侧标签 */
.label {
  width: 100px;
  color: #666;
}

/* 右侧值 */
.value {
  flex: 1;
  word-break: break-all;
}

/* 描述文本 */
.desc {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 12px;
}

/* 输入框 */
.input {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  margin-bottom: 12px;
}

/* 按钮区域 */
.actions {
  display: flex;
  gap: 12px;
}

/* 主按钮 */
.primary-btn {
  padding: 10px 16px;
  cursor: pointer;
}

/* 次按钮 */
.secondary-btn {
  padding: 10px 16px;
  cursor: pointer;
}

/* 危险按钮 */
.danger-btn {
  padding: 10px 16px;
  cursor: pointer;
}

/* 按钮禁用态 */
.primary-btn:disabled,
.secondary-btn:disabled,
.danger-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 错误提示 */
.error-box {
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
  border-radius: 8px;
}

/* 加载提示 */
.loading-box {
  padding: 16px;
  border: 1px solid #ddd;
  background: #fafafa;
  border-radius: 8px;
  text-align: center;
}
</style>