<script setup>
import RankingTool from './components/RankingTool.vue';
import ScoreConverter from './components/ScoreConverter.vue';

// Keep the page skeleton close to the original HTML so the migration
// changes framework structure without changing the visible layout.
</script>

<template>
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
  </header>

  <section class="hero">
    <h2>欢迎使用录途 你的留学申请工具包</h2>
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
    <p>© 录途 2025 - All Rights Reserved</p>
    <p>
      <a href="https://esie.top/" target="_blank" rel="noopener noreferrer">地海国际教育</a>
      提供技术支持与赞助
    </p>
  </footer>
</template>
