import { useCallback, useEffect, useRef, useState } from "react";
import { ChatPanel } from "./components/ChatPanel";
import { ControlBar } from "./components/ControlBar";
import { HistoryPanel } from "./components/HistoryPanel";
import { VideoPanel } from "./components/VideoPanel";
import { useCamera } from "./hooks/useCamera";
import { useHistory } from "./hooks/useHistory";
import { useSpeechSynthesis } from "./hooks/useSpeechSynthesis";
import { useVoiceActivity } from "./hooks/useVoiceActivity";
import { useWebSocket } from "./hooks/useWebSocket";
import type { ChatMessage, SessionPhase, WsIncoming } from "./types";
import "./App.css";

type MainTab = "live" | "history";

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<MainTab>("live");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isThinking, setIsThinking] = useState(false);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [isStartingSession, setIsStartingSession] = useState(false);
  const [sessionStartError, setSessionStartError] = useState<string | null>(null);
  const [chatError, setChatError] = useState<string | null>(null);
  const [sessionPhase, setSessionPhase] = useState<SessionPhase>("idle");
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const history = useHistory();
  const isSessionActiveRef = useRef(false);
  const sendRef = useRef<(p: Record<string, unknown>) => boolean>(() => false);
  const sendMessageRef = useRef<(text: string) => void>(() => {});
  const captureFrameRef = useRef<() => string | null>(() => null);
  const streamingMessageIdRef = useRef<string | null>(null);
  const camera = useCamera();
  const tts = useSpeechSynthesis();
  captureFrameRef.current = camera.captureFrame;

  const sendMessage = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;

    const sent = sendRef.current({
      type: "user_message",
      text: trimmed,
      image: captureFrameRef.current(),
    });
    if (!sent) return;

    setChatError(null);
    setMessages((prev) => [
      ...prev,
      { id: uid(), role: "user", text: trimmed, timestamp: Date.now() },
    ]);
    setIsThinking(true);
  }, []);
  sendMessageRef.current = sendMessage;

  const voice = useVoiceActivity({
    onSpeechEnd: (text) => {
      if (isSessionActiveRef.current) sendMessageRef.current(text);
    },
  });

  const handleWsMessage = useCallback((data: WsIncoming) => {
    switch (data.type) {
      case "session":
        if (data.session_id != null) {
          setCurrentSessionId(String(data.session_id));
        }
        break;
      case "thinking":
        setIsThinking(true);
        break;
      case "assistant_start": {
        const messageId = data.message_id ?? uid();
        streamingMessageIdRef.current = messageId;
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
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === messageId ? { ...msg, text: msg.text + data.delta } : msg,
          ),
        );
        break;
      }
      case "assistant_done": {
        const messageId = data.message_id;
        streamingMessageIdRef.current = null;
        setIsThinking(false);
        if (messageId) {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === messageId
                ? { ...msg, text: data.text ?? msg.text, usedVision: data.used_vision }
                : msg,
            ),
          );
        }
        if (data.text) tts.speak(data.text);
        break;
      }
      case "assistant_message":
        setIsThinking(false);
        setMessages((prev) => [
          ...prev,
          {
            id: uid(),
            role: "assistant",
            text: data.text ?? "",
            usedVision: data.used_vision,
            timestamp: Date.now(),
          },
        ]);
        if (data.text) tts.speak(data.text);
        break;
      case "history_cleared":
        setMessages([]);
        setIsThinking(false);
        break;
      case "error": {
        setIsThinking(false);
        const failedStreamId = streamingMessageIdRef.current;
        streamingMessageIdRef.current = null;
        if (failedStreamId) {
          setMessages((prev) => prev.filter((msg) => msg.id !== failedStreamId));
        }
        setChatError(data.message ?? "发生错误，请重试");
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

  const startSession = useCallback(async () => {
    setIsStartingSession(true);
    setSessionStartError(null);
    setChatError(null);

    const connected = await ensureConnected();
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

  const stopSession = useCallback(() => {
    camera.stop();
    voice.stop();
    tts.stop();
    isSessionActiveRef.current = false;
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
            isActive={camera.isActive}
            sessionPhase={sessionPhase}
            isSpeaking={voice.isSpeaking}
            isListening={voice.isListening}
            transcript={voice.transcript}
            error={camera.error || voice.error}
          />
          <ControlBar
            isSessionActive={isSessionActive}
            isStartingSession={isStartingSession}
            isThinking={isThinking}
            canSend={canSend}
            connectionStatus={status}
            sessionStartError={sessionStartError}
            wsUrl={wsUrl}
            onStart={startSession}
            onStop={stopSession}
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
              chatError={chatError}
              canClear={status === "connected" && !isThinking}
              onClearHistory={clearHistory}
              onDismissError={() => setChatError(null)}
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
              onRefresh={history.loadSessions}
            />
          )}
        </div>
      </main>
    </div>
  );
}
