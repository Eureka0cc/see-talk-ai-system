import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import type { SessionPhase } from "../types";

interface VideoPanelProps {
  videoRef: RefObject<HTMLVideoElement | null>;
  effectCanvasRef?: RefObject<HTMLCanvasElement | null>;
  isActive: boolean;
  isCameraEnabled?: boolean;
  sessionPhase: SessionPhase;
  isMicEnabled?: boolean;
  isSpeaking?: boolean;
  isListening?: boolean;
  transcript?: string;
  error: string | null;
}

function resolvePlaceholder(
  sessionPhase: SessionPhase,
  isActive: boolean,
  isCameraEnabled: boolean,
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
  if (sessionPhase === "active" && isActive && !isCameraEnabled) {
    return "摄像头已关闭";
  }
  if (sessionPhase === "active" && !isActive) {
    return "摄像头启动中...";
  }
  return "点击「开始对话」开启摄像头";
}

export function VideoPanel({
  videoRef,
  effectCanvasRef,
  isActive,
  isCameraEnabled = true,
  sessionPhase,
  isMicEnabled = true,
  isSpeaking,
  isListening,
  transcript,
  error,
}: VideoPanelProps) {
  const placeholder = resolvePlaceholder(sessionPhase, isActive, isCameraEnabled, error);
  const showVoiceUi = isMicEnabled !== false;
  const showVideo = isActive && isCameraEnabled;

  const [heldTranscript, setHeldTranscript] = useState("");
  const heldTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const prevListeningRef = useRef(isListening);

  useEffect(() => {
    const wasListening = prevListeningRef.current;
    prevListeningRef.current = isListening;

    if (wasListening && !isListening && transcript) {
      setHeldTranscript(transcript);
      if (heldTimerRef.current) clearTimeout(heldTimerRef.current);
      heldTimerRef.current = setTimeout(() => setHeldTranscript(""), 3000);
    }
    if (isListening && transcript) {
      setHeldTranscript("");
    }
    return () => {
      if (heldTimerRef.current) clearTimeout(heldTimerRef.current);
    };
  }, [isListening, transcript]);

  return (
    <div className="video-panel">
      <div className="video-container">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className={`video-feed ${showVideo ? "active" : ""}`}
        />
        <canvas
          ref={effectCanvasRef}
          className="effect-canvas"
          aria-hidden="true"
        />
        {!showVideo && (
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
        {showVoiceUi && isSpeaking && (
          <div className="speaking-indicator">正在说话...</div>
        )}
        {showVideo && !showVoiceUi && (
          <div className="mic-status-badge">麦克风已关闭</div>
        )}
      </div>
      {showVoiceUi && (
        <>
          {isListening && transcript && (
            <div className="live-transcript">识别中：{transcript}</div>
          )}
          {!isListening && heldTranscript && (
            <div className="live-transcript">{heldTranscript}</div>
          )}
        </>
      )}
      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
