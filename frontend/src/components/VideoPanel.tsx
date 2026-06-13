import type { RefObject } from "react";
import type { SessionPhase } from "../types";

interface VideoPanelProps {
  videoRef: RefObject<HTMLVideoElement | null>;
  isActive: boolean;
  sessionPhase: SessionPhase;
  isSpeaking?: boolean;
  isListening?: boolean;
  transcript?: string;
  error: string | null;
}

function resolvePlaceholder(
  sessionPhase: SessionPhase,
  isActive: boolean,
  error: string | null,
): string {
  if (sessionPhase === "ended") {
    return "对话已结束";
  }
  if (error) {
    const lower = error.toLowerCase();
    if (lower.includes("permission") || lower.includes("notallowed") || lower.includes("denied")) {
      return "摄像头权限被拒绝";
    }
    return "摄像头不可用";
  }
  if (sessionPhase === "active" && !isActive) {
    return "摄像头启动中...";
  }
  return "点击「开始对话」开启摄像头";
}

export function VideoPanel({
  videoRef,
  isActive,
  sessionPhase,
  isSpeaking,
  isListening,
  transcript,
  error,
}: VideoPanelProps) {
  const placeholder = resolvePlaceholder(sessionPhase, isActive, error);

  return (
    <div className="video-panel">
      <div className="video-container">
        <video
          ref={videoRef as React.RefObject<HTMLVideoElement>}
          autoPlay
          playsInline
          muted
          className={`video-feed ${isActive ? "active" : ""}`}
        />
        {!isActive && (
          <div className="video-placeholder">
            <span className="video-placeholder-icon" aria-hidden="true">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
                <rect x="3" y="6" width="13" height="12" rx="2" stroke="currentColor" strokeWidth="1.5" />
                <path d="M16 10l5-3v10l-5-3" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
              </svg>
            </span>
            <span className="video-placeholder-text">{placeholder}</span>
          </div>
        )}
        {isSpeaking && <div className="speaking-indicator">正在说话...</div>}
      </div>
      {isListening && transcript && (
        <div className="live-transcript">识别中：{transcript}</div>
      )}
      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
