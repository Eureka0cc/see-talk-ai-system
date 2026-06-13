import { useEffect, useRef } from "react";
import type { ChatMessage } from "../types";
import { chatMessagesToDisplay, MessageList } from "./MessageList";

interface ChatPanelProps {
  messages: ChatMessage[];
  isThinking: boolean;
  chatError: string | null;
  canClear: boolean;
  onClearHistory: () => void;
  onDismissError: () => void;
}

export function ChatPanel({
  messages,
  isThinking,
  chatError,
  canClear,
  onClearHistory,
  onDismissError,
}: ChatPanelProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isThinking, chatError]);

  const displayMessages = chatMessagesToDisplay(messages);

  return (
    <div className="chat-panel">
      <div className="chat-header">
        <h2>对话记录</h2>
        <button
          type="button"
          className="btn btn-ghost-danger btn-clear-history"
          onClick={onClearHistory}
          disabled={!canClear || messages.length === 0}
          title={canClear ? "清空当前对话" : "连接恢复后可清空"}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
          清空记录
        </button>
      </div>

      {chatError && (
        <div className="chat-error-banner">
          <span>{chatError}</span>
          <button type="button" className="chat-error-dismiss" onClick={onDismissError} aria-label="关闭">
            ×
          </button>
        </div>
      )}

      <div className="chat-messages">
        {messages.length === 0 && !isThinking && (
          <div className="chat-empty">
            <div className="chat-empty-icon" aria-hidden="true">💬</div>
            <div className="chat-empty-title">开始对话吧</div>
            <div className="chat-empty-subtitle">对着摄像头说话，或打字输入你的问题</div>
          </div>
        )}
        <MessageList messages={displayMessages} />
        {isThinking && (
          <div className="chat-message chat-message--assistant">
            <div className="chat-message-meta">
              <span className="chat-message-avatar" aria-hidden="true">🤖</span>
              <span className="chat-message-role">SeeTalk</span>
            </div>
            <div className="chat-bubble chat-bubble--assistant thinking-indicator">
              思考中...
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
