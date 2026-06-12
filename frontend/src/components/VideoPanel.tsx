import type { RefObject } from "react";

interface VideoPanelProps {
  videoRef: RefObject<HTMLVideoElement | null>;
  isActive: boolean;
  isSpeaking?: boolean;
  isListening?: boolean;
  transcript?: string;
  error: string | null;
}

export function VideoPanel({
  videoRef,
  isActive,
  isSpeaking,
  isListening,
  transcript,
  error,
}: VideoPanelProps) {
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
        {!isActive && <div className="video-placeholder">摄像头未开启</div>}
        {isSpeaking && <div className="speaking-indicator">正在说话...</div>}
      </div>
      {isListening && transcript && (
        <div className="live-transcript">识别中：{transcript}</div>
      )}
      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
