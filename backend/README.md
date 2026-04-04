# Backend

FastAPI backend for EarthSeaEdu.

> 中文说明：该目录包含 EarthSeaEdu 的后端服务代码、配置和基础联调文件。

## Setup

1. Create a virtual environment.
2. Install dependencies:

```bash
pip install -r backend/requirements.txt
```

3. Copy `backend/.env.example` to `backend/.env`.
4. Start the server:

中文说明：
- 第 1 步：创建 Python 虚拟环境。
- 第 2 步：安装后端依赖。
- 第 3 步：复制环境变量模板并按实际数据库信息修改。
- 第 4 步：启动 FastAPI 服务。

SSH 隧道说明：
- 如果数据库不对外开放，只能先登录到跳板机，再访问内网 MySQL，请将 `SSH_ENABLED=true`。
- `SSH_HOST`、`SSH_PORT`、`SSH_USER`、`SSH_PASSWORD` 填 SSH 服务器信息。
- `SSH_REMOTE_BIND_HOST`、`SSH_REMOTE_BIND_PORT` 填 MySQL 在 SSH 服务器视角下可访问的地址与端口。
- `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD` 仍然要填写 MySQL 自己的库名、账号和密码。

```bash
uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000
```

## Structure

- `config/`: settings and database configuration
- `routers/`: API routes
- `schemas/`: response and request schemas
- `models/`: SQLAlchemy models
- `crud/`: data access layer
- `utils/`: shared helpers
- `main.py`: application entry

中文说明：
- `config/`：配置项和数据库连接初始化。
- `routers/`：接口路由定义。
- `schemas/`：请求与响应的数据模型。
- `models/`：ORM 数据库模型。
- `crud/`：数据访问逻辑。
- `utils/`：通用依赖和工具函数。
- `main.py`：应用启动入口。

## Available endpoints

- `GET /`
- `GET /api/v1/health`
- `GET /api/v1/db-health`

中文说明：
- `GET /`：检查服务是否启动。
- `GET /api/v1/health`：检查应用自身状态。
- `GET /api/v1/db-health`：检查数据库连接状态。
