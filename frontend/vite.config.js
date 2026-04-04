import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // 中文注释：允许局域网访问，便于本机和移动端联调
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        // 中文注释：认证接口继续复用本地 FastAPI 服务
        target: "http://127.0.0.1:8000",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
