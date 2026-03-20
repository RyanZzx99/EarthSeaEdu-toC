<template>
  <!-- 中文注释：首页保留原版 H5 结构，只在导航栏右侧增加用户入口 -->
  <div>
    <header class="navbar">
      <img src="/assets/logo带字.png" alt="Logo" width="144" height="70" />

      <nav>
        <ul class="nav-links">
          <li><a href="#">首页</a></li>
          <li><a href="#">关于</a></li>
          <li><a href="#">功能</a></li>
          <li><a href="#">联系</a></li>
        </ul>
      </nav>

      <button v-if="profile" class="user-entry" @click="goProfile">
        <img
          v-if="profile.avatar_url"
          :src="profile.avatar_url"
          alt="用户头像"
          class="user-avatar"
        />
        <div v-else class="user-avatar user-avatar-fallback">
          {{ getDisplayInitial(profile.nickname, profile.mobile) }}
        </div>
        <span class="user-name">{{ profile.nickname || profile.mobile || "用户" }}</span>
      </button>
    </header>

    <section class="hero">
      <h2>欢迎使用路途 你的留学申请工具包</h2>
      <h1>备考，选校，申请，查分一站式搞定</h1>
      <a href="#" class="btn">Get Started</a>
    </section>

    <section class="content">
      <h2>快捷小工具</h2>
      <p>你会发现这里有各种实用工具，帮助你更轻松地完成留学申请过程。</p>

      <ScoreConverter />

      <div class="test_signup" aria-labelledby="test_signup-heading">
        <h4 id="test_signup-heading">考试报名指南</h4>
        <p>点击下方链接，前往各大语言类标准化考试的官方网站，了解最新的考试信息并完成报名。</p>
        <li><a href="https://www.ets.org/toefl" target="_blank" rel="noopener noreferrer">TOEFL iBT 报名</a></li>
        <li><a href="https://www.ielts.org" target="_blank" rel="noopener noreferrer">IELTS 报名</a></li>
        <li><a href="https://pearsonpte.com" target="_blank" rel="noopener noreferrer">PTE Academic 报名</a></li>
        <li><a href="https://englishtest.duolingo.com" target="_blank" rel="noopener noreferrer">Duolingo English Test 报名</a></li>
        <li><a href="https://www.languagecert.org" target="_blank" rel="noopener noreferrer">LanguageCert Academic 报名</a></li>
      </div>

      <RankingTool />
    </section>

    <footer>
      <p>© 路途 2025 - All Rights Reserved</p>
      <p><a href="https://esie.top/" target="_blank" rel="noopener noreferrer">地海国际教育</a>提供技术支持与赞助</p>
    </footer>
  </div>
</template>

<script setup>
// 中文注释：首页只负责补充登录态用户入口，不改动原版 H5 主体结构
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import RankingTool from "../components/RankingTool.vue";
import ScoreConverter from "../components/ScoreConverter.vue";
import { getMe } from "../api/auth";

const router = useRouter();
const profile = ref(null);

function clearLoginState() {
  localStorage.removeItem("access_token");
}

function getDisplayInitial(nickname, mobile) {
  const source = (nickname || mobile || "U").trim();
  return source.charAt(0).toUpperCase();
}

function goProfile() {
  router.push("/profile");
}

async function fetchProfile() {
  try {
    const res = await getMe();
    profile.value = res.data;
  } catch (error) {
    if (error?.response?.status === 401) {
      clearLoginState();
      router.push("/login");
    }
  }
}

onMounted(async () => {
  await fetchProfile();
});
</script>

<style scoped>
/* 中文注释：仅补充用户入口样式，避免污染原版 H5 样式 */
.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  flex: 0 0 34px;
}

.user-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f7aea, #3ab8ff);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.user-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #333;
  font-weight: 600;
}

@media (max-width: 900px) {
  .navbar {
    gap: 12px;
    flex-wrap: wrap;
  }
}

@media (max-width: 600px) {
  .user-name {
    max-width: 120px;
  }
}
</style>
