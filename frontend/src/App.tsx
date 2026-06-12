import { useCallback, useState } from "react";
import { ChatPanel } from "./components/ChatPanel";
import { ControlBar } from "./components/ControlBar";
import { VideoPanel } from "./components/VideoPanel";
import { useCamera } from "./hooks/useCamera";
import type { ChatMessage } from "./types";
import "./App.css";

export default function App() {
  const [messages] = useState<ChatMessage[]>([]);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const camera = useCamera();

  const startSession = useCallback(async () => {
    await camera.start();
    setIsSessionActive(true);
  }, [camera]);

  const stopSession = useCallback(() => {
    camera.stop();
    setIsSessionActive(false);
  }, [camera]);

  return (
    <div className="app">
      <header className="app-header">
        <h1>SeeTalk</h1>
        <p className="subtitle">AI 视觉对话助手</p>
      </header>
      <main className="app-main">
        <div className="left-column">
          <VideoPanel videoRef={camera.videoRef} isActive={camera.isActive} error={camera.error} />
          <ControlBar isSessionActive={isSessionActive} onStart={startSession} onStop={stopSession} />
        </div>
        <div className="right-column">
          <ChatPanel messages={messages} />
        </div>
      </main>
    </div>
  );
}
