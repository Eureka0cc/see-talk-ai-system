import type { HistoryMessage, SessionSummary } from "../types/history";
import { formatHistoryDate } from "../utils/timeFormat";
import { historyMessagesToDisplay, MessageList } from "./MessageList";

interface HistoryPanelProps {
  sessions: SessionSummary[];
  messages: HistoryMessage[];
  selectedSessionId: string | null;
  loadingSessions: boolean;
  loadingMessages: boolean;
  error: string | null;
  onSelectSession: (id: string) => void;
  onDeleteSession: (id: string) => void;
  onRefresh: () => void;
}

function SessionIcon() {
  return (
    <span className="history-item-icon" aria-hidden="true">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
        <path
          d="M3 4.5A1.5 1.5 0 0 1 4.5 3h7A1.5 1.5 0 0 1 13 4.5v7A1.5 1.5 0 0 1 11.5 13h-7A1.5 1.5 0 0 1 3 11.5v-7Z"
          stroke="currentColor"
          strokeWidth="1.2"
        />
        <path d="M5.5 6h5M5.5 8.5h3.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      </svg>
    </span>
  );
}

export function HistoryPanel({
  sessions,
  messages,
  selectedSessionId,
  loadingSessions,
  loadingMessages,
  error,
  onSelectSession,
  onDeleteSession,
  onRefresh,
}: HistoryPanelProps) {
  const selectedSession = sessions.find((s) => s.id === selectedSessionId);
  const displayMessages = historyMessagesToDisplay(messages);

  const handleDelete = (session: SessionSummary) => {
    if (window.confirm(`确定删除「${session.title}」吗？`)) {
      onDeleteSession(session.id);
    }
  };

  return (
    <div className="history-panel">
      <div className="history-header">
        <div className="history-header-info">
          <h2>历史记录</h2>
          {sessions.length > 0 && (
            <span className="history-count">{sessions.length} 个会话</span>
          )}
        </div>
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={onRefresh}
          disabled={loadingSessions}
        >
          {loadingSessions ? "刷新中…" : "刷新"}
        </button>
      </div>

      {error && <div className="history-error">{error}</div>}

      <div className="history-body">
        <div className="history-list">
          {loadingSessions && sessions.length === 0 && (
            <div className="history-placeholder">
              <span className="history-placeholder-icon">⏳</span>
              <span>加载中…</span>
            </div>
          )}
          {!loadingSessions && sessions.length === 0 && (
            <div className="history-placeholder">
              <span className="history-placeholder-icon">💬</span>
              <span>暂无历史会话</span>
              <span className="history-placeholder-hint">开始对话后，记录会出现在这里</span>
            </div>
          )}
          {sessions.map((session) => (
            <div
              key={session.id}
              className={`history-item${selectedSessionId === session.id ? " history-item--active" : ""}`}
            >
              <button
                type="button"
                className="history-item-main"
                onClick={() => onSelectSession(session.id)}
              >
                <div className="history-item-top">
                  <SessionIcon />
                  <div
                    className="history-item-title"
                    title={session.title || "新对话"}
                  >
                    {session.title || "新对话"}
                  </div>
                </div>
                <div className="history-item-meta">
                  <span>{formatHistoryDate(session.lastActiveTime)}</span>
                  <span className="history-item-dot">·</span>
                  <span>{session.messageCount} 条消息</span>
                </div>
              </button>
              <button
                type="button"
                className="history-item-delete"
                title="删除此会话"
                aria-label={`删除会话 ${session.title}`}
                onClick={() => handleDelete(session)}
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                  <path
                    d="M3 4h8M5.5 4V3a1 1 0 0 1 1-1h1a1 1 0 0 1 1 1v1M6 6.5v3M8 6.5v3M4.5 4l.5 7.5a1 1 0 0 0 1 .9h2a1 1 0 0 0 1-.9L9.5 4"
                    stroke="currentColor"
                    strokeWidth="1.1"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            </div>
          ))}
        </div>

        <div className="history-detail">
          {!selectedSessionId && (
            <div className="history-placeholder">
              <span className="history-placeholder-icon">👈</span>
              <span>选择左侧会话查看完整对话</span>
            </div>
          )}
          {selectedSessionId && loadingMessages && (
            <div className="history-placeholder">
              <span className="history-placeholder-icon">⏳</span>
              <span>加载消息中…</span>
            </div>
          )}
          {selectedSessionId && !loadingMessages && messages.length === 0 && (
            <div className="history-placeholder">
              <span className="history-placeholder-icon">📭</span>
              <span>该会话暂无消息</span>
            </div>
          )}
          {selectedSession && !loadingMessages && messages.length > 0 && (
            <>
              <div className="history-detail-header">
                <div className="history-detail-title">{selectedSession.title}</div>
                <div className="history-detail-meta">
                  <span>{formatHistoryDate(selectedSession.lastActiveTime)}</span>
                  <span className="history-item-dot">·</span>
                  <span>{selectedSession.messageCount} 条消息</span>
                </div>
              </div>
              <div className="history-messages">
                <MessageList messages={displayMessages} />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
