import type { ChatMessage } from "../types";

interface ChatPanelProps {
  messages: ChatMessage[];
  isThinking: boolean;
}

export function ChatPanel({ messages, isThinking }: ChatPanelProps) {
  return (
    <div className="chat-panel">
      <div className="chat-header"><h2>对话记录</h2></div>
      <div className="chat-messages">
        {messages.length === 0 && (
          <div className="chat-empty">对着摄像头说话，AI 会看到你并回应你</div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`chat-bubble chat-bubble--${msg.role}`}>
            <div className="chat-bubble-role">
              {msg.role === "user" ? "你" : msg.role === "assistant" ? "SeeTalk" : "系统"}
              {msg.usedVision && <span className="vision-badge">视觉</span>}
            </div>
            <div className="chat-bubble-text">{msg.text}</div>
          </div>
        ))}
        {isThinking && <div className="chat-bubble chat-bubble--assistant">思考中...</div>}
      </div>
    </div>
  );
}
