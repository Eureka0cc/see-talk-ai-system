interface ControlBarProps {
  isSessionActive: boolean;
  isThinking: boolean;
  connectionStatus: string;
  onStart: () => void;
  onStop: () => void;
  onSendText: (text: string) => void;
  onClearHistory: () => void;
}

export function ControlBar({
  isSessionActive,
  isThinking,
  connectionStatus,
  onStart,
  onStop,
  onSendText,
  onClearHistory,
}: ControlBarProps) {
  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const input = e.currentTarget.elements.namedItem("textInput") as HTMLInputElement;
    const text = input.value.trim();
    if (text) {
      onSendText(text);
      input.value = "";
    }
  };

  return (
    <div className="control-bar">
      <div className="status-row">
        <span className={`status-dot status-dot--${connectionStatus}`} />
        <span>{connectionStatus === "connected" ? "已连接" : connectionStatus}</span>
      </div>
      {!isSessionActive ? (
        <button className="btn btn-primary btn-lg" onClick={onStart}>开始对话</button>
      ) : (
        <button className="btn btn-danger" onClick={onStop}>结束对话</button>
      )}
      <button className="btn btn-secondary" onClick={onClearHistory} disabled={isThinking}>
        清空记录
      </button>
      <form className="text-input-form" onSubmit={handleSubmit}>
        <input
          name="textInput"
          type="text"
          placeholder="打字输入..."
          disabled={!isSessionActive || isThinking}
          className="text-input"
        />
        <button type="submit" className="btn btn-primary" disabled={!isSessionActive || isThinking}>
          发送
        </button>
      </form>
    </div>
  );
}
