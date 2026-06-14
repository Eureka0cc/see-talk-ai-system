# SeeTalk 设计文档

> 竞赛题目：**AI 视觉对话助手** — 打开摄像头与麦克风，让 AI 看到画面、听到语音并恰当回应。  
> 本文档回答：计划/实现了哪些用户故事，以及成本控制策略的设想与实践。

---

## 0. 产品愿景与演进

### 0.1 最初设想：腾讯会议式多人 AI 互动

项目立项时，除题目要求的「单人视觉对话」外，还曾设想一种 **类似腾讯会议的多人协作形态**：

| 设想 | 说明 |
|------|------|
| 多人同房 | 多位用户同时开启摄像头，进入同一「AI 房间」 |
| AI 作为虚拟成员 | 模型同时感知多路画面、听懂多人发言，像会议里的智能助手一样插话、总结、答疑 |
| 典型场景 | 小组讨论、远程协作、课堂答疑、家庭聚会里的 AI 主持 |

这在体验上更接近「共享空间 + 共同对话」，而非当前实现的「一对一私聊」。

### 0.2 为何收敛为单人 MVP

| 考量 | 说明 |
|------|------|
| **竞赛范围** | 题目聚焦单个用户与 AI 的视觉语音对话；单人闭环可在有限时间内打穿体验与成本 |
| **成本模型** | 多路视频流 × 多参与者会近似线性放大视觉 API 调用；与「端云协同成本控制」目标冲突 |
| **技术复杂度** | 需房间/席位管理、多路帧调度、说话人分离、混音 ASR、权限与状态同步 |
| **语音链路** | 浏览器 Web Speech API 面向单麦克风流；多人混音识别质量与延迟难保证 |
| **72h 交付** | 优先保证视觉理解准确性、语音自然度、持久化与 Agent 能力，而非多人同步 |

### 0.3 会议思路在当前版本中的保留

虽未实现多人同房，部分设想以 **单人形态** 落地，并为后续扩展预留空间：

- **历史会话侧栏** — 类似「会议记录」，可回看、搜索、删除历次对话
- **Agent 工具调用** — AI 可主动检索历史、搜索网页，承担「秘书/助理」角色
- **WebSocket + Redis 热会话** — 会话隔离清晰，架构上便于未来扩展为 Room / 多连接
- **人脸模糊** — 多人场景下的隐私需求，在单人 Demo 中先行验证

---

## 1. 用户故事

### 1.1 计划实现

| ID | 用户故事 | 优先级 |
|----|----------|--------|
| US-01 | 作为用户，我点击「开始对话」后摄像头和麦克风自动开启 | P0 |
| US-02 | 作为用户，我说话后 AI 能听懂并以文字+语音回复 | P0 |
| US-03 | 作为用户，我问视觉相关问题时 AI 能结合当前画面回答 | P0 |
| US-04 | 作为用户，我可以打字输入作为语音的补充 | P1 |
| US-05 | 作为用户，我可以清空对话记录重新开始 | P1 |
| US-06 | 作为用户，我能看到 WebSocket 连接状态和是否在说话 | P1 |
| US-07 | 作为用户，在 API 未配置或权限拒绝时能看到明确错误提示 | P2 |
| US-08 | 作为开发者，我可以通过环境变量配置 DashScope API Key 和模型 | P2 |
| US-09 | 作为用户，我可以查看以往会话列表及每会话的完整对话内容 | P0 |
| US-10 | 作为用户，AI 回复以流式文字呈现，我可在播报过程中一键打断 | P1 |
| US-11 | 作为用户，我可以问「之前聊过什么」时 AI 自动检索历史记录 | P1 |
| US-12 | 作为用户，我问实时信息时 AI 可联网搜索后回答 | P2 |
| US-13 | 作为用户，我希望画面中人脸被模糊以保护隐私 | P2 |

### 1.2 最终实现

