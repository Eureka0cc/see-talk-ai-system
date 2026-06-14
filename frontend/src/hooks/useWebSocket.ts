import { useCallback, useEffect, useRef, useState } from "react";
import type { ConnectionStatus, WsIncoming } from "../types";

const WS_URL =
  import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws/chat`;

export function useWebSocket(onMessage: (data: WsIncoming) => void) {
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const wsRef = useRef<WebSocket | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    setStatus("connecting");
    const ws = new WebSocket(WS_URL);
    wsRef.current = ws;
    ws.onopen = () => setStatus("connected");
    ws.onclose = () => { setStatus("disconnected"); wsRef.current = null; };
    ws.onerror = () => setStatus("error");
    ws.onmessage = (ev) => {
      try { onMessageRef.current(JSON.parse(ev.data) as WsIncoming); } catch { /* ignore */ }
    };
  }, []);

  const disconnect = useCallback(() => {
    wsRef.current?.close();
    wsRef.current = null;
    setStatus("disconnected");
  }, []);

  const send = useCallback((payload: Record<string, unknown>) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }, []);

  useEffect(() => { connect(); return () => disconnect(); }, [connect, disconnect]);

  return { status, connect, disconnect, send };
}
