<template>
  <div class="home-page">
    <h1>首页</h1>

    <div v-if="profile" class="card">
      <p>用户ID：{{ profile.user_id }}</p>
      <p>手机号：{{ profile.mobile || "未绑定" }}</p>
      <p>昵称：{{ profile.nickname || "未设置" }}</p>
      <p>状态：{{ profile.status }}</p>
    </div>

    <div class="actions">
      <button @click="handleLogout">退出登录</button>
    </div>
  </div>
</template>

<script setup>
// 导入 Vue API
import { onMounted, ref } from "vue";

// 导入路由
import { useRouter } from "vue-router";

// 导入接口
import { getMe, logout } from "../api/auth";

// 路由实例
const router = useRouter();

// 用户资料
const profile = ref(null);

// 提示
function notify(message) {
  alert(message);
}

// 获取用户资料
async function fetchProfile() {
  try {
    const res = await getMe();
    profile.value = res.data;
  } catch (error) {
    profile.value = null;
    localStorage.removeItem("access_token");
    router.push("/login");
  }
}

// 退出登录
async function handleLogout() {
  try {
    await logout();
  } catch (error) {
    // 即便失败也继续退出前端状态
  }

  localStorage.removeItem("access_token");
  notify("已退出登录");
  router.push("/login");
}

// 页面挂载时获取资料
onMounted(async () => {
  await fetchProfile();
});
</script>

<style scoped>
.home-page {
  width: 600px;
  margin: 40px auto;
  font-family: Arial, sans-serif;
}

.card {
  padding: 20px;
  border: 1px solid #ddd;
  margin: 20px 0;
  border-radius: 12px;
}

.actions button {
  padding: 10px 16px;
  cursor: pointer;
}
</style>
