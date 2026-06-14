import type { ConnectionStatus } from "../types";

const STATUS_LABELS: Record<ConnectionStatus, string> = {
  connected: "已连接",
  connecting: "连接中...",
  disconnected: "未连接",
  error: "连接失败",
};

interface ControlBarProps {
  isSessionActive: boolean;
  isStartingSession: boolean;
  isThinking: boolean;
  isMicEnabled: boolean;
  isCameraEnabled: boolean;
  isFaceBlurEnabled: boolean;
  canSend: boolean;
  connectionStatus: ConnectionStatus;
  sessionStartError: string | null;
  wsUrl: string;
  onStart: () => void;
  onStop: () => void;
  onNewSession: () => void;
  onToggleMic: () => void;
  onToggleCamera: () => void;
  onToggleFaceBlur: () => void;
  onSendText: (text: string) => void;
  onReconnect: () => void;
}

function CameraIcon({ muted }: { muted: boolean }) {
  if (muted) {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M4 8h4l2-2h4l2 2h4a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinejoin="round"
        />
        <circle cx="12" cy="13" r="3" stroke="currentColor" strokeWidth="1.5" />
        <path d="M4 4l16 16" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
    );
  }

  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 8h4l2-2h4l2 2h4a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="13" r="3" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}

function MicIcon({ muted }: { muted: boolean }) {
  if (muted) {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3Z"
          stroke="currentColor"
          strokeWidth="1.5"
        />
        <path
          d="M19 11v1a7 7 0 0 1-14 0v-1M12 19v3M8 22h8"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
        <path d="M4 4l16 16" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
    );
  }

  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3Z"
        stroke="currentColor"
        strokeWidth="1.5"
      />
      <path
        d="M19 11v1a7 7 0 0 1-14 0v-1M12 19v3M8 22h8"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function ControlBar({
  isSessionActive,
  isStartingSession,
  isThinking,
  isMicEnabled,
  isCameraEnabled,
  isFaceBlurEnabled,
  canSend,
  connectionStatus,
  sessionStartError,
  wsUrl,
  onStart,
  onStop,
  onNewSession,
  onToggleMic,
  onToggleCamera,
  onToggleFaceBlur,
  onSendText,
  onReconnect,
}: ControlBarProps) {
  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!canSend) return;
    const input = e.currentTarget.elements.namedItem("textInput") as HTMLInputElement;
    const text = input.value.trim();
    if (text) {
      onSendText(text);
      input.value = "";
    }
  };

  const showConnectionBanner = connectionStatus !== "connected";
  const connectionBannerText =
    connectionStatus === "connecting"
      ? `正在连接 ${wsUrl} ...`
      : connectionStatus === "error"
        ? `无法连接 ${wsUrl}，请确认后端已启动`
        : `连接已断开，目标 ${wsUrl}`;

  const inputPlaceholder = !isSessionActive
    ? "请先开始对话..."
    : connectionStatus !== "connected"
      ? "连接恢复后可发送..."
      : isThinking
        ? "等待回复..."
        : "打字输入...";

  return (
    <div className="control-bar">
      {showConnectionBanner && (
        <div className="connection-banner">
          <span className="connection-banner-text">{connectionBannerText}</span>
          {connectionStatus !== "connecting" && (
            <button type="button" className="btn btn-link" onClick={onReconnect}>
              重试
            </button>
          )}
        </div>
      )}

      {sessionStartError && (
        <div className="session-error-banner">{sessionStartError}</div>
      )}

      <div className="control-section control-section--status">
        <div className="status-row">
          <span className={`status-dot status-dot--${connectionStatus}`} />
          <span>{STATUS_LABELS[connectionStatus] ?? connectionStatus}</span>
        </div>
      </div>

      <div className="control-section control-section--primary">
        {!isSessionActive ? (
          <div className="session-controls">
            <button
              className="btn btn-primary btn-lg"
              onClick={onStart}
              disabled={isStartingSession}
            >
              {isStartingSession ? "准备中..." : "开始对话"}
            </button>
            <button
              type="button"
              className="btn btn-new-session"
              onClick={onNewSession}
              disabled={isStartingSession || isThinking}
              aria-label="新建对话"
            >
              新建对话
            </button>
          </div>
        ) : (
          <div className="session-controls">
            <button
              type="button"
              className={`btn btn-mic${isMicEnabled ? "" : " btn-mic--muted"}`}
              onClick={onToggleMic}
              aria-pressed={isMicEnabled}
              aria-label={isMicEnabled ? "关闭麦克风" : "开启麦克风"}
            >
              <MicIcon muted={!isMicEnabled} />
              <span>{isMicEnabled ? "麦克风" : "麦克风"}</span>
            </button>
            <button
              type="button"
              className={`btn btn-camera${isCameraEnabled ? "" : " btn-camera--muted"}`}
              onClick={onToggleCamera}
              aria-pressed={isCameraEnabled}
              aria-label={isCameraEnabled ? "关闭摄像头" : "开启摄像头"}
            >
              <CameraIcon muted={!isCameraEnabled} />
              <span>{isCameraEnabled ? "摄像头" : "摄像头"}</span>
            </button>
            <button type="button" className="btn btn-session-stop" onClick={onStop}>
              结束对话
            </button>
            <button
              type="button"
              className="btn btn-new-session"
              onClick={onNewSession}
              disabled={isStartingSession || isThinking}
              aria-label="新建对话"
            >
              新建对话
            </button>
            <button
              type="button"
              className={`btn btn-face-blur${isFaceBlurEnabled ? " btn-face-blur--active" : ""}`}
              onClick={onToggleFaceBlur}
              aria-pressed={isFaceBlurEnabled}
              aria-label={isFaceBlurEnabled ? "关闭人脸磨砂" : "开启人脸磨砂"}
            >
              {isFaceBlurEnabled ? "人脸模糊" : "人脸模糊"}
            </button>
          </div>
        )}
      </div>

      <div className="control-section control-section--input">
        <form className="text-input-form" onSubmit={handleSubmit}>
          <input
            name="textInput"
            type="text"
            placeholder={inputPlaceholder}
            disabled={!canSend}
            className="text-input"
          />
          <button type="submit" className="btn btn-primary btn-send" disabled={!canSend}>
            发送
          </button>
        </form>
      </div>
    </div>
  );
}
