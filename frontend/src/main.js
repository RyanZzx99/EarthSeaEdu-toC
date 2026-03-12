// 导入 createApp
import { createApp } from "vue";

// 导入根组件
import App from "./App.vue";

// 导入路由实例
import router from "./router";

// 创建应用
const app = createApp(App);

// 注册路由
app.use(router);

// 挂载应用
app.mount("#app");
