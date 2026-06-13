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
  canSend: boolean;
  connectionStatus: ConnectionStatus;
  sessionStartError: string | null;
  wsUrl: string;
  onStart: () => void;
  onStop: () => void;
  onSendText: (text: string) => void;
  onReconnect: () => void;
}

export function ControlBar({
  isSessionActive,
  isStartingSession,
  isThinking,
  canSend,
  connectionStatus,
  sessionStartError,
  wsUrl,
  onStart,
  onStop,
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
          <button
            className="btn btn-primary btn-lg"
            onClick={onStart}
            disabled={isStartingSession}
          >
            {isStartingSession ? "准备中..." : "开始对话"}
          </button>
        ) : (
          <button className="btn btn-session-stop" onClick={onStop}>
            结束对话
          </button>
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
