# SeeTalk 设计文档

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

### 1.3 未纳入 MVP

| 功能 | 原因 |
|------|------|
| 流式输出 | 时间优先级，P1 可后续 PR 添加 |
| DashScope 云端 ASR/TTS | 成本控制，浏览器 API 零费用 |
| 用户登录与多用户 | 竞赛 MVP 不需要 |

---

## 2. 运营成本控制

### 2.1 想到的策略

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

### 2.2 实际采用的策略

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
| 会话超时 3600s | `ChatSessionManager.java` | 释放内存与限流状态 |

### 2.3 未采用及原因

| 策略 | 原因 |
|------|------|
| DashScope ASR/TTS | MVP 优先省钱，浏览器 API 够用 |
| 七牛云 API | 已选定 DashScope 作为模型提供商 |
| 本地 Ollama | 部署复杂，72h 不优先 |
| 持续视频流 | 成本极高，与竞赛成本控制目标冲突 |

---

## 3. 技术架构摘要

- **前端**：React 18 + Vite + TypeScript
- **后端**：Java 21 + Spring Boot 3.5 + Spring AI Alibaba DashScope
- **通信**：WebSocket `/ws/chat`
- **AI**：DashScope `qwen-vl-flash` 多模态对话

详见项目根目录 [README.md](../README.md)。
