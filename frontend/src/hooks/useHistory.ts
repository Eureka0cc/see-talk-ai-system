import { useCallback, useState } from "react";
import type { HistoryMessage, SessionSummary } from "../types/history";
import { listSessions } from "../api/huihualishi";
import request from "../request";

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
      const res = await listSessions({});
      setSessions(
        (res.data?.content ?? []).map((dto) => ({
          id: String(dto.id ?? ""),
          title: dto.title ?? "",
          preview: dto.preview ?? "",
          lastActiveTime: dto.lastActiveTime ?? "",
          messageCount: dto.messageCount ?? 0,
          createTime: dto.createTime ?? "",
        })),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoadingSessions(false);
    }
  }, []);

  const fetchMessages = useCallback(async (sessionId: string): Promise<HistoryMessage[]> => {
    const res = await request<API.BaseResponseListMessageDto>(
      `/api/sessions/${sessionId}/messages`,
      { method: "GET" },
    );
    return (res.data ?? []).map((dto) => ({
      id: String(dto.id ?? ""),
      role: dto.role ?? "user",
      content: dto.content ?? "",
      usedVision: dto.usedVision ?? false,
      createTime: dto.createTime ?? "",
    }));
  }, []);

  const loadMessages = useCallback(async (sessionId: string) => {
    setLoadingMessages(true);
    setError(null);
    setSelectedSessionId(sessionId);
    try {
      setMessages(await fetchMessages(sessionId));
    } catch (e) {
      setMessages([]);
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoadingMessages(false);
    }
  }, [fetchMessages]);

  const deleteSession = useCallback(
    async (sessionId: string) => {
      setError(null);
      try {
        await request(`/api/sessions/${sessionId}`, { method: "DELETE" });
        setSessions((prev) => prev.filter((s) => s.id !== sessionId));
        if (selectedSessionId === sessionId) {
          setSelectedSessionId(null);
          setMessages([]);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "删除失败");
      }
    },
    [selectedSessionId],
  );

  return {
    sessions,
    messages,
    selectedSessionId,
    loadingSessions,
    loadingMessages,
    error,
    loadSessions,
    loadMessages,
    fetchMessages,
    deleteSession,
  };
}