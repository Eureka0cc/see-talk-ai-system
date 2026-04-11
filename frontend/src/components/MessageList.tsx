import type { ReactNode } from "react";
import type { ChatMessage } from "../types";
import type { HistoryMessage } from "../types/history";
import { formatDividerTime, shouldShowTimeDivider } from "../utils/timeFormat";

export interface DisplayMessage {
  id: string;
  role: "user" | "assistant" | "system" | string;
  text: string;
  usedVision?: boolean;
  interrupted?: boolean;
  timestamp: number | string;
}

function TimeDivider({ time }: { time: number | string }) {
  return (
    <div className="time-divider">
      <span>{formatDividerTime(time)}</span>
    </div>
  );
}

function renderInlineMarkdown(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const tokenRegex = /`([^`]+)`|\*\*([^*]+)\*\*|__(.+?)__|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g;
  let lastIndex = 0;
  let key = 0;
  let match: RegExpExecArray | null;

  while ((match = tokenRegex.exec(text)) != null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    if (match[1]) {
      nodes.push(<code key={`code-${key++}`}>{match[1]}</code>);
    } else if (match[2]) {
      nodes.push(<strong key={`strong-${key++}`}>{match[2]}</strong>);
    } else if (match[3]) {
      nodes.push(<strong key={`strong2-${key++}`}>{match[3]}</strong>);
    } else if (match[4] && match[5]) {
      nodes.push(
        <a key={`link-${key++}`} href={match[5]} target="_blank" rel="noreferrer">
          {match[4]}
        </a>,
      );
    }
    lastIndex = tokenRegex.lastIndex;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }

  return nodes;
}

function renderAssistantMarkdown(text: string): ReactNode {
  const codeFenceRegex = /```(\w+)?\n([\s\S]*?)```/g;
  const pieces: ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;
  let fenceMatch: RegExpExecArray | null;

  const pushParagraphs = (value: string) => {
    const blocks = value.split(/\n{2,}/).filter((block) => block.trim().length > 0);
    blocks.forEach((block) => {
      const lines = block.split("\n");
      if (lines.every((line) => /^[-*]\s+/.test(line.trim()))) {
        pieces.push(
          <ul key={`ul-${key++}`}>
            {lines.map((line, idx) => (
              <li key={`li-${key}-${idx}`}>{renderInlineMarkdown(line.replace(/^[-*]\s+/, ""))}</li>
            ))}
          </ul>,
        );
        return;
      }

      if (lines.every((line) => /^\d+\.\s+/.test(line.trim()))) {
        pieces.push(
          <ol key={`ol-${key++}`}>
            {lines.map((line, idx) => (
              <li key={`oli-${key}-${idx}`}>{renderInlineMarkdown(line.replace(/^\d+\.\s+/, ""))}</li>
            ))}
          </ol>,
        );
        return;
      }

      if (/^#{1,3}\s+/.test(block.trim())) {
        const heading = block.trim();
        const level = Math.min(3, (heading.match(/^#+/)?.[0].length ?? 1));
        const content = heading.replace(/^#{1,3}\s+/, "");
        const HeadingTag = level === 1 ? "h1" : level === 2 ? "h2" : "h3";
        pieces.push(<HeadingTag key={`h-${key++}`}>{renderInlineMarkdown(content)}</HeadingTag>);
        return;
      }

      pieces.push(
        <p key={`p-${key++}`}>
          {lines.map((line, idx) => (
            <span key={`line-${key}-${idx}`}>
              {renderInlineMarkdown(line)}
              {idx < lines.length - 1 && <br />}
            </span>
          ))}
        </p>,
      );
    });
  };

  while ((fenceMatch = codeFenceRegex.exec(text)) != null) {
    const prefix = text.slice(lastIndex, fenceMatch.index);
    if (prefix) pushParagraphs(prefix);
    pieces.push(
      <pre key={`pre-${key++}`} className="chat-markdown-pre">
        <code>{fenceMatch[2]}</code>
      </pre>,
    );
    lastIndex = codeFenceRegex.lastIndex;
  }

  if (lastIndex < text.length) {
    pushParagraphs(text.slice(lastIndex));
  }

  if (pieces.length === 0) {
    return text;
  }
  return pieces;
}

function MessageItem({
  msg,
  isStreaming,
  showStreamingPlaceholder,
}: {
  msg: DisplayMessage;
  isStreaming: boolean;
  showStreamingPlaceholder: boolean;
}) {
  const isUser = msg.role === "user";
  const isAssistant = msg.role === "assistant";
  const roleLabel = isUser ? "你" : isAssistant ? "SeeTalk" : "系统";
  const shouldRenderMarkdown = isAssistant && !isStreaming;
  const isEmptyStreamingAssistant = isAssistant && showStreamingPlaceholder;

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
        <div className={`chat-bubble-text${isStreaming ? " chat-bubble-text--streaming" : ""}`}>
          {shouldRenderMarkdown ? renderAssistantMarkdown(msg.text) : msg.text}
          {isEmptyStreamingAssistant && (
            <span className="streaming-loader" aria-label="AI 正在生成">
              <span className="streaming-loader-dot" />
              <span className="streaming-loader-dot" />
              <span className="streaming-loader-dot" />
            </span>
          )}
          {msg.role === "assistant" && msg.interrupted && (
            <span className="interrupted-badge">（被打断）</span>
          )}
        </div>
      </div>
    </div>
  );
}

interface MessageListProps {
  messages: DisplayMessage[];
  isStreaming?: boolean;
}

export function MessageList({ messages, isStreaming = false }: MessageListProps) {
  let previousTimestamp: number | string | null = null;
  const lastAssistantMessageId = [...messages]
    .reverse()
    .find((msg) => msg.role === "assistant")?.id;

  return (
    <>
      {messages.map((msg) => {
        const showDivider = shouldShowTimeDivider(previousTimestamp, msg.timestamp);
        previousTimestamp = msg.timestamp;
        return (
          <div key={msg.id}>
            {showDivider && <TimeDivider time={msg.timestamp} />}
            <MessageItem
              msg={msg}
              isStreaming={Boolean(isStreaming && msg.id === lastAssistantMessageId)}
              showStreamingPlaceholder={Boolean(
                isStreaming &&
                msg.id === lastAssistantMessageId &&
                msg.role === "assistant" &&
                msg.text.trim().length === 0,
              )}
            />
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
      interrupted: msg.interrupted,
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
