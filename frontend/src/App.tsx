import { useCallback, useEffect, useRef, useState } from "react";
import { ChatPanel } from "./components/ChatPanel";
import { ControlBar } from "./components/ControlBar";
import { HistoryPanel } from "./components/HistoryPanel";
import { VideoPanel } from "./components/VideoPanel";
import { useCamera } from "./hooks/useCamera";
import { useFaceBlur } from "./hooks/useFaceBlur";
import { useHistory } from "./hooks/useHistory";
import { useSpeechSynthesis } from "./hooks/useSpeechSynthesis";
import { useVoiceActivity } from "./hooks/useVoiceActivity";
import { useWebSocket } from "./hooks/useWebSocket";
import type { ChatMessage, SessionPhase, WsIncoming } from "./types";
import type { HistoryMessage } from "./types/history";
import "./App.css";

type MainTab = "live" | "history";
const IMAGE_MIN_INTERVAL_MS = 3000;
const VISION_REQUEST_REGEX =
  /看到什么|看到了吗|你看到|帮我看看|帮我看一下|画面|镜头|摄像头|图像|图片|照片|这张|那张|脸上|穿着|身后|旁边|手里|在干嘛|在做什么|在干什么|做什么|干什么|干嘛|看出|这是什么|那是什么|这个是什么|那个是什么|这个呢|那个呢|什么颜色|长什么样|好不好看|这边|那边/;

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<MainTab>("live");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isThinking, setIsThinking] = useState(false);
  const [isFaceBlurEnabled, setIsFaceBlurEnabled] = useState(false);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [isStartingSession, setIsStartingSession] = useState(false);
  const [sessionStartError, setSessionStartError] = useState<string | null>(null);
  const [chatError, setChatError] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [sessionPhase, setSessionPhase] = useState<SessionPhase>("idle");
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [sessionLockId, setSessionLockId] = useState<string | null>(null);
  const history = useHistory();
  const isSessionActiveRef = useRef(false);
  const sendRef = useRef<(p: Record<string, unknown>) => boolean>(() => false);
  const sendMessageRef = useRef<(text: string) => void>(() => {});
  const captureFrameRef = useRef<() => string | null>(() => null);
  const streamingMessageIdRef = useRef<string | null>(null);
  const streamingBufferRef = useRef<Map<string, string>>(new Map());
  const rafPendingRef = useRef(false);
  const activeTtsMessageIdRef = useRef<string | null>(null);
  const ttsStartTimeRef = useRef(0);
  const isTtsSpeakingRef = useRef(false);
  const isStreamingRef = useRef(false);
  const stopTtsRef = useRef<() => void>(() => {});
  const setRecognitionSuppressedRef = useRef<(enabled: boolean) => void>(() => {});
  const lastImageSentTimeRef = useRef(0);
  const expectedSessionIdRef = useRef<string | null>(null);
  const camera = useCamera();
  const faceBlur = useFaceBlur(
    camera.videoRef,
    camera.isActive && camera.isCameraEnabled && isFaceBlurEnabled,
  );
  captureFrameRef.current = camera.captureFrame;

  const historyToLiveMessages = useCallback((historyMessages: HistoryMessage[]): ChatMessage[] => {
    return historyMessages.map((message, index) => {
      const parsed = Date.parse(message.createTime);
      return {
        id: message.id || uid(),
        role: message.role === "assistant" ? "assistant" : "user",
        text: message.content,
        usedVision: message.usedVision,
        timestamp: Number.isNaN(parsed) ? Date.now() + index : parsed,
      };
    });
  }, []);

  const sendMessage = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    if (sessionLockId && currentSessionId !== sessionLockId) {
      setChatError("会话尚未切换完成，请重试继续对话");
      return;
    }

    const now = Date.now();
    const userMightWantVision = VISION_REQUEST_REGEX.test(trimmed);
    const shouldSendImage =
      userMightWantVision || now - lastImageSentTimeRef.current > IMAGE_MIN_INTERVAL_MS;
    const image = shouldSendImage ? captureFrameRef.current() : null;
    if (shouldSendImage && image) {
      lastImageSentTimeRef.current = now;
    }

    const sent = sendRef.current({
      type: "user_message",
      text: trimmed,
      image,
    });
    if (!sent) return;

    setChatError(null);
    setMessages((prev) => [
      ...prev,
      { id: uid(), role: "user", text: trimmed, timestamp: Date.now() },
    ]);
    setIsThinking(true);
  }, [currentSessionId, sessionLockId]);
  sendMessageRef.current = sendMessage;

  const handleSpeechStart = useCallback(() => {
    const streamingId = streamingMessageIdRef.current;
    const ttsId = activeTtsMessageIdRef.current;
    const canInterruptTts =
      isTtsSpeakingRef.current && Date.now() - ttsStartTimeRef.current >= 300;
    const canInterruptStream = isStreamingRef.current && streamingId != null;

    if (!canInterruptTts && !canInterruptStream) return;

    const interruptedMessageId = canInterruptTts ? (ttsId ?? streamingId) : streamingId;

    if (canInterruptTts) {
      stopTtsRef.current();
      isTtsSpeakingRef.current = false;
      activeTtsMessageIdRef.current = null;
      setRecognitionSuppressedRef.current(false);
    }

    if (!interruptedMessageId) return;
    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === interruptedMessageId ? { ...msg, interrupted: true } : msg,
      ),
    );
  }, []);

  const voice = useVoiceActivity({
    onSpeechStart: handleSpeechStart,
    onSpeechEnd: (text) => {
      if (isSessionActiveRef.current) sendMessageRef.current(text);
    },
  });

  const tts = useSpeechSynthesis({
    onStart: () => {
      if (!isSessionActiveRef.current) return;
      isTtsSpeakingRef.current = true;
      ttsStartTimeRef.current = Date.now();
      voice.setRecognitionSuppressed(true);
    },
    onEnd: () => {
      if (!isSessionActiveRef.current) return;
      isTtsSpeakingRef.current = false;
      activeTtsMessageIdRef.current = null;
      voice.setRecognitionSuppressed(false);
    },
  });
  stopTtsRef.current = tts.stop;
  setRecognitionSuppressedRef.current = voice.setRecognitionSuppressed;

  const handleWsMessage = useCallback((data: WsIncoming) => {
    switch (data.type) {
      case "session":
        if (data.session_id != null) {
          const incomingSessionId = String(data.session_id);
          const expectedSessionId = expectedSessionIdRef.current;
          if (expectedSessionId && incomingSessionId !== expectedSessionId) {
            setChatError("历史会话切换失败：服务端返回了不同的 session_id");
            setCurrentSessionId(null);
            break;
          }
          setCurrentSessionId(incomingSessionId);
          if (expectedSessionId && incomingSessionId === expectedSessionId) {
            expectedSessionIdRef.current = null;
          }
        }
        break;
      case "thinking":
        setIsThinking(true);
        break;
      case "assistant_start": {
        const messageId = data.message_id ?? uid();
        streamingMessageIdRef.current = messageId;
        streamingBufferRef.current.delete(messageId);
        isStreamingRef.current = true;
        setIsStreaming(true);
        setIsThinking(false);
        setMessages((prev) => [
          ...prev,
          {
            id: messageId,
            role: "assistant",
            text: "",
            usedVision: data.used_vision,
            timestamp: Date.now(),
          },
        ]);
        break;
      }
      case "assistant_delta": {
        const messageId = data.message_id;
        if (!messageId || !data.delta) break;

        const prev = streamingBufferRef.current.get(messageId) || "";
        streamingBufferRef.current.set(messageId, prev + data.delta);

        if (!rafPendingRef.current) {
          rafPendingRef.current = true;
          requestAnimationFrame(() => {
            rafPendingRef.current = false;

            streamingBufferRef.current.forEach((deltaText, msgId) => {
              setMessages((prevMessages) =>
                prevMessages.map((msg) =>
                  msg.id === msgId ? { ...msg, text: msg.text + deltaText } : msg,
                ),
              );
            });
            streamingBufferRef.current.clear();
          });
        }
        break;
      }
      case "assistant_done": {
        const messageId = data.message_id;
        streamingMessageIdRef.current = null;
        streamingBufferRef.current.forEach((deltaText, msgId) => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === msgId ? { ...msg, text: msg.text + deltaText } : msg,
            ),
          );
        });
        streamingBufferRef.current.clear();
        isStreamingRef.current = false;
        setIsStreaming(false);
        setIsThinking(false);
        let shouldSpeak = Boolean(data.text);
        if (messageId) {
          setMessages((prev) =>
            prev.map((msg) => {
              if (msg.id !== messageId) return msg;
              if (msg.interrupted) {
                shouldSpeak = false;
              }
              return {
                ...msg,
                text: data.text ?? msg.text,
                usedVision: data.used_vision,
              };
            }),
          );
        }
        if (data.text && shouldSpeak) {
          activeTtsMessageIdRef.current = messageId ?? null;
          tts.speak(data.text);
        }
        break;
      }
      case "assistant_message": {
        setIsThinking(false);
        const messageId = data.message_id ?? uid();
        let shouldSpeak = Boolean(data.text);
        setMessages((prev) => {
          const exists = prev.some((msg) => msg.id === messageId);
          if (exists) {
            return prev.map((msg) => {
              if (msg.id !== messageId) return msg;
              if (msg.interrupted) {
                shouldSpeak = false;
              }
              return {
                ...msg,
                text: data.text ?? msg.text,
                usedVision: data.used_vision,
              };
            });
          }
          return [
            ...prev,
            {
              id: messageId,
              role: "assistant",
              text: data.text ?? "",
              usedVision: data.used_vision,
              timestamp: Date.now(),
            },
          ];
        });
        if (data.text && shouldSpeak) {
          activeTtsMessageIdRef.current = messageId;
          tts.speak(data.text);
        }
        break;
      }
      case "history_cleared":
        streamingBufferRef.current.clear();
        rafPendingRef.current = false;
        isStreamingRef.current = false;
        setIsStreaming(false);
        setMessages([]);
        setIsThinking(false);
        break;
      case "error": {
        streamingBufferRef.current.clear();
        rafPendingRef.current = false;
        isStreamingRef.current = false;
        setIsStreaming(false);
        setIsThinking(false);
        const failedStreamId = streamingMessageIdRef.current;
        streamingMessageIdRef.current = null;
        if (failedStreamId) {
          setMessages((prev) => prev.filter((msg) => msg.id !== failedStreamId));
        }
        const errorMessage = data.message ?? "发生错误，请重试";
        if (errorMessage !== "请等待当前回复完成") {
          setChatError(errorMessage);
        }
        break;
      }
    }
  }, [tts]);

  const handleDisconnect = useCallback(() => {
    setIsThinking((prev) => {
      if (prev) {
        setChatError("连接已断开，消息可能未送达，请点击重试后重新发送");
      }
      return false;
    });
  }, []);

  const { status, reconnect, send: wsSend, ensureConnected, wsUrl } = useWebSocket(
    handleWsMessage,
    handleDisconnect,
  );
  sendRef.current = wsSend;

  const canSend = isSessionActive && status === "connected" && !isThinking;

  const continueHistorySession = useCallback(async (sessionId: string) => {
    const targetSessionId = sessionId.trim();
    if (!targetSessionId) return;

    setIsStartingSession(true);
    setSessionStartError(null);
    setChatError(null);
    setActiveTab("live");
    setSessionLockId(targetSessionId);
    setCurrentSessionId(targetSessionId);
    expectedSessionIdRef.current = targetSessionId;

    try {
      const historyMessages = await history.fetchMessages(targetSessionId);
      setMessages(historyToLiveMessages(historyMessages));
    } catch (e) {
      setSessionStartError(e instanceof Error ? e.message : "加载历史消息失败");
      setIsStartingSession(false);
      return;
    }

    if (isSessionActiveRef.current) {
      camera.stop();
      voice.stop();
      tts.stop();
      isSessionActiveRef.current = false;
      setIsSessionActive(false);
    }

    isTtsSpeakingRef.current = false;
    activeTtsMessageIdRef.current = null;
    ttsStartTimeRef.current = 0;
    streamingMessageIdRef.current = null;
    streamingBufferRef.current.clear();
    rafPendingRef.current = false;
    setIsStreaming(false);
    setIsThinking(false);

    reconnect({ sessionId: targetSessionId });
    const connected = await ensureConnected(2000, { sessionId: targetSessionId });
    if (!connected) {
      setSessionStartError(`无法恢复历史会话（${wsUrl}），请确认后端已启动`);
      setIsStartingSession(false);
      return;
    }

    const cameraOk = await camera.start();
    if (!cameraOk) {
      setSessionStartError(camera.error ?? "无法访问摄像头，请检查权限");
      setIsStartingSession(false);
      return;
    }

    await voice.start();
    isSessionActiveRef.current = true;
    setIsSessionActive(true);
    setSessionPhase("active");
    setIsStartingSession(false);
  }, [history, historyToLiveMessages, camera, voice, tts, reconnect, ensureConnected, wsUrl]);

  const startSession = useCallback(async () => {
    setIsStartingSession(true);
    setSessionStartError(null);
    setChatError(null);
    setSessionLockId(null);
    expectedSessionIdRef.current = null;

    const connected = await ensureConnected(undefined, { sessionId: null });
    if (!connected) {
      setSessionStartError(`无法连接服务器（${wsUrl}），请确认后端已在 8080 端口运行`);
      setIsStartingSession(false);
      return;
    }

    const cameraOk = await camera.start();
    if (!cameraOk) {
      setSessionStartError(camera.error ?? "无法访问摄像头，请检查权限或设备");
      setIsStartingSession(false);
      return;
    }

    await voice.start();

    isSessionActiveRef.current = true;
    setIsSessionActive(true);
    setSessionPhase("active");
    setIsStartingSession(false);
  }, [camera, voice, ensureConnected, wsUrl]);

  const startNewSession = useCallback(async () => {
    setIsStartingSession(true);
    setSessionStartError(null);
    setChatError(null);

    if (isSessionActiveRef.current) {
      camera.stop();
      voice.stop();
      tts.stop();
      isSessionActiveRef.current = false;
      setIsSessionActive(false);
    }

    isTtsSpeakingRef.current = false;
    activeTtsMessageIdRef.current = null;
    ttsStartTimeRef.current = 0;
    streamingMessageIdRef.current = null;
    streamingBufferRef.current.clear();
    rafPendingRef.current = false;
    setMessages([]);
    setIsStreaming(false);
    setIsThinking(false);
    setCurrentSessionId(null);
    setSessionLockId(null);
    expectedSessionIdRef.current = null;

    reconnect({ sessionId: null });
    const connected = await ensureConnected(800, { sessionId: null });
    if (!connected) {
      setSessionStartError(`无法连接服务器（${wsUrl}），请确认后端已启动`);
      setIsStartingSession(false);
      return;
    }

    const cameraOk = await camera.start();
    if (!cameraOk) {
      setSessionStartError(camera.error ?? "无法访问摄像头，请检查权限");
      setIsStartingSession(false);
      return;
    }

    await voice.start();

    isSessionActiveRef.current = true;
    setIsSessionActive(true);
    setSessionPhase("active");
    setIsStartingSession(false);
  }, [camera, voice, tts, reconnect, ensureConnected, wsUrl]);

  const stopSession = useCallback(() => {
    camera.stop();
    tts.stop();
    voice.stop();
    isTtsSpeakingRef.current = false;
    activeTtsMessageIdRef.current = null;
    streamingMessageIdRef.current = null;
    streamingBufferRef.current.clear();
    rafPendingRef.current = false;
    ttsStartTimeRef.current = 0;
    isSessionActiveRef.current = false;
    setIsStreaming(false);
    setIsSessionActive(false);
    setSessionPhase("ended");
    setIsThinking(false);
    setSessionStartError(null);
  }, [camera, voice, tts]);

  const clearHistory = useCallback(() => {
    if (status !== "connected") return;
    if (!wsSend({ type: "clear_history" })) return;
    setMessages([]);
    setIsThinking(false);
    setChatError(null);
  }, [wsSend, status]);

  useEffect(() => {
    if (activeTab === "history") {
      history.loadSessions();
    }
  }, [activeTab, history.loadSessions]);

  useEffect(() => {
    if (activeTab !== "history" || !currentSessionId || history.sessions.length === 0) {
      return;
    }
    const matched = history.sessions.find((session) => session.id === currentSessionId);
    if (matched) {
      history.loadMessages(matched.id);
    }
  }, [activeTab, currentSessionId, history.sessions, history.loadMessages]);

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-brand">
          <h1>SeeTalk</h1>
          <span className="subtitle">AI 视觉对话助手</span>
        </div>
      </header>
      <main className="app-main">
        <div className="left-column">
          <VideoPanel
            videoRef={camera.videoRef}
            effectCanvasRef={faceBlur.canvasRef}
            isActive={camera.isActive}
            isCameraEnabled={camera.isCameraEnabled}
            sessionPhase={sessionPhase}
            isMicEnabled={voice.isMicEnabled}
            isSpeaking={voice.isSpeaking}
            isListening={voice.isListening}
            transcript={voice.transcript}
            error={camera.error || voice.error}
          />
          <ControlBar
            isSessionActive={isSessionActive}
            isStartingSession={isStartingSession}
            isThinking={isThinking}
            isMicEnabled={voice.isMicEnabled}
            isCameraEnabled={camera.isCameraEnabled}
            isFaceBlurEnabled={isFaceBlurEnabled}
            canSend={canSend}
            connectionStatus={status}
            sessionStartError={sessionStartError}
            wsUrl={wsUrl}
            onStart={startSession}
            onStop={stopSession}
            onNewSession={startNewSession}
            onToggleMic={() => voice.setMicEnabled(!voice.isMicEnabled)}
            onToggleCamera={() => camera.setCameraEnabled(!camera.isCameraEnabled)}
            onToggleFaceBlur={() => setIsFaceBlurEnabled((prev) => !prev)}
            onSendText={sendMessage}
            onReconnect={reconnect}
          />
        </div>
        <div className="right-column">
          <div className="panel-tabs">
            <button
              type="button"
              className={`panel-tab${activeTab === "live" ? " panel-tab--active" : ""}`}
              onClick={() => setActiveTab("live")}
            >
              当前对话
            </button>
            <button
              type="button"
              className={`panel-tab${activeTab === "history" ? " panel-tab--active" : ""}`}
              onClick={() => setActiveTab("history")}
            >
              历史记录
            </button>
          </div>
          {activeTab === "live" ? (
            <ChatPanel
              messages={messages}
              isThinking={isThinking}
              isStreaming={isStreaming}
              chatError={chatError}
              canClear={status === "connected" && !isThinking}
              onClearHistory={clearHistory}
              onDismissError={() => setChatError(null)}
              onSendText={sendMessage}
            />
          ) : (
            <HistoryPanel
              sessions={history.sessions}
              messages={history.messages}
              selectedSessionId={history.selectedSessionId}
              loadingSessions={history.loadingSessions}
              loadingMessages={history.loadingMessages}
              error={history.error}
              onSelectSession={history.loadMessages}
              onDeleteSession={history.deleteSession}
              onContinueSession={continueHistorySession}
              onRefresh={history.loadSessions}
            />
          )}
        </div>
      </main>
    </div>
  );
}
