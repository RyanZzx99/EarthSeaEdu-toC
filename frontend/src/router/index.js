// 导入 createRouter 和 createWebHistory
import { createRouter, createWebHistory } from "vue-router";

// 导入页面组件
import LoginView from "../views/LoginView.vue";
import HomeView from "../views/HomeView.vue";

// 定义路由表
const routes = [
  {
    // 登录页
    path: "/login",
    name: "login",
    component: LoginView,
  },
  {
    // 首页
    path: "/",
    name: "home",
    component: HomeView,
    meta: {
      requiresAuth: true, // 标记该页面需要登录
    },
  },
];

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 从本地读取 token
  const token = localStorage.getItem("access_token");

  // 如果目标页面需要登录，但没有 token，则跳转登录页
  if (to.meta.requiresAuth && !token) {
    next("/login");
    return;
  }

  // 如果已登录，再访问登录页，可直接跳首页
  if (to.path === "/login" && token) {
    next("/");
    return;
  }

  // 其他情况正常放行
  next();
});

// 导出路由实例
export default router;
