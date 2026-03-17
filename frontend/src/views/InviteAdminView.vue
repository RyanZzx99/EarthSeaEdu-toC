<template>
  <!-- 邀请码管理页容器 -->
  <div class="invite-admin-page">
    <!-- 页面标题 -->
    <h1 class="title">邀请码管理</h1>

    <!-- 管理员密钥输入区域 -->
    <section class="card">
      <h2 class="card-title">管理员密钥</h2>
      <p class="desc">用于调用生成/发放/查询邀请码接口，请填写后再操作。</p>
      <input
        v-model.trim="adminKey"
        class="input"
        type="password"
        placeholder="请输入 X-Admin-Key"
      />
    </section>

    <!-- 生成邀请码区域 -->
    <section class="card">
      <h2 class="card-title">生成邀请码</h2>

      <!-- 生成数量 -->
      <input
        v-model.number="generateForm.count"
        class="input"
        type="number"
        min="1"
        max="200"
        placeholder="生成数量（1~200）"
      />

      <!-- 过期天数 -->
      <input
        v-model.number="generateForm.expires_days"
        class="input"
        type="number"
        min="1"
        max="3650"
        placeholder="过期天数（可留空）"
      />

      <!-- 批次备注 -->
      <input
        v-model.trim="generateForm.note"
        class="input"
        placeholder="批次备注（可留空）"
      />

      <!-- 生成按钮 -->
      <button class="primary-btn" :disabled="loadingGenerate" @click="handleGenerate">
        {{ loadingGenerate ? "生成中..." : "生成邀请码" }}
      </button>
    </section>

    <!-- 发放邀请码区域 -->
    <section class="card">
      <h2 class="card-title">发放邀请码</h2>

      <!-- 邀请码 -->
      <input
        v-model.trim="issueForm.code"
        class="input"
        placeholder="邀请码"
      />

      <!-- 发放目标手机号 -->
      <input
        v-model.trim="issueForm.mobile"
        class="input"
        placeholder="发放目标手机号"
      />

      <!-- 发放按钮 -->
      <button class="primary-btn" :disabled="loadingIssue" @click="handleIssue">
        {{ loadingIssue ? "发放中..." : "发放邀请码" }}
      </button>
    </section>

    <!-- 查询邀请码区域 -->
    <section class="card">
      <h2 class="card-title">查询邀请码</h2>

      <!-- 状态筛选 -->
      <select v-model="queryForm.status" class="input">
        <option value="">全部状态</option>
        <option value="1">1（未使用）</option>
        <option value="2">2（已使用）</option>
        <option value="3">3（已禁用）</option>
      </select>

      <!-- 发放手机号筛选 -->
      <input
        v-model.trim="queryForm.mobile"
        class="input"
        placeholder="发放手机号筛选（可留空）"
      />

      <!-- 邀请码关键字筛选 -->
      <input
        v-model.trim="queryForm.code_keyword"
        class="input"
        placeholder="邀请码关键字筛选（可留空）"
      />

      <!-- 返回数量上限 -->
      <input
        v-model.number="queryForm.limit"
        class="input"
        type="number"
        min="1"
        max="200"
        placeholder="返回数量上限（1~200）"
      />

      <!-- 查询按钮 -->
      <button class="primary-btn" :disabled="loadingQuery" @click="handleQuery">
        {{ loadingQuery ? "查询中..." : "查询邀请码" }}
      </button>
    </section>

    <!-- 错误提示 -->
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>

    <!-- 生成结果展示 -->
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

    <!-- 发放结果展示 -->
    <section v-if="lastIssued.code" class="card">
      <h2 class="card-title">最近发放结果</h2>
      <p>邀请码：{{ lastIssued.code }}</p>
      <p>状态：{{ formatInviteStatus(lastIssued.status) }}</p>
      <p>手机号：{{ lastIssued.issued_to_mobile || "-" }}</p>
      <p>发放时间：{{ formatDate(lastIssued.issued_time) }}</p>
    </section>

    <!-- 查询结果展示 -->
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
                <!-- 行内状态下拉 -->
                <select
                  class="inline-select"
                  :value="getPendingStatus(item.code, item.status)"
                  @change="setPendingStatus(item.code, $event.target.value)"
                >
                  <option value="1">1（未使用）</option>
                  <option value="2">2（已使用）</option>
                  <option value="3">3（已禁用）</option>
                </select>
                <!-- 行内保存按钮 -->
                <button class="inline-btn" @click="handleUpdateStatus(item)">保存</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
