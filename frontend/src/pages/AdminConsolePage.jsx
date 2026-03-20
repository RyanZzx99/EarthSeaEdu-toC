import React, { useMemo, useState } from "react";
import {
  generateInviteCodes,
  issueInviteCode,
  listInviteCodes,
  updateInviteCodeStatus,
  updateUserStatus,
} from "../api/auth";

function validateMobile(mobile) {
  return /^1\d{10}$/.test(mobile);
}

function formatDate(value) {
  if (!value) {
    return "-";
  }

  return new Date(value).toLocaleString();
}

function formatInviteStatus(status) {
  if (status === "1") return "1（未使用）";
  if (status === "2") return "2（已使用）";
  if (status === "3") return "3（已禁用）";
  return status || "-";
}

export default function AdminConsolePage() {
  const [adminKey, setAdminKey] = useState("");
  const [generateForm, setGenerateForm] = useState({ count: 10, expires_days: "", note: "" });
  const [issueForm, setIssueForm] = useState({ code: "", mobile: "" });
  const [queryForm, setQueryForm] = useState({ status: "", mobile: "", code_keyword: "", limit: 50 });
  const [userStatusForm, setUserStatusForm] = useState({ user_id: "", mobile: "", status: "active" });
  const [lastUserStatusResult, setLastUserStatusResult] = useState({});
  const [generatedItems, setGeneratedItems] = useState([]);
  const [lastIssued, setLastIssued] = useState({});
  const [inviteList, setInviteList] = useState([]);
  const [queryTotal, setQueryTotal] = useState(0);
  const [pendingStatusMap, setPendingStatusMap] = useState({});
  const [loadingGenerate, setLoadingGenerate] = useState(false);
  const [loadingIssue, setLoadingIssue] = useState(false);
  const [loadingQuery, setLoadingQuery] = useState(false);
  const [loadingUserStatus, setLoadingUserStatus] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const generatedSummary = useMemo(() => generatedItems, [generatedItems]);

  function ensureAdminKey() {
    if (!adminKey) {
      setErrorMessage("请输入管理员密钥");
      return false;
    }

    return true;
  }

  function getPendingStatus(code, currentStatus) {
    return pendingStatusMap[code] || currentStatus || "1";
  }

  function setPendingStatus(code, status) {
    setPendingStatusMap((previous) => ({
      ...previous,
      [code]: status,
    }));
  }

  async function handleGenerate() {
    setErrorMessage("");

    if (!ensureAdminKey()) {
      return;
    }

    if (!generateForm.count || generateForm.count < 1 || generateForm.count > 200) {
      setErrorMessage("生成数量必须在 1~200 之间");
      return;
    }

    try {
      setLoadingGenerate(true);
      const response = await generateInviteCodes(
        {
          count: Number(generateForm.count),
          expires_days: generateForm.expires_days ? Number(generateForm.expires_days) : null,
          note: generateForm.note || null,
        },
        adminKey
      );
      setGeneratedItems(response.data.items || []);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码生成失败");
    } finally {
      setLoadingGenerate(false);
    }
  }

  async function handleIssue() {
    setErrorMessage("");

    if (!ensureAdminKey()) {
      return;
    }

    if (!issueForm.code.trim()) {
      setErrorMessage("请输入邀请码");
      return;
    }

    if (!validateMobile(issueForm.mobile.trim())) {
      setErrorMessage("请输入正确的手机号");
      return;
    }

    try {
      setLoadingIssue(true);
      const response = await issueInviteCode(
        {
          code: issueForm.code.trim(),
          mobile: issueForm.mobile.trim(),
        },
        adminKey
      );
      setLastIssued(response.data.item || response.data);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码发放失败");
    } finally {
      setLoadingIssue(false);
    }
  }

  async function handleQuery() {
    setErrorMessage("");

    if (!ensureAdminKey()) {
      return;
    }

    try {
      setLoadingQuery(true);
      const response = await listInviteCodes(
        {
          status: queryForm.status || undefined,
          mobile: queryForm.mobile || undefined,
          code_keyword: queryForm.code_keyword || undefined,
          limit: queryForm.limit ? Number(queryForm.limit) : 50,
        },
        adminKey
      );

      const items = response.data.items || [];
      setInviteList(items);
      setQueryTotal(response.data.total || items.length);
      setPendingStatusMap(
        items.reduce((result, item) => {
          result[item.code] = item.status;
          return result;
        }, {})
      );
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码查询失败");
    } finally {
      setLoadingQuery(false);
    }
  }

  async function handleUpdateInviteStatus(item) {
    setErrorMessage("");

    if (!ensureAdminKey()) {
      return;
    }

    try {
      await updateInviteCodeStatus(
        {
          code: item.code,
          status: getPendingStatus(item.code, item.status),
        },
        adminKey
      );

      setInviteList((previous) =>
        previous.map((current) =>
          current.code === item.code ? { ...current, status: getPendingStatus(item.code, item.status) } : current
        )
      );
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码状态更新失败");
    }
  }

  async function handleUpdateUserStatus() {
    setErrorMessage("");

    if (!ensureAdminKey()) {
      return;
    }

    if (!userStatusForm.user_id.trim() && !userStatusForm.mobile.trim()) {
      setErrorMessage("用户ID和手机号至少填写一个");
      return;
    }

    if (userStatusForm.mobile.trim() && !validateMobile(userStatusForm.mobile.trim())) {
      setErrorMessage("手机号格式不正确");
      return;
    }

    try {
      setLoadingUserStatus(true);
      const response = await updateUserStatus(
        {
          user_id: userStatusForm.user_id.trim() || null,
          mobile: userStatusForm.mobile.trim() || null,
          status: userStatusForm.status,
        },
        adminKey
      );
      setLastUserStatusResult(response.data.item || response.data);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "用户状态更新失败");
    } finally {
      setLoadingUserStatus(false);
    }
  }

  return (
    <div className="admin-console-page">
      <h1 className="title">管理员控制台</h1>

      <section className="card">
        <h2 className="card-title">管理员密钥</h2>
        <p className="desc">请先填写 X-Admin-Key，再执行下方管理操作。</p>
        <input value={adminKey} onChange={(event) => setAdminKey(event.target.value)} className="input" type="password" placeholder="请输入 X-Admin-Key" />
      </section>

      <section className="card">
        <h2 className="card-title">生成邀请码</h2>
        <input
          value={generateForm.count}
          onChange={(event) => setGenerateForm((previous) => ({ ...previous, count: event.target.value }))}
          className="input"
          type="number"
          min="1"
          max="200"
          placeholder="生成数量（1~200）"
        />
        <input
          value={generateForm.expires_days}
          onChange={(event) => setGenerateForm((previous) => ({ ...previous, expires_days: event.target.value }))}
          className="input"
          type="number"
          min="1"
          max="3650"
          placeholder="过期天数（可留空）"
        />
        <input
          value={generateForm.note}
          onChange={(event) => setGenerateForm((previous) => ({ ...previous, note: event.target.value }))}
          className="input"
          placeholder="批次备注（可留空）"
        />
        <button type="button" className="primary-btn" disabled={loadingGenerate} onClick={handleGenerate}>
          {loadingGenerate ? "生成中..." : "生成邀请码"}
        </button>
      </section>

      <section className="card">
        <h2 className="card-title">发放邀请码</h2>
        <input value={issueForm.code} onChange={(event) => setIssueForm((previous) => ({ ...previous, code: event.target.value }))} className="input" placeholder="邀请码" />
        <input value={issueForm.mobile} onChange={(event) => setIssueForm((previous) => ({ ...previous, mobile: event.target.value }))} className="input" placeholder="发放目标手机号" />
        <button type="button" className="primary-btn" disabled={loadingIssue} onClick={handleIssue}>
          {loadingIssue ? "发放中..." : "发放邀请码"}
        </button>
      </section>

      <section className="card">
        <h2 className="card-title">查询邀请码</h2>
        <select value={queryForm.status} onChange={(event) => setQueryForm((previous) => ({ ...previous, status: event.target.value }))} className="input">
          <option value="">全部状态</option>
          <option value="1">1（未使用）</option>
          <option value="2">2（已使用）</option>
          <option value="3">3（已禁用）</option>
        </select>
        <input value={queryForm.mobile} onChange={(event) => setQueryForm((previous) => ({ ...previous, mobile: event.target.value }))} className="input" placeholder="发放手机号筛选（可留空）" />
        <input value={queryForm.code_keyword} onChange={(event) => setQueryForm((previous) => ({ ...previous, code_keyword: event.target.value }))} className="input" placeholder="邀请码关键字筛选（可留空）" />
        <input value={queryForm.limit} onChange={(event) => setQueryForm((previous) => ({ ...previous, limit: event.target.value }))} className="input" type="number" min="1" max="200" placeholder="返回数量上限（1~200）" />
        <button type="button" className="primary-btn" disabled={loadingQuery} onClick={handleQuery}>
          {loadingQuery ? "查询中..." : "查询邀请码"}
        </button>
      </section>

      <section className="card">
        <h2 className="card-title">修改用户状态</h2>
        <p className="desc">支持按用户ID或手机号修改；状态仅支持 active / disabled。</p>
        <input value={userStatusForm.user_id} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, user_id: event.target.value }))} className="input" placeholder="用户ID（UUID，可留空，与手机号二选一）" />
        <input value={userStatusForm.mobile} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, mobile: event.target.value }))} className="input" placeholder="手机号（可留空，与用户ID二选一）" />
        <select value={userStatusForm.status} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, status: event.target.value }))} className="input">
          <option value="active">active（启用）</option>
          <option value="disabled">disabled（禁用）</option>
        </select>
        <button type="button" className="primary-btn" disabled={loadingUserStatus} onClick={handleUpdateUserStatus}>
          {loadingUserStatus ? "保存中..." : "修改用户状态"}
        </button>
        {lastUserStatusResult.user_id ? (
          <div className="result-box">
            用户ID：{lastUserStatusResult.user_id}，手机号：{lastUserStatusResult.mobile || "-"}，状态：{lastUserStatusResult.status}
          </div>
        ) : null}
      </section>

      {errorMessage ? <div className="error-box">{errorMessage}</div> : null}

      {generatedSummary.length ? (
        <section className="card">
          <h2 className="card-title">最近生成结果</h2>
          <div className="list">
            {generatedSummary.map((item) => (
              <div key={item.code} className="list-item">
                <span className="code">{item.code}</span>
                <span className="meta">状态：{formatInviteStatus(item.status)}</span>
                <span className="meta">过期：{formatDate(item.expires_time)}</span>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {lastIssued.code ? (
        <section className="card">
          <h2 className="card-title">最近发放结果</h2>
          <p>邀请码：{lastIssued.code}</p>
          <p>状态：{formatInviteStatus(lastIssued.status)}</p>
          <p>手机号：{lastIssued.issued_to_mobile || "-"}</p>
          <p>发放时间：{formatDate(lastIssued.issued_time)}</p>
        </section>
      ) : null}

      {inviteList.length ? (
        <section className="card">
          <h2 className="card-title">查询结果（共 {queryTotal} 条）</h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>邀请码</th>
                  <th>状态</th>
                  <th>发放手机号</th>
                  <th>使用人ID</th>
                  <th>发放时间</th>
                  <th>使用时间</th>
                  <th>过期时间</th>
                  <th>备注</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {inviteList.map((item) => (
                  <tr key={item.code}>
                    <td>{item.code}</td>
                    <td>{formatInviteStatus(item.status)}</td>
                    <td>{item.issued_to_mobile || "-"}</td>
                    <td>{item.used_by_user_id || "-"}</td>
                    <td>{formatDate(item.issued_time)}</td>
                    <td>{formatDate(item.used_time)}</td>
                    <td>{formatDate(item.expires_time)}</td>
                    <td>{item.note || "-"}</td>
                    <td>
                      <select className="inline-select" value={getPendingStatus(item.code, item.status)} onChange={(event) => setPendingStatus(item.code, event.target.value)}>
                        <option value="1">1（未使用）</option>
                        <option value="2">2（已使用）</option>
                        <option value="3">3（已禁用）</option>
                      </select>
                      <button type="button" className="inline-btn" onClick={() => handleUpdateInviteStatus(item)}>
                        保存
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
    </div>
  );
}
