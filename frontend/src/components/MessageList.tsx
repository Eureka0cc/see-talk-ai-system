import type { ChatMessage } from "../types";
import type { HistoryMessage } from "../types/history";
import { formatDividerTime, shouldShowTimeDivider } from "../utils/timeFormat";

export interface DisplayMessage {
  id: string;
  role: "user" | "assistant" | "system" | string;
  text: string;
  usedVision?: boolean;
  timestamp: number | string;
}

function TimeDivider({ time }: { time: number | string }) {
  return (
    <div className="time-divider">
      <span>{formatDividerTime(time)}</span>
    </div>
  );
}

function MessageItem({ msg }: { msg: DisplayMessage }) {
  const isUser = msg.role === "user";
  const isAssistant = msg.role === "assistant";
  const roleLabel = isUser ? "你" : isAssistant ? "SeeTalk" : "系统";

  return (
    <div className={`chat-message chat-message--${msg.role}`}>
      <div className="chat-message-meta">
        <span className="chat-message-avatar" aria-hidden="true">
          {isUser ? "👤" : isAssistant ? "🤖" : "ℹ️"}
        </span>
        <span className="chat-message-role">{roleLabel}</span>
        {msg.usedVision && <span className="vision-badge">视觉</span>}
      </div>
      <div className={`chat-bubble chat-bubble--${msg.role}`}>
        <div className="chat-bubble-text">{msg.text}</div>
      </div>
    </div>
  );
}

interface MessageListProps {
  messages: DisplayMessage[];
}

export function MessageList({ messages }: MessageListProps) {
  let previousTimestamp: number | string | null = null;

  return (
    <>
      {messages.map((msg) => {
        const showDivider = shouldShowTimeDivider(previousTimestamp, msg.timestamp);
        previousTimestamp = msg.timestamp;
        return (
          <div key={msg.id}>
            {showDivider && <TimeDivider time={msg.timestamp} />}
            <MessageItem msg={msg} />
          </div>
        );
      })}
    </>
  );
}

export function chatMessagesToDisplay(messages: ChatMessage[]): DisplayMessage[] {
  return messages
    .filter((msg) => msg.role !== "system")
    .map((msg) => ({
      id: msg.id,
      role: msg.role,
      text: msg.text,
      usedVision: msg.usedVision,
      timestamp: msg.timestamp,
    }));
}

export function historyMessagesToDisplay(messages: HistoryMessage[]): DisplayMessage[] {
  return messages.map((msg) => ({
    id: msg.id,
    role: msg.role,
    text: msg.content,
    usedVision: msg.usedVision,
    timestamp: msg.createTime,
  }));
}