| ID | 状态 | 实现说明 |
|----|------|----------|
| US-01 | 已实现 | `useCamera` + `useVoiceActivity` 在「开始对话」时并行启动 |
| US-02 | 已实现 | Web Audio VAD 检测静音 → Web Speech ASR → WebSocket 发送 → Speech Synthesis TTS 播报 |
| US-03 | 已实现 | 发送消息时 Canvas 抓帧 → Spring AI `UserMessage.media` → DashScope `qwen-vl-flash` |
| US-04 | 已实现 | ControlBar 文本输入框支持手动发送 |
| US-05 | 已实现 | `clear_history` WebSocket 消息同步清空前后端历史 |
| US-06 | 已实现 | 连接状态点、说话指示器、实时识别文本 |
| US-07 | 已实现 | 无 API Key、摄像头/麦克风权限、AI 调用失败均有错误气泡 |
| US-08 | 已实现 | `DASHSCOPE_API_KEY` 环境变量 + `application.yml` 可配置模型名 |
| US-09 | 已实现 | MySQL 持久化 + Redis 热会话 + 历史 API + 前端 HistoryPanel |
| US-10 | 已实现 | WebSocket 流式 `assistant_delta` + 新消息自动打断 TTS/生成 |
| US-11 | 已实现 | `ChatHistoryTools` @Tool 按关键词/时间/会话检索 |
| US-12 | 已实现 | `WebSearchTools` 调用 DuckDuckGo 免费搜索 |
| US-13 | 已实现 | `useFaceBlur` Canvas 人脸检测 + 像素化 |

### 1.3 未纳入 MVP

| 功能 | 原因 |
|------|------|
| **多人会议式 AI 同房** | 见 §0：成本、复杂度与竞赛范围；当前为单人 MVP |
| DashScope 云端 ASR/TTS | 成本控制，浏览器 Web Speech API 零云端语音费 |
| 用户登录与多租户 | 竞赛 MVP 不需要账号体系 |
| 刷新页面后无缝续接同一 WebSocket | 可从 MySQL 历史 API 恢复会话，非必须 |
| 持续上传视频流 | 成本极高，改为「发消息时单帧抓图」 |

---

## 2. 数据存储设计

### 2.1 三层分工

| 存储 | 职责 | 历史查询 | 会过期吗 |
|------|------|----------|----------|
| JVM 内存 | — | 否 | — |
| **Redis** | 当前 WebSocket 最近 20 轮上下文 + 帧率计数 | 否 | TTL 3600s（frames 60s） |
| **MySQL** | 全部 session + message 文本 | **历史页唯一数据源** | 软删前永久 |

- **写路径**：每轮 user/assistant 消息经 `ChatPersistenceService` 写入 `chat_message`，并更新 `chat_session`。
- **读路径**：历史页只查 MySQL，不回溯 Redis。
- **WebSocket `clear_history`**：仅清内存上下文，**不**软删已落库记录。
- **DELETE `/api/sessions/{id}`**：软删 session 及其 messages（`is_deleted=1`）。

### 2.2 表规范（全局强制）

所有表包含以下 4 个基础字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `create_time` | DATETIME NOT NULL | 创建时间 |
| `update_time` | DATETIME NOT NULL | 更新时间 |
| `is_deleted` | TINYINT(1) DEFAULT 0 | 软删除：0=正常，1=已删除 |

JPA 基类 `BaseEntity` + `@SQLRestriction("is_deleted = 0")` 默认过滤已删记录。

### 2.3 ER 关系

```mermaid
erDiagram
    chat_session ||--o{ chat_message : contains
    chat_session {
        bigint id PK
        varchar session_uuid UK
        varchar title
        datetime last_active_time
        int message_count
        datetime create_time
        datetime update_time
        tinyint is_deleted
    }
    chat_message {
        bigint id PK
        bigint session_id FK
        varchar role
        text content
        tinyint used_vision
        datetime create_time
        datetime update_time
        tinyint is_deleted
    }
```

