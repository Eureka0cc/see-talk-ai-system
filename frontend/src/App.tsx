import { useCallback, useRef, useState } from "react";
import { ChatPanel } from "./components/ChatPanel";
import { ControlBar } from "./components/ControlBar";
import { VideoPanel } from "./components/VideoPanel";
import { useCamera } from "./hooks/useCamera";
import { useWebSocket } from "./hooks/useWebSocket";
import type { ChatMessage, WsIncoming } from "./types";
import "./App.css";

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isThinking, setIsThinking] = useState(false);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const sendRef = useRef<(p: Record<string, unknown>) => boolean>(() => false);
  const captureFrameRef = useRef<() => string | null>(() => null);
  const camera = useCamera();
  captureFrameRef.current = camera.captureFrame;

  const sendMessage = useCallback((text: string) => {
    if (!text.trim()) return;
    setMessages((prev) => [...prev, { id: uid(), role: "user", text: text.trim(), timestamp: Date.now() }]);
    sendRef.current({ type: "user_message", text: text.trim(), image: captureFrameRef.current() });
    setIsThinking(true);
  }, []);

  const handleWsMessage = useCallback((data: WsIncoming) => {
    switch (data.type) {
      case "thinking":
        setIsThinking(true);
        break;
      case "assistant_message":
        setIsThinking(false);
        setMessages((prev) => [...prev, {
          id: uid(), role: "assistant", text: data.text ?? "",
          usedVision: data.used_vision, timestamp: Date.now(),
        }]);
        break;
      case "history_cleared":
        setMessages([]);
        setIsThinking(false);
        break;
      case "error":
        setIsThinking(false);
        setMessages((prev) => [...prev, { id: uid(), role: "system", text: data.message ?? "错误", timestamp: Date.now() }]);
        break;
    }
  }, []);

  const { status, send: wsSend } = useWebSocket(handleWsMessage);
  sendRef.current = wsSend;

  const startSession = useCallback(async () => {
    await camera.start();
    setIsSessionActive(true);
  }, [camera]);

  const stopSession = useCallback(() => {
    camera.stop();
    setIsSessionActive(false);
    setIsThinking(false);
  }, [camera]);

  const clearHistory = useCallback(() => {
    wsSend({ type: "clear_history" });
    setMessages([]);
    setIsThinking(false);
  }, [wsSend]);

  return (
    <div className="app">
      <header className="app-header">
        <h1>SeeTalk</h1>
        <p className="subtitle">AI 视觉对话助手</p>
      </header>
      <main className="app-main">
        <div className="left-column">
          <VideoPanel videoRef={camera.videoRef} isActive={camera.isActive} error={camera.error} />
          <ControlBar
            isSessionActive={isSessionActive}
            isThinking={isThinking}
            connectionStatus={status}
            onStart={startSession}
            onStop={stopSession}
            onSendText={sendMessage}
            onClearHistory={clearHistory}
          />
        </div>
        <div className="right-column">
          <ChatPanel messages={messages} isThinking={isThinking} />
        </div>
      </main>
    </div>
  );
}
