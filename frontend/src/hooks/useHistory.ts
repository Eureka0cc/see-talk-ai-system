import { useCallback, useState } from "react";
import type { HistoryMessage, PageResponse, SessionSummary } from "../types/history";

export function useHistory() {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [messages, setMessages] = useState<HistoryMessage[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    setLoadingSessions(true);
    setError(null);
    try {
      const res = await fetch("/api/sessions");
      const contentType = res.headers.get("content-type") ?? "";
      if (!contentType.includes("application/json")) {
        throw new Error("无法加载历史记录：后端 API 未响应（请确认 backend 已启动且 /api 已正确代理）");
      }
      if (!res.ok) throw new Error(`加载会话列表失败 (${res.status})`);
      const data: PageResponse<SessionSummary> = await res.json();
      setSessions(data.content.map((session) => ({
        ...session,
        id: String(session.id),
      })));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoadingSessions(false);
    }
  }, []);

  const loadMessages = useCallback(async (sessionId: string) => {
    setLoadingMessages(true);
    setError(null);
    setSelectedSessionId(sessionId);
    try {
      const res = await fetch(`/api/sessions/${sessionId}/messages`);
      const contentType = res.headers.get("content-type") ?? "";
      if (!contentType.includes("application/json")) {
        throw new Error("无法加载消息：后端 API 未响应（请确认 backend 已启动且 /api 已正确代理）");
      }
      if (!res.ok) throw new Error(`加载消息失败 (${res.status})`);
      const data: HistoryMessage[] = await res.json();
      setMessages(data.map((message) => ({
        ...message,
        id: String(message.id),
      })));
    } catch (e) {
      setMessages([]);
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoadingMessages(false);
    }
  }, []);

  const deleteSession = useCallback(async (sessionId: string) => {
    setError(null);
    try {
      const res = await fetch(`/api/sessions/${sessionId}`, { method: "DELETE" });
      if (!res.ok && res.status !== 204) throw new Error(`删除失败 (${res.status})`);
      setSessions((prev) => prev.filter((s) => s.id !== sessionId));
      if (selectedSessionId === sessionId) {
        setSelectedSessionId(null);
        setMessages([]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }, [selectedSessionId]);

  return {
    sessions,
    messages,
    selectedSessionId,
    loadingSessions,
    loadingMessages,
    error,
    loadSessions,
    loadMessages,
    deleteSession,
  };
}