### 2.5 Redis Key 设计（热会话）

| Key 模式 | 类型 | TTL | 说明 |
|----------|------|-----|------|
| `seetalk:session:{uuid}` | Hash | 3600s | dbSessionId、lastImageHash、lastActive |
| `seetalk:memory:{uuid}` | List | 3600s | 最近 N 条消息 JSON（role + text） |
| `seetalk:frames:{uuid}` | ZSet | 60s | 视觉帧率滑动窗口 |

每条消息后刷新 session/memory TTL，与 `session-timeout-seconds: 3600` 对齐。

---

### 2.4 历史 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sessions` | 分页列表，按 `last_active_time DESC` |
| GET | `/api/sessions/{id}/messages` | `{id}` 为表主键 BIGINT |
| DELETE | `/api/sessions/{id}` | 软删 session 及 messages |

---

## 3. 运营成本控制

### 3.1 想到的策略

| 策略 | 说明 |
|------|------|
| 选用小模型 | qwen-vl-flash 比 plus 系列便宜 |
| 端侧 ASR/TTS | 浏览器 Web Speech API 不计云端语音费 |
| 端侧 VAD | 静音后才发送，减少无效请求 |
| 按需单帧 | 非持续视频流上传 |
| 图像压缩 | 服务端缩放 + JPEG 压缩 |
| 感知哈希去重 | 连续相似帧跳过视觉调用 |
| 帧率限流 | 每会话每分钟上限 |
| 对话窗口截断 | 只保留最近 N 条消息 |
| 回复长度限制 | maxTokens 约 300 |
| DashScope 云端 ASR/TTS | 准确率更高但按量计费 |
| 七牛云多模型聚合 | 统一 API Key 切换模型 |
| 流式输出 | 改善体验，不直接降本 |

### 3.2 实际采用的策略

| 策略 | 实现位置 | 效果 |
|------|----------|------|
| qwen-vl-flash | `application.yml` | 降低视觉 token 单价 |
| 端侧 ASR/TTS | `useVoiceActivity.ts`, `useSpeechSynthesis.ts` | 语音链路零 API 成本 |
| 端侧 VAD | `useVoiceActivity.ts` | 避免连续发送空段/杂音 |
| 按需单帧 | `useCamera.captureFrame()` | 仅在用户发消息时上传一帧 |
| 图像压缩 640×480 JPEG75 | `ImageProcessService.java` | 减少 payload 与视觉 token |
| 感知哈希去重 | `ImageDeduplicator.java` | 静态画面连续提问时跳过视觉 |
| 帧率限流 12/min | `FrameRateLimiter.java` | 防止恶意/误操作刷 API |
| 对话窗口 20 条 | `ChatSession.trimHistory()` | 控制上下文 token |
| maxTokens 300 | `application.yml` | 限制回复长度 |
| 会话超时 3600s | `ChatSessionManager` + Redis TTL | 释放热数据与限流状态 |

### 3.3 未采用及原因

| 策略 | 原因 |
|------|------|
| DashScope ASR/TTS | MVP 优先省钱，浏览器 API 够用 |
| 七牛云 API | 已选定 DashScope 作为模型提供商 |
| 本地 Ollama | 部署复杂，72h 不优先 |
| 持续视频流 | 成本极高，与竞赛成本控制目标冲突 |

---

## 4. 技术架构摘要

- **前端**：React 18 + Vite + TypeScript
- **后端**：Java 21 + Spring Boot 3.5 + Spring AI Alibaba DashScope
- **持久化**：Spring Data JPA + MySQL（历史真相来源）
- **热会话**：Spring Data Redis（进行中对话上下文，TTL 过期不影响历史）
- **通信**：WebSocket `/ws/chat`；REST `/api/sessions` 历史查询
- **AI**：DashScope `qwen-vl-flash` 多模态对话

详见项目根目录 [README.md](../README.md)。
