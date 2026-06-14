# SeeTalk — AI 视觉对话助手

打开摄像头与麦克风，让 AI 看到你眼前的画面、听到你说的话，并给出自然的中文回应。

## Demo 视频

Bilibili 演示视频（待录制上传后替换为真实链接）：

https://www.bilibili.com/video/BVxxxxxxxx

> 视频需包含旁白讲解，覆盖摄像头、语音对话、视觉理解、成本控制等核心功能。

## 功能特性

- 实时摄像头预览与语音对话
- 基于 DashScope `qwen-vl-flash` 的视觉理解（Spring AI 多模态）
- 浏览器端语音识别（Web Speech API）与语音播报（Speech Synthesis）
- VAD 静音检测，说话结束后自动发送
- 图像压缩、去重、帧率限流等成本控制策略
- 支持文字输入与清空对话历史
- 对话历史持久化（MySQL）与历史记录侧栏查看
- Redis 热会话：进行中对话上下文与帧率限流（TTL 3600s）

## 项目结构

```
see-talk-ai-system/
├── backend/          # Spring Boot 3.5 + Spring AI Alibaba
├── frontend/         # React + Vite + TypeScript
├── docs/DESIGN.md    # 设计文档（用户故事 + 成本控制）
├── docs/PR_DESCRIPTIONS.md  # PR 描述模板
└── README.md
```

## 环境要求

- Java 21+
- Maven 3.9+
- Node.js 18+
- Chrome 或 Edge（推荐，Web Speech API 支持较好）
- DashScope API Key（[阿里云百炼控制台](https://bailian.console.aliyun.com/) 获取）
- MySQL 8+ 与 Redis 7+（本地可 `docker compose up mysql redis -d` 启动依赖）

## 快速启动

### 1. 配置 API Key

```powershell
$env:DASHSCOPE_API_KEY="sk-your-api-key"
```

也可参考 [backend/.env.example](backend/.env.example)。

### 2. 启动 MySQL 与 Redis（若本地未安装）

```bash
docker compose up mysql redis -d
```

默认连接：`mysql://seetalk:seetalk@localhost:3306/seetalk`，`redis://localhost:6379`。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/health`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`

右侧可切换「当前对话」与「历史记录」Tab；历史数据来自 `GET /api/sessions`。

**连接不上？** 开发环境前端会直连 `ws://127.0.0.1:8080/ws/chat`（见 `frontend/.env.development`）。请确认：
1. 后端已启动且 `GET http://localhost:8080/health` 返回 200
2. MySQL / Redis 已就绪（`docker compose up mysql redis -d`）
3. 修改后端 CORS / WebSocket 配置后需 **重启后端**
4. 新增或修改 `frontend/.env.development` 后需 **重启 `npm run dev`**

### 5. Docker Compose（MySQL + Redis + 全栈）

```bash
DASHSCOPE_API_KEY=sk-your-key docker compose up --build
```

历史数据写入 MySQL；进行中会话上下文写入 Redis。

## PR 提交记录

| # | 分支 | Commit 说明 |
|---|------|-------------|
| 01 | feat/project-scaffold | 初始化前后端项目骨架 |
| 02 | feat/health-endpoint | 添加后端健康检查接口 |
| 03 | feat/websocket-skeleton | WebSocket 会话握手与消息协议 |
| 04 | feat/image-compression | 图像压缩服务 |
| 05 | feat/image-dedup | 感知哈希去重 |
| 06 | feat/frame-rate-limit | 视觉帧率限流 |
| 07 | feat/dashscope-text-chat | Spring AI DashScope 纯文本对话 |
| 08 | feat/vision-multimodal | qwen-vl-flash 多模态视觉对话 |
| 09 | feat/frontend-layout-camera | 前端双栏布局与摄像头 |
| 10 | feat/frontend-websocket | 前端 WebSocket 联调 |
| 11 | feat/frontend-vad-asr | VAD 与语音识别 |
| 12 | feat/frontend-tts | TTS 语音播报 |
| 13 | docs/design-and-readme | 设计文档与 README |
| 14 | docs/demo-video | Demo 视频链接 |
| 15 | feat/jpa-entities | BaseEntity + chat_session/chat_message + JPA 配置 |
| 16 | feat/chat-persistence | ChatPersistenceService + VisionChatService 落库 |
| 17 | feat/history-api | REST 历史查询与软删除 API |
| 18 | feat/history-panel | 前端 HistoryPanel + Tab 切换 |
| 19 | feat/redis-hot-session | Redis 热会话 + 移除 H2，统一 MySQL |

PR 描述模板见 [docs/PR_DESCRIPTIONS.md](docs/PR_DESCRIPTIONS.md)。

## 依赖说明

| 依赖 | 用途 |
|------|------|
| Spring Boot 3.5 | Web 框架、WebSocket |
| Spring Data JPA + MySQL | 对话历史持久化 |
| Spring Data Redis | 热会话上下文与帧率限流 |
| Spring AI Alibaba DashScope | qwen-vl-flash 多模态 |
| React 18 + Vite 6 | 前端 UI |

详见 [docs/DESIGN.md](docs/DESIGN.md)。

## 许可证

本项目为七牛云竞赛参赛作品，源代码将在提交截止后公开。
