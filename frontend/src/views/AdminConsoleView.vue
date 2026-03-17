<template>
  <!-- 中文注释：管理员控制台页面容器 -->
  <div class="admin-console-page">
    <h1 class="title">管理员控制台</h1>

    <!-- 中文注释：管理员密钥，所有管理接口都依赖它 -->
    <section class="card">
      <h2 class="card-title">管理员密钥</h2>
      <p class="desc">请先填写 `X-Admin-Key`，再执行下方管理操作。</p>
      <input
        v-model.trim="adminKey"
        class="input"
        type="password"
        placeholder="请输入 X-Admin-Key"
      />
    </section>

    <!-- 中文注释：邀请码生成 -->
    <section class="card">
      <h2 class="card-title">生成邀请码</h2>
      <input
        v-model.number="generateForm.count"
        class="input"
        type="number"
        min="1"
        max="200"
        placeholder="生成数量（1~200）"
      />
      <input
        v-model.number="generateForm.expires_days"
        class="input"
        type="number"
        min="1"
        max="3650"
        placeholder="过期天数（可留空）"
      />
      <input
        v-model.trim="generateForm.note"
        class="input"
        placeholder="批次备注（可留空）"
      />
      <button class="primary-btn" :disabled="loadingGenerate" @click="handleGenerate">
        {{ loadingGenerate ? "生成中..." : "生成邀请码" }}
      </button>
    </section>

    <!-- 中文注释：邀请码发放 -->
    <section class="card">
      <h2 class="card-title">发放邀请码</h2>
      <input v-model.trim="issueForm.code" class="input" placeholder="邀请码" />
      <input v-model.trim="issueForm.mobile" class="input" placeholder="发放目标手机号" />
      <button class="primary-btn" :disabled="loadingIssue" @click="handleIssue">
        {{ loadingIssue ? "发放中..." : "发放邀请码" }}
      </button>
    </section>

    <!-- 中文注释：邀请码查询 -->
    <section class="card">
      <h2 class="card-title">查询邀请码</h2>
      <select v-model="queryForm.status" class="input">
        <option value="">全部状态</option>
        <option value="1">1（未使用）</option>
        <option value="2">2（已使用）</option>
        <option value="3">3（已禁用）</option>
      </select>
      <input
        v-model.trim="queryForm.mobile"
        class="input"
        placeholder="发放手机号筛选（可留空）"
      />
      <input
        v-model.trim="queryForm.code_keyword"
        class="input"
        placeholder="邀请码关键字筛选（可留空）"
      />
      <input
        v-model.number="queryForm.limit"
        class="input"
        type="number"
        min="1"
        max="200"
        placeholder="返回数量上限（1~200）"
      />
      <button class="primary-btn" :disabled="loadingQuery" @click="handleQuery">
        {{ loadingQuery ? "查询中..." : "查询邀请码" }}
      </button>
    </section>

    <!-- 中文注释：新增用户状态修改能力（非邀请码状态） -->
    <section class="card">
      <h2 class="card-title">修改用户状态</h2>
      <p class="desc">支持按用户ID或手机号修改；状态仅支持 active / disabled。</p>
      <input
        v-model.number="userStatusForm.user_id"
        class="input"
        type="number"
        min="1"
        placeholder="用户ID（可留空，与手机号二选一）"
      />
      <input
        v-model.trim="userStatusForm.mobile"
        class="input"
        placeholder="手机号（可留空，与用户ID二选一）"
      />
      <select v-model="userStatusForm.status" class="input">
        <option value="active">active（启用）</option>
        <option value="disabled">disabled（禁用）</option>
      </select>
      <button class="primary-btn" :disabled="loadingUserStatus" @click="handleUpdateUserStatus">
        {{ loadingUserStatus ? "保存中..." : "修改用户状态" }}
      </button>
      <div v-if="lastUserStatusResult.user_id" class="result-box">
        用户ID：{{ lastUserStatusResult.user_id }}，手机号：{{ lastUserStatusResult.mobile || "-" }}，状态：{{ lastUserStatusResult.status }}
      </div>
    </section>

    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>

    <section v-if="generatedItems.length" class="card">
      <h2 class="card-title">最近生成结果</h2>
      <div class="list">
        <div v-for="item in generatedItems" :key="item.code" class="list-item">
          <span class="code">{{ item.code }}</span>
          <span class="meta">状态：{{ formatInviteStatus(item.status) }}</span>
          <span class="meta">过期：{{ formatDate(item.expires_time) }}</span>
        </div>
      </div>
    </section>

    <section v-if="lastIssued.code" class="card">
      <h2 class="card-title">最近发放结果</h2>
      <p>邀请码：{{ lastIssued.code }}</p>
      <p>状态：{{ formatInviteStatus(lastIssued.status) }}</p>
      <p>手机号：{{ lastIssued.issued_to_mobile || "-" }}</p>
      <p>发放时间：{{ formatDate(lastIssued.issued_time) }}</p>
    </section>

    <section v-if="inviteList.length" class="card">
      <h2 class="card-title">查询结果（共 {{ queryTotal }} 条）</h2>
      <div class="table-wrap">
        <table class="table">
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
            <tr v-for="item in inviteList" :key="item.code">
              <td>{{ item.code }}</td>
              <td>{{ formatInviteStatus(item.status) }}</td>
              <td>{{ item.issued_to_mobile || "-" }}</td>
              <td>{{ item.used_by_user_id ?? "-" }}</td>
              <td>{{ formatDate(item.issued_time) }}</td>
              <td>{{ formatDate(item.used_time) }}</td>
              <td>{{ formatDate(item.expires_time) }}</td>
              <td>{{ item.note || "-" }}</td>
              <td>
                <select
                  class="inline-select"
                  :value="getPendingStatus(item.code, item.status)"
                  @change="setPendingStatus(item.code, $event.target.value)"
                >
                  <option value="1">1（未使用）</option>
                  <option value="2">2（已使用）</option>
                  <option value="3">3（已禁用）</option>
                </select>
                <button class="inline-btn" @click="handleUpdateInviteStatus(item)">保存</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
