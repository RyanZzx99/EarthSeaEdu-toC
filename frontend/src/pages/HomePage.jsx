import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/auth";
import RankingTool from "../components/RankingTool";
import ScoreConverter from "../components/ScoreConverter";

export default function HomePage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchProfile() {
      try {
        const response = await getMe();

        if (!cancelled) {
          setProfile(response.data);
        }
      } catch (error) {
        if (error?.response?.status === 401) {
          localStorage.removeItem("access_token");
          navigate("/login", { replace: true });
        }
      }
    }

    fetchProfile();

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  function getDisplayInitial(nickname, mobile) {
    const source = (nickname || mobile || "U").trim();
    return source.charAt(0).toUpperCase();
  }

  return (
    <div>
      <header className="navbar">
        <img src="/assets/logo带字.png" alt="Logo" width="144" height="70" />
        <nav>
          <ul className="nav-links">
            <li><a href="#">首页</a></li>
            <li><a href="#">关于</a></li>
            <li><a href="#">功能</a></li>
            <li><a href="#">联系</a></li>
          </ul>
        </nav>

        {profile ? (
          <button type="button" className="user-entry" onClick={() => navigate("/profile")}>
            {profile.avatar_url ? (
              <img src={profile.avatar_url} alt="用户头像" className="user-avatar" />
            ) : (
              <div className="user-avatar user-avatar-fallback">
                {getDisplayInitial(profile.nickname, profile.mobile)}
              </div>
            )}
            <span className="user-name">{profile.nickname || profile.mobile || "用户"}</span>
          </button>
        ) : null}
      </header>

      <section className="hero">
        <h2>欢迎使用路途 你的留学申请工具包</h2>
        <h1>备考，选校，申请，查分一站式搞定</h1>
        <a href="#content" className="btn">Get Started</a>
      </section>

      <section id="content" className="content">
        <h2>快捷小工具</h2>
        <p>你会发现这里有各种实用工具，帮助你更轻松地完成留学申请过程。</p>

        <ScoreConverter />

        <div className="test_signup" aria-labelledby="test_signup-heading">
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
  );
}
