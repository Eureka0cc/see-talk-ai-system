# PR 描述参考（复制到 Gitee/GitHub PR 正文）

## PR-01 feat: 初始化前后端项目骨架

**功能描述**：建立 Spring Boot 后端与 React+Vite 前端目录结构，添加 .gitignore 与 README 简介。

**实现思路**：后端使用 Spring Boot 3.5 Parent POM；前端使用 Vite 6 + React 18 + TypeScript。

**测试方式**：
- [ ] `cd backend && mvn compile`
- [ ] `cd frontend && npm install`

---

## PR-02 feat: 添加后端健康检查接口

**功能描述**：提供 `GET /health` 接口，返回服务状态，供部署探活使用。

**实现思路**：Spring MVC RestController + CORS 配置支持前端跨域。

**测试方式**：
- [ ] `mvn spring-boot:run` 后访问 `http://localhost:8080/health`

---

## PR-03 feat: 实现 WebSocket 会话握手与消息协议

**功能描述**：`/ws/chat` 建立连接后返回 session 消息，支持 ping/pong 与 clear_history。

**实现思路**：Spring WebSocket TextWebSocketHandler + 会话管理器。

**测试方式**：
- [ ] DevTools 连接 `ws://localhost:8080/ws/chat`，收到 `type: session`

---

## PR-04 feat: 添加图像压缩服务以降低视觉 API 成本

**功能描述**：将上传图像缩放至 640x480 并以 JPEG quality 75 压缩。

**实现思路**：Java ImageIO 缩放 + JPEG 压缩，配置项见 application.yml。

**测试方式**：
- [ ] `mvn test -Dtest=ImageProcessServiceTest`

---

## PR-05 feat: 添加感知哈希去重跳过相似帧

**功能描述**：对连续相似画面计算感知哈希，避免重复视觉 API 调用。

**实现思路**：8x8 灰度图平均值哈希。

**测试方式**：
- [ ] 相同图片两次 computeHash 结果一致

---

## PR-06 feat: 添加每会话视觉帧率限流

**功能描述**：每会话每分钟最多 12 帧视觉请求。

**实现思路**：滑动窗口时间戳列表 + synchronized 控制。

**测试方式**：
- [ ] 单元测试或集成测试验证超限返回 false

---

## PR-07 feat: 接入 Spring AI DashScope 纯文本对话

**功能描述**：配置 DashScope API，WebSocket user_message 可触发纯文本 AI 回复。

**实现思路**：spring-ai-alibaba-starter-dashscope + ChatClient + VisionChatService。

**测试方式**：
- [ ] 配置 DASHSCOPE_API_KEY，发送文本消息收到 assistant_message

---

## PR-08 feat: 实现 qwen-vl-flash 多模态视觉对话

**功能描述**：支持 image 字段，调用 UserMessage.media 进行视觉理解，响应含 used_vision。

**实现思路**：压缩+去重+限流通过后附带 JPEG media 调用 ChatClient。

**测试方式**：
- [ ] 发送带截图的消息，used_vision=true 且回复与画面相关

---

## PR-09 ~ PR-12 前端系列

见各 commit 标题，分别覆盖：布局摄像头、WebSocket、VAD+ASR、TTS。

---

## PR-13 docs: 添加设计文档与完善 README

**功能描述**：竞赛必填设计文档与完整 README 启动说明。

**测试方式**：
- [ ] 按 README 步骤可复现 Demo

---

## PR-14 docs: 更新 Demo 视频链接

**功能描述**：README 填入 Bilibili 可播放链接。

**测试方式**：
- [ ] 链接可正常打开播放
