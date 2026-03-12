import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],

  server: {
    // 允许局域网访问，方便你手机或其他设备调试
    host: "0.0.0.0",

    // 前端开发端口
    port: 5173,

    proxy: {
      // 只要请求路径以 /api 开头，就代理到后端
      "/api": {
        // 你的 FastAPI 地址
        target: "http://127.0.0.1:8000",

        // 修改请求源
        changeOrigin: true,

        // 是否启用 https 校验证书
        secure: false,
      },
    },
  },
});
