import type { RefObject } from "react";

interface VideoPanelProps {
  videoRef: RefObject<HTMLVideoElement | null>;
  isActive: boolean;
  error: string | null;
}

export function VideoPanel({ videoRef, isActive, error }: VideoPanelProps) {
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
      </div>
      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