// 中文注释：页面内统一使用 ref 管理状态
import { ref } from "vue";
import {
  generateInviteCodes,
  issueInviteCode,
  listInviteCodes,
  updateInviteCodeStatus,
  updateUserStatus,
} from "../api/auth";

// 中文注释：管理员密钥
const adminKey = ref("");

// 中文注释：邀请码管理表单
const generateForm = ref({ count: 10, expires_days: null, note: "" });
const issueForm = ref({ code: "", mobile: "" });
const queryForm = ref({ status: "", mobile: "", code_keyword: "", limit: 50 });

// 中文注释：新增用户状态管理表单
const userStatusForm = ref({ user_id: null, mobile: "", status: "active" });
const lastUserStatusResult = ref({});

// 中文注释：页面展示与加载态
const generatedItems = ref([]);
const lastIssued = ref({});
const inviteList = ref([]);
const queryTotal = ref(0);
const pendingStatusMap = ref({});
const loadingGenerate = ref(false);
const loadingIssue = ref(false);
const loadingQuery = ref(false);
const loadingUserStatus = ref(false);
const errorMessage = ref("");

// 中文注释：手机号格式校验
function validateMobile(mobile) {
  return /^1\d{10}$/.test(mobile);
}

// 中文注释：时间格式化展示
function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

// 中文注释：邀请码状态格式化
function formatInviteStatus(status) {
  if (status === "1") return "1（未使用）";
  if (status === "2") return "2（已使用）";
  if (status === "3") return "3（已禁用）";
  return status || "-";
}

// 中文注释：表格行内待保存状态缓存
function getPendingStatus(code, currentStatus) {
  return pendingStatusMap.value[code] || currentStatus || "1";
}

// 中文注释：更新表格行内待保存状态缓存
function setPendingStatus(code, status) {
  pendingStatusMap.value[code] = status;
}

// 中文注释：统一校验管理员密钥
function ensureAdminKey() {
  if (!adminKey.value) {
    errorMessage.value = "请输入管理员密钥";
    return false;
  }
  return true;
}

// 中文注释：生成邀请码
async function handleGenerate() {
  errorMessage.value = "";
  if (!ensureAdminKey()) return;

  if (!generateForm.value.count || generateForm.value.count < 1 || generateForm.value.count > 200) {
    errorMessage.value = "生成数量必须在 1~200 之间";
    return;
  }

  try {
    loadingGenerate.value = true;
    const res = await generateInviteCodes(
      {
        count: generateForm.value.count,
        expires_days: generateForm.value.expires_days || null,
        note: generateForm.value.note || null,
      },
      adminKey.value
    );
    generatedItems.value = res?.data?.items || [];
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "生成邀请码失败";
  } finally {
    loadingGenerate.value = false;
  }
}