// 导入 Vue 响应式 API
import { ref } from "vue";

// 导入邀请码管理接口
import {
  generateInviteCodes,
  issueInviteCode,
  listInviteCodes,
  updateInviteCodeStatus,
} from "../api/auth";

// 管理员密钥
const adminKey = ref("");

// 生成邀请码表单
const generateForm = ref({
  // 生成数量，默认 10
  count: 10,
  // 过期天数，可空
  expires_days: null,
  // 批次备注
  note: "",
});

// 发放邀请码表单
const issueForm = ref({
  // 邀请码
  code: "",
  // 发放手机号
  mobile: "",
});

// 查询邀请码表单
const queryForm = ref({
  // 状态筛选：1=未使用，2=已使用，3=已禁用
  status: "",
  // 发放手机号筛选
  mobile: "",
  // 邀请码关键字筛选
  code_keyword: "",
  // 返回数量上限
  limit: 50,
});

// 最近生成结果
const generatedItems = ref([]);

// 最近发放结果
const lastIssued = ref({});

// 查询结果列表
const inviteList = ref([]);

// 查询总数
const queryTotal = ref(0);

// 生成接口 loading
const loadingGenerate = ref(false);

// 发放接口 loading
const loadingIssue = ref(false);

// 查询接口 loading
const loadingQuery = ref(false);

// 页面错误消息
const errorMessage = ref("");

// 邀请码行内待修改状态映射
// 说明：
// 1. key=邀请码 code
// 2. value=待提交状态（1/2/3）
const pendingStatusMap = ref({});

// 校验中国大陆手机号格式
function validateMobile(mobile) {
  return /^1\d{10}$/.test(mobile);
}

// 格式化时间展示
function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

// 邀请码状态格式化
function formatInviteStatus(status) {
  if (status === "1") return "1（未使用）";
  if (status === "2") return "2（已使用）";
  if (status === "3") return "3（已禁用）";
  return status || "-";
}

// 获取某个邀请码行的待修改状态
function getPendingStatus(code, currentStatus) {
  return pendingStatusMap.value[code] || currentStatus || "1";
}

// 设置某个邀请码行的待修改状态
function setPendingStatus(code, status) {
  pendingStatusMap.value[code] = status;
}

// 生成邀请码
async function handleGenerate() {
  // 清空错误
  errorMessage.value = "";

  // 管理员密钥校验
  if (!adminKey.value) {
    errorMessage.value = "请输入管理员密钥";
    return;
  }

  // 数量校验
  if (!generateForm.value.count || generateForm.value.count < 1 || generateForm.value.count > 200) {
    errorMessage.value = "生成数量必须在 1~200 之间";
    return;
  }

  try {
    // 打开 loading
    loadingGenerate.value = true;

    // 调用后端生成接口
    const res = await generateInviteCodes(
      {
        count: generateForm.value.count,
        expires_days: generateForm.value.expires_days || null,
        note: generateForm.value.note || null,
      },
      adminKey.value
    );

    // 保存返回结果用于展示
    generatedItems.value = res?.data?.items || [];
  } catch (error) {
    // 展示后端错误
    errorMessage.value = error?.response?.data?.detail || "生成邀请码失败";
  } finally {
    // 关闭 loading
    loadingGenerate.value = false;
  }
}

// 发放邀请码
async function handleIssue() {
  // 清空错误
  errorMessage.value = "";

  // 管理员密钥校验
  if (!adminKey.value) {
    errorMessage.value = "请输入管理员密钥";
    return;
  }

  // 邀请码校验
  if (!issueForm.value.code) {
    errorMessage.value = "请输入邀请码";
    return;
  }

  // 手机号校验
  if (!validateMobile(issueForm.value.mobile)) {
    errorMessage.value = "手机号格式不正确";
    return;
  }

  try {
    // 打开 loading
    loadingIssue.value = true;

    // 调用后端发放接口
    const res = await issueInviteCode(
      {
        code: issueForm.value.code,
        mobile: issueForm.value.mobile,
      },
      adminKey.value
    );

    // 保存最近发放结果
    lastIssued.value = res?.data || {};
  } catch (error) {
    // 展示后端错误
    errorMessage.value = error?.response?.data?.detail || "发放邀请码失败";
  } finally {
    // 关闭 loading
    loadingIssue.value = false;
  }
}

