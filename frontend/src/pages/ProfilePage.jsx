import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  checkMyNickname,
  checkMyPassword,
  getMe,
  logout,
  setPassword,
  updateMyNickname,
} from "../api/auth";

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 24;
const BCRYPT_PASSWORD_MAX_BYTES = 72;

export default function ProfilePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [setPasswordLoading, setSetPasswordLoading] = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);
  const [updateNicknameLoading, setUpdateNicknameLoading] = useState(false);
  const [checkNicknameLoading, setCheckNicknameLoading] = useState(false);
  const [checkPasswordLoading, setCheckPasswordLoading] = useState(false);
  const [showNicknameEditor, setShowNicknameEditor] = useState(false);
  const [showPasswordEditor, setShowPasswordEditor] = useState(false);
  const [nicknameCheckMessage, setNicknameCheckMessage] = useState("");
  const [nicknameCheckAvailable, setNicknameCheckAvailable] = useState(false);
  const [passwordCheckMessage, setPasswordCheckMessage] = useState("");
  const [passwordCheckAvailable, setPasswordCheckAvailable] = useState(false);
  const [profile, setProfile] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [passwordForm, setPasswordForm] = useState({ new_password: "", confirm_password: "" });
  const [nicknameForm, setNicknameForm] = useState({ nickname: "" });

  useEffect(() => {
    fetchProfile();
  }, []);

  function notify(message) {
    window.alert(message);
  }

  function getUtf8ByteLength(value) {
    return new TextEncoder().encode(value).length;
  }

  function getDisplayInitial(nickname, mobile) {
    const source = (nickname || mobile || "U").trim();
    return source.charAt(0).toUpperCase();
  }

  function resetNicknameCheckResult() {
    setNicknameCheckMessage("");
    setNicknameCheckAvailable(false);
  }

  function resetPasswordCheckResult() {
    setPasswordCheckMessage("");
    setPasswordCheckAvailable(false);
  }

  function validatePassword(value) {
    if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
      return "密码长度需为 8-24 位";
    }

    if (/\s/.test(value)) {
      return "密码不能包含空格";
    }

    const hasLetter = /[A-Za-z]/.test(value);
    const hasDigit = /\d/.test(value);
    const hasSpecial = /[^A-Za-z0-9]/.test(value);
    const categoryCount = [hasLetter, hasDigit, hasSpecial].filter(Boolean).length;

    if (categoryCount < 2) {
      return "密码至少需包含字母、数字、特殊字符中的 2 种";
    }

    if (getUtf8ByteLength(value) > BCRYPT_PASSWORD_MAX_BYTES) {
      return "密码字节长度不能超过 72 bytes";
    }

    return "";
  }

  async function fetchProfile() {
    setErrorMessage("");

    try {
      setLoading(true);
      const response = await getMe();
      setProfile(response.data);
      setNicknameForm({ nickname: response.data.nickname || "" });
    } catch (error) {
      const detail = error?.response?.data?.detail || "获取用户信息失败";
      setErrorMessage(detail);

      if (error?.response?.status === 401) {
        localStorage.removeItem("access_token");
        navigate("/login", { replace: true });
      }
    } finally {
      setLoading(false);
    }
  }

  function handleOpenPasswordEditor() {
    setShowPasswordEditor(true);
    setPasswordForm({ new_password: "", confirm_password: "" });
    resetPasswordCheckResult();
  }

  function handleCancelPasswordEdit() {
    setShowPasswordEditor(false);
    setPasswordForm({ new_password: "", confirm_password: "" });
    resetPasswordCheckResult();
  }

  function handleCancelNicknameEdit() {
    setShowNicknameEditor(false);
    setNicknameForm({ nickname: profile?.nickname || "" });
    resetNicknameCheckResult();
  }

  async function handleCheckNickname() {
    resetNicknameCheckResult();

    if (!nicknameForm.nickname.trim()) {
      setNicknameCheckMessage("请输入昵称");
      setNicknameCheckAvailable(false);
      return;
    }

    try {
      setCheckNicknameLoading(true);
      const response = await checkMyNickname({ nickname: nicknameForm.nickname.trim() });
      setNicknameCheckMessage(response.data.message);
      setNicknameCheckAvailable(Boolean(response.data.available));
    } catch (error) {
      setNicknameCheckMessage(error?.response?.data?.detail || "昵称检查失败");
      setNicknameCheckAvailable(false);
    } finally {
      setCheckNicknameLoading(false);
    }
  }

  async function handleUpdateNickname() {
    setErrorMessage("");

    if (!nicknameForm.nickname.trim()) {
      setErrorMessage("请输入昵称");
      return;
    }

    try {
      setUpdateNicknameLoading(true);
      await updateMyNickname({ nickname: nicknameForm.nickname.trim() });
      notify("昵称修改成功");
      setShowNicknameEditor(false);
      await fetchProfile();
      resetNicknameCheckResult();
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "昵称修改失败");
    } finally {
      setUpdateNicknameLoading(false);
    }
  }

  async function handleCheckPassword() {
    resetPasswordCheckResult();

    if (!passwordForm.new_password) {
      setPasswordCheckMessage("请输入新密码");
      setPasswordCheckAvailable(false);
      return;
    }

    if (passwordForm.new_password !== passwordForm.confirm_password) {
      setPasswordCheckMessage("两次输入的密码不一致");
      setPasswordCheckAvailable(false);
      return;
    }

    const localValidationMessage = validatePassword(passwordForm.new_password);

    if (localValidationMessage) {
      setPasswordCheckMessage(localValidationMessage);
      setPasswordCheckAvailable(false);
      return;
    }

    try {
      setCheckPasswordLoading(true);
      const response = await checkMyPassword({ new_password: passwordForm.new_password });
      setPasswordCheckMessage(response.data.message);
      setPasswordCheckAvailable(Boolean(response.data.available));
    } catch (error) {
      setPasswordCheckMessage(error?.response?.data?.detail || "密码检查失败");
      setPasswordCheckAvailable(false);
    } finally {
      setCheckPasswordLoading(false);
    }
  }

  async function handleSetPassword() {
    setErrorMessage("");

    if (!passwordForm.new_password) {
      setErrorMessage("请输入新密码");
      return;
    }

    if (passwordForm.new_password !== passwordForm.confirm_password) {
      setErrorMessage("两次输入的密码不一致");
      return;
    }

    const localValidationMessage = validatePassword(passwordForm.new_password);

    if (localValidationMessage) {
      setErrorMessage(localValidationMessage);
      return;
    }

    try {
      setSetPasswordLoading(true);
      await setPassword({ new_password: passwordForm.new_password });
      notify(profile?.has_password ? "密码修改成功" : "密码设置成功");
      setShowPasswordEditor(false);
      setPasswordForm({ new_password: "", confirm_password: "" });
      resetPasswordCheckResult();
      await fetchProfile();
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "密码保存失败");
    } finally {
      setSetPasswordLoading(false);
    }
  }

  async function handleLogout() {
    setErrorMessage("");

    try {
      setLogoutLoading(true);
      await logout();
    } catch (error) {
      console.error("Logout request failed", error);
    } finally {
      localStorage.removeItem("access_token");
      setLogoutLoading(false);
      navigate("/login", { replace: true });
    }
  }

  return (
    <div className="profile-page">
      <div className="page-head">
        <h1 className="title">用户信息</h1>
        <button type="button" className="back-btn" onClick={() => navigate("/")}>
          返回首页
        </button>
      </div>

      {loading ? <div className="loading-box">正在加载用户信息...</div> : null}

      {!loading && profile ? (
        <div className="card">
          <h2 className="card-title">当前登录用户信息</h2>

          <div className="profile-hero">
            {profile.avatar_url ? (
              <img src={profile.avatar_url} alt="用户头像" className="avatar-image" />
            ) : (
              <div className="avatar-fallback">{getDisplayInitial(profile.nickname, profile.mobile)}</div>
            )}
            <div className="hero-meta">
              <div className="hero-name">{profile.nickname || "未设置昵称"}</div>
              <div className="hero-sub">{profile.mobile || "未绑定手机号"}</div>
            </div>
          </div>

          <div className="info-item"><span className="label">用户ID：</span><span className="value">{profile.user_id}</span></div>
          <div className="info-item"><span className="label">手机号：</span><span className="value">{profile.mobile || "未绑定"}</span></div>

          <div className="info-item nickname-row">
            <span className="label">昵称：</span>
            <div className="value value-block">
              <div className="nickname-actions">
                <span>{profile.nickname || "未设置"}</span>
                {!showNicknameEditor ? (
                  <button type="button" className="secondary-btn inline-btn" onClick={() => setShowNicknameEditor(true)}>
                    修改昵称
                  </button>
                ) : null}
              </div>

              {showNicknameEditor ? (
                <div className="editor-box">
                  <input
                    value={nicknameForm.nickname}
                    onChange={(event) => {
                      setNicknameForm({ nickname: event.target.value });
                      resetNicknameCheckResult();
                    }}
                    className="input"
                    type="text"
                    maxLength={100}
                    placeholder="请输入新昵称"
                  />

                  <div className="inline-actions">
                    <button type="button" className="secondary-btn inline-btn" disabled={checkNicknameLoading} onClick={handleCheckNickname}>
                      {checkNicknameLoading ? "检查中..." : "查看昵称是否可用"}
                    </button>
                    <button type="button" className="primary-btn inline-btn" disabled={updateNicknameLoading} onClick={handleUpdateNickname}>
                      {updateNicknameLoading ? "保存中..." : "保存昵称"}
                    </button>
                    <button type="button" className="secondary-btn inline-btn" disabled={updateNicknameLoading || checkNicknameLoading} onClick={handleCancelNicknameEdit}>
                      取消
                    </button>
                  </div>

                  {nicknameCheckMessage ? (
                    <p className={`check-message ${nicknameCheckAvailable ? "check-success" : "check-error"}`}>
                      {nicknameCheckMessage}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </div>
          </div>

          <div className="info-item"><span className="label">头像：</span><span className="value">{profile.avatar_url || "未设置"}</span></div>
          <div className="info-item"><span className="label">状态：</span><span className="value">{profile.status}</span></div>
        </div>
      ) : null}

      {errorMessage ? <div className="error-box">{errorMessage}</div> : null}

      {profile ? (
        <div className="card">
          <h2 className="card-title">设置密码</h2>
          <p className="desc">
            如果你是通过短信登录或微信绑定手机号后首次进入系统，可以在这里设置密码。密码需为 8-24 位，且至少包含字母、数字、特殊字符中的 2 种，不能包含空格。
          </p>

          {!showPasswordEditor ? (
            <button type="button" className="primary-btn" onClick={handleOpenPasswordEditor}>
              {profile.has_password ? "修改密码" : "设置密码"}
            </button>
          ) : (
            <div className="editor-box">
              <input
                value={passwordForm.new_password}
                onChange={(event) => {
                  setPasswordForm((previous) => ({ ...previous, new_password: event.target.value }));
                  resetPasswordCheckResult();
                }}
                className="input"
                type="password"
                placeholder="请输入新密码（8-24位，至少包含2种字符类型）"
              />
              <input
                value={passwordForm.confirm_password}
                onChange={(event) => {
                  setPasswordForm((previous) => ({ ...previous, confirm_password: event.target.value }));
                  resetPasswordCheckResult();
                }}
                className="input"
                type="password"
                placeholder="请再次输入新密码"
              />

              <div className="inline-actions">
                <button type="button" className="secondary-btn" disabled={checkPasswordLoading} onClick={handleCheckPassword}>
                  {checkPasswordLoading ? "检查中..." : "检查密码是否可用"}
                </button>
                <button type="button" className="primary-btn" disabled={setPasswordLoading} onClick={handleSetPassword}>
                  {setPasswordLoading ? "提交中..." : "保存密码"}
                </button>
                <button type="button" className="secondary-btn" disabled={setPasswordLoading || checkPasswordLoading} onClick={handleCancelPasswordEdit}>
                  取消
                </button>
              </div>

              {passwordCheckMessage ? (
                <p className={`check-message ${passwordCheckAvailable ? "check-success" : "check-error"}`}>
                  {passwordCheckMessage}
                </p>
              ) : null}
            </div>
          )}
        </div>
      ) : null}

      <div className="actions">
        <button type="button" className="secondary-btn" disabled={loading} onClick={fetchProfile}>
          刷新用户信息
        </button>
        <button type="button" className="danger-btn" disabled={logoutLoading} onClick={handleLogout}>
          {logoutLoading ? "退出中..." : "退出登录"}
        </button>
      </div>
    </div>
  );
}
