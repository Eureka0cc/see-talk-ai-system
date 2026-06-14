interface ControlBarProps {
  isSessionActive: boolean;
  onStart: () => void;
  onStop: () => void;
}

export function ControlBar({ isSessionActive, onStart, onStop }: ControlBarProps) {
  return (
    <div className="control-bar">
      {!isSessionActive ? (
        <button className="btn btn-primary btn-lg" onClick={onStart}>开始对话</button>
      ) : (
        <button className="btn btn-danger" onClick={onStop}>结束对话</button>
      )}
    </div>
  );
}