// 查询邀请码列表
async function handleQuery() {
  // 清空错误
  errorMessage.value = "";

  // 管理员密钥校验
  if (!adminKey.value) {
    errorMessage.value = "请输入管理员密钥";
    return;
  }

  // limit 校验
  if (!queryForm.value.limit || queryForm.value.limit < 1 || queryForm.value.limit > 200) {
    errorMessage.value = "返回数量上限必须在 1~200 之间";
    return;
  }

  try {
    // 打开 loading
    loadingQuery.value = true;

    // 调用后端查询接口
    const res = await listInviteCodes(
      {
        status: queryForm.value.status || null,
        mobile: queryForm.value.mobile || null,
        code_keyword: queryForm.value.code_keyword || null,
        limit: queryForm.value.limit,
      },
      adminKey.value
    );

    // 保存查询结果
    inviteList.value = res?.data?.items || [];
    queryTotal.value = res?.data?.total || 0;

    // 每次查询后，同步初始化行内状态缓存
    const nextMap = {};
    for (const item of inviteList.value) {
      nextMap[item.code] = item.status;
    }
    pendingStatusMap.value = nextMap;
  } catch (error) {
    // 展示后端错误
    errorMessage.value = error?.response?.data?.detail || "查询邀请码失败";
  } finally {
    // 关闭 loading
    loadingQuery.value = false;
  }
}

// 保存某一行的邀请码状态
async function handleUpdateStatus(item) {
  // 清空错误
  errorMessage.value = "";

  // 管理员密钥校验
  if (!adminKey.value) {
    errorMessage.value = "请输入管理员密钥";
    return;
  }

  // 读取待提交状态
  const targetStatus = getPendingStatus(item.code, item.status);

  // 状态值基础校验
  if (!["1", "2", "3"].includes(targetStatus)) {
    errorMessage.value = "状态仅支持 1、2、3";
    return;
  }

  try {
    // 调后端更新状态接口
    const res = await updateInviteCodeStatus(
      {
        code: item.code,
        status: targetStatus,
      },
      adminKey.value
    );

    // 本地更新当前行展示，减少一次刷新等待
    item.status = res?.data?.status || targetStatus;
    item.used_by_user_id = res?.data?.used_by_user_id ?? item.used_by_user_id;
    item.used_time = res?.data?.used_time ?? item.used_time;
  } catch (error) {
    // 显示后端错误
    errorMessage.value = error?.response?.data?.detail || "修改邀请码状态失败";
  }
}
</script>

<style scoped>
/* 页面容器 */
.invite-admin-page {
  width: 760px;
  max-width: calc(100vw - 32px);
  margin: 24px auto;
  font-family: Arial, sans-serif;
}

/* 页面标题 */
.title {
  margin-bottom: 16px;
  text-align: center;
}

/* 卡片容器 */
.card {
  padding: 16px;
  margin-bottom: 14px;
  border: 1px solid #ddd;
  border-radius: 10px;
  background: #fff;
}

/* 卡片标题 */
.card-title {
  margin: 0 0 10px;
}

/* 描述文字 */
.desc {
  margin: 0 0 10px;
  color: #666;
  font-size: 14px;
}

/* 输入框 */
.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px;
  margin-bottom: 10px;
}

/* 主按钮 */
.primary-btn {
  padding: 10px 14px;
  cursor: pointer;
}

/* 错误提示 */
.error-box {
  margin-bottom: 14px;
  padding: 10px;
  border: 1px solid #f0b4b4;
  background: #fff4f4;
  color: #c0392b;
  border-radius: 8px;
}

/* 列表容器 */
.list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 列表项 */
.list-item {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px;
  border: 1px solid #eee;
  border-radius: 8px;
}

/* 邀请码高亮 */
.code {
  font-weight: 700;
}

/* 元信息 */
.meta {
  color: #555;
  font-size: 13px;
}

/* 表格横向滚动容器 */
.table-wrap {
  overflow-x: auto;
}

/* 表格 */
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

/* 表头和单元格边框 */
.table th,
.table td {
  border: 1px solid #e5e5e5;
  padding: 8px;
  text-align: left;
  white-space: nowrap;
}

/* 表头底色 */
.table th {
  background: #f7f7f7;
}

/* 表格行内状态下拉 */
.inline-select {
  width: 130px;
  padding: 4px 6px;
  margin-right: 8px;
}

/* 表格行内保存按钮 */
.inline-btn {
  padding: 4px 10px;
  cursor: pointer;
}
</style>