// 中文注释：发放邀请码
async function handleIssue() {
  errorMessage.value = "";
  if (!ensureAdminKey()) return;

  if (!issueForm.value.code) {
    errorMessage.value = "请输入邀请码";
    return;
  }
  if (!validateMobile(issueForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    loadingIssue.value = true;
    const res = await issueInviteCode(
      {
        code: issueForm.value.code,
        mobile: issueForm.value.mobile,
      },
      adminKey.value
    );
    lastIssued.value = res?.data || {};
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "发放邀请码失败";
  } finally {
    loadingIssue.value = false;
  }
}

// 中文注释：查询邀请码
async function handleQuery() {
  errorMessage.value = "";
  if (!ensureAdminKey()) return;

  if (!queryForm.value.limit || queryForm.value.limit < 1 || queryForm.value.limit > 200) {
    errorMessage.value = "返回数量上限必须在 1~200 之间";
    return;
  }

  try {
    loadingQuery.value = true;
    const res = await listInviteCodes(
      {
        status: queryForm.value.status || null,
        mobile: queryForm.value.mobile || null,
        code_keyword: queryForm.value.code_keyword || null,
        limit: queryForm.value.limit,
      },
      adminKey.value
    );
    inviteList.value = res?.data?.items || [];
    queryTotal.value = res?.data?.total || 0;

    const nextMap = {};
    for (const item of inviteList.value) {
      nextMap[item.code] = item.status;
    }
    pendingStatusMap.value = nextMap;
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "查询邀请码失败";
  } finally {
    loadingQuery.value = false;
  }
}

// 中文注释：保存邀请码状态修改
async function handleUpdateInviteStatus(item) {
  errorMessage.value = "";
  if (!ensureAdminKey()) return;

  const targetStatus = getPendingStatus(item.code, item.status);
  if (!["1", "2", "3"].includes(targetStatus)) {
    errorMessage.value = "状态仅支持 1、2、3";
    return;
  }

  try {
    const res = await updateInviteCodeStatus(
      {
        code: item.code,
        status: targetStatus,
      },
      adminKey.value
    );
    item.status = res?.data?.status || targetStatus;
    item.used_by_user_id = res?.data?.used_by_user_id ?? item.used_by_user_id;
    item.used_time = res?.data?.used_time ?? item.used_time;
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "修改邀请码状态失败";
  }
}

// 中文注释：保存用户状态修改（非邀请码状态）
async function handleUpdateUserStatus() {
  errorMessage.value = "";
  if (!ensureAdminKey()) return;

  const userId = userStatusForm.value.user_id;
  const mobile = userStatusForm.value.mobile;
  const status = userStatusForm.value.status;

  if (!userId && !mobile) {
    errorMessage.value = "用户ID和手机号至少填写一个";
    return;
  }
  if (mobile && !validateMobile(mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }
  if (!["active", "disabled"].includes(status)) {
    errorMessage.value = "用户状态仅支持 active / disabled";
    return;
  }

  try {
    loadingUserStatus.value = true;
    const res = await updateUserStatus(
      {
        user_id: userId || null,
        mobile: mobile || null,
        status,
      },
      adminKey.value
    );
    lastUserStatusResult.value = res?.data || {};
  } catch (error) {
    errorMessage.value = error?.response?.data?.detail || "修改用户状态失败";
  } finally {
    loadingUserStatus.value = false;
  }
}
</script>

<style scoped>
.admin-console-page {
  width: 860px;
  max-width: calc(100vw - 32px);
  margin: 24px auto;
  font-family: Arial, sans-serif;
}

.title {
  margin-bottom: 16px;
  text-align: center;
}

.card {
  padding: 16px;
  margin-bottom: 14px;
  border: 1px solid #ddd;
  border-radius: 10px;
  background: #fff;
}

.card-title {
  margin: 0 0 10px;
}

.desc {
  margin: 0 0 10px;
  color: #666;
  font-size: 14px;
}

.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px;
  margin-bottom: 10px;
}

.primary-btn {
  padding: 10px 14px;
  cursor: pointer;
}

.error-box {
  margin-bottom: 14px;
  padding: 10px;
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
  border-radius: 8px;
}

.result-box {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #d9e8ff;
  background: #f5f9ff;
  border-radius: 8px;
  color: #2f5a9e;
  font-size: 14px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-item {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px;
  border: 1px solid #eee;
  border-radius: 8px;
}

.code {
  font-weight: 700;
}

.meta {
  color: #555;
  font-size: 13px;
}

.table-wrap {
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.table th,
.table td {
  border: 1px solid #e5e5e5;
  padding: 8px;
  text-align: left;
  white-space: nowrap;
}

.table th {
  background: #f7f7f7;
}

.inline-select {
  width: 130px;
  padding: 4px 6px;
  margin-right: 8px;
}

.inline-btn {
  padding: 4px 10px;
  cursor: pointer;
}
</style>
