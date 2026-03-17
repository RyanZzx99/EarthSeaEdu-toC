/**
 * 前端路由配置
 *
 * 主要职责：
 * 1. 定义页面路由
 * 2. 做登录态路由守卫
 * 3. 未登录时拦截需要认证的页面
 */

 // 导入 createRouter 和 createWebHistory
import { createRouter, createWebHistory } from "vue-router";

// 导入登录页
import LoginView from "../views/LoginView.vue";

// 导入首页
import HomeView from "../views/HomeView.vue";

/**
 * 路由表
 *
 * 说明：
 * 1. /login 为登录页
 * 2. / 为首页
 * 3. 首页通过 meta.requiresAuth 标记为需要登录
 */
const routes = [
  {
    path: "/login",
    name: "login",
    component: LoginView,
  },
  {
    path: "/",
    name: "home",
    component: HomeView,
    meta: {
      requiresAuth: true,
    },
  },
];

/**
 * 创建路由实例
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
});

/**
 * 全局前置守卫
 *
 * 逻辑：
 * 1. 如果目标路由需要登录，但本地没有 token，则跳转到 /login
 * 2. 如果用户已经登录，再访问 /login，则直接跳到首页
 */
router.beforeEach((to, from, next) => {
  // 从本地存储读取 access token
  const token = localStorage.getItem("access_token");

  // 判断目标页面是否要求登录
  // 使用可选链 to.meta?.requiresAuth，可避免编辑器提示“未解析的变量 requiresAuth”
  const requiresAuth = to.matched.some(
    record => record.meta.requiresAuth
  )

  // 如果目标页面需要登录，但当前没有 token，则跳转到登录页
  if (requiresAuth && !token) {
    next("/login");
    return;
  }

  // 如果已经登录，再访问登录页，则直接跳到首页
  if (to.path === "/login" && token) {
    next("/");
    return;
  }

  // 其他情况正常放行
  next();
});

// 导出路由实例
export default router;