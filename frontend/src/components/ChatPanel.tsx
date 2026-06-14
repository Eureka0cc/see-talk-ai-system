import type { ChatMessage } from "../types";

interface ChatPanelProps {
  messages: ChatMessage[];
}

export function ChatPanel({ messages }: ChatPanelProps) {
  return (
    <div className="chat-panel">
      <div className="chat-header"><h2>对话记录</h2></div>
      <div className="chat-messages">
        {messages.length === 0 && (
          <div className="chat-empty">对着摄像头说话，AI 会看到你并回应你</div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`chat-bubble chat-bubble--${msg.role}`}>
            <div className="chat-bubble-text">{msg.text}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
