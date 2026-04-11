import { useCallback, useEffect, useRef, useState } from "react";
import type { ConnectionStatus, WsIncoming } from "../types";

function resolveWsBaseUrl(): string {
  const configured = import.meta.env.VITE_WS_URL?.trim();
  if (configured) {
    return configured;
  }

  if (import.meta.env.DEV) {
    const backendHost = import.meta.env.VITE_BACKEND_HOST?.trim() || "127.0.0.1:8080";
    return `ws://${backendHost}/ws/chat`;
  }

  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/chat`;
}

const BASE_WS_URL = resolveWsBaseUrl();

interface ConnectOptions {
  sessionId?: string | null;
}

function normalizeSessionId(sessionId?: string | null): string | null {
  const trimmed = sessionId?.trim();
  return trimmed ? trimmed : null;
}

function buildWsUrl(sessionId?: string | null): string {
  const normalized = normalizeSessionId(sessionId);
  if (!normalized) return BASE_WS_URL;
  const url = new URL(BASE_WS_URL, window.location.origin);
  url.searchParams.set("sessionId", normalized);
  return url.toString();
}

const CONNECT_TIMEOUT_MS = 8000;
const BACKOFF_INITIAL_MS = 1000;
const BACKOFF_MAX_MS = 30000;

export function useWebSocket(
  onMessage: (data: WsIncoming) => void,
  onDisconnect?: () => void,
) {
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [wsUrl, setWsUrl] = useState(() => buildWsUrl());
  const wsRef = useRef<WebSocket | null>(null);
  const statusRef = useRef<ConnectionStatus>("disconnected");
  const onMessageRef = useRef(onMessage);
  const onDisconnectRef = useRef(onDisconnect);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const backoffRef = useRef(BACKOFF_INITIAL_MS);
  const intentionalCloseRef = useRef(false);
  const requestedSessionIdRef = useRef<string | null>(null);
  const wsUrlRef = useRef(buildWsUrl());
  onMessageRef.current = onMessage;
  onDisconnectRef.current = onDisconnect;

  const setConnectionStatus = useCallback((next: ConnectionStatus) => {
    statusRef.current = next;
    setStatus(next);
  }, []);

  const clearConnectTimeout = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const updateTargetUrl = useCallback((sessionId?: string | null) => {
    requestedSessionIdRef.current = normalizeSessionId(sessionId);
    const targetUrl = buildWsUrl(requestedSessionIdRef.current);
    wsUrlRef.current = targetUrl;
    setWsUrl(targetUrl);
  }, []);

  const connectInternalRef = useRef<() => void>(() => {});

  const scheduleReconnect = useCallback(() => {
    if (intentionalCloseRef.current) return;
    clearReconnectTimer();
    const delay = backoffRef.current;
    backoffRef.current = Math.min(backoffRef.current * 2, BACKOFF_MAX_MS);
    reconnectTimerRef.current = setTimeout(() => {
      connectInternalRef.current();
    }, delay);
  }, [clearReconnectTimer]);

  const connectInternal = useCallback(() => {
    const existing = wsRef.current;
    if (
      existing?.readyState === WebSocket.OPEN ||
      existing?.readyState === WebSocket.CONNECTING
    ) {
      return;
    }

    clearConnectTimeout();
    clearReconnectTimer();
    intentionalCloseRef.current = false;
    setConnectionStatus("connecting");

    if (import.meta.env.DEV) {
      console.info("[ws] connecting to", wsUrlRef.current);
    }

    const ws = new WebSocket(wsUrlRef.current);
    wsRef.current = ws;

    timeoutRef.current = setTimeout(() => {
      if (wsRef.current === ws && ws.readyState !== WebSocket.OPEN) {
        ws.close();
        setConnectionStatus("error");
      }
    }, CONNECT_TIMEOUT_MS);

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      clearConnectTimeout();
      backoffRef.current = BACKOFF_INITIAL_MS;
      setConnectionStatus("connected");
      if (import.meta.env.DEV) {
        console.info("[ws] connected");
      }
    };

    ws.onclose = (event) => {
      if (wsRef.current !== ws) return;
      clearConnectTimeout();
      wsRef.current = null;
      if (import.meta.env.DEV) {
        console.warn("[ws] closed", event.code, event.reason || "(no reason)");
      }
      if (!intentionalCloseRef.current) {
        onDisconnectRef.current?.();
        setConnectionStatus("disconnected");
        scheduleReconnect();
      } else {
        setConnectionStatus("disconnected");
      }
    };

    ws.onerror = () => {
      if (wsRef.current !== ws) return;
      setConnectionStatus("error");
      if (import.meta.env.DEV) {
        console.error("[ws] connection error", wsUrlRef.current);
      }
    };

    ws.onmessage = (ev) => {
      try {
        onMessageRef.current(JSON.parse(ev.data) as WsIncoming);
      } catch {
        /* ignore malformed payload */
      }
    };
  }, [clearConnectTimeout, clearReconnectTimer, scheduleReconnect, setConnectionStatus]);

  connectInternalRef.current = connectInternal;

  const connect = useCallback((options?: ConnectOptions) => {
    if (options) {
      updateTargetUrl(options.sessionId);
    }
    backoffRef.current = BACKOFF_INITIAL_MS;
    connectInternal();
  }, [connectInternal, updateTargetUrl]);

  const reconnect = useCallback((options?: ConnectOptions) => {
    if (options) {
      updateTargetUrl(options.sessionId);
    }
    intentionalCloseRef.current = false;
    backoffRef.current = BACKOFF_INITIAL_MS;
    clearReconnectTimer();
    wsRef.current?.close();
    wsRef.current = null;
    connectInternal();
  }, [clearReconnectTimer, connectInternal, updateTargetUrl]);

  const disconnect = useCallback(() => {
    intentionalCloseRef.current = true;
    clearConnectTimeout();
    clearReconnectTimer();
    wsRef.current?.close();
    wsRef.current = null;
    setConnectionStatus("disconnected");
  }, [clearConnectTimeout, clearReconnectTimer, setConnectionStatus]);

  const ensureConnected = useCallback(
    (timeoutMs = CONNECT_TIMEOUT_MS, options?: ConnectOptions): Promise<boolean> => {
      const requestedSessionId = normalizeSessionId(options?.sessionId);
      const targetUrl = buildWsUrl(requestedSessionId);
      const socketReady = wsRef.current?.readyState === WebSocket.OPEN;
      if (requestedSessionIdRef.current !== requestedSessionId) {
        reconnect({ sessionId: requestedSessionId });
      } else if (socketReady && wsUrlRef.current === targetUrl) {
        return Promise.resolve(true);
      } else {
        connectInternal();
      }

      return new Promise((resolve) => {
        const deadline = Date.now() + timeoutMs;
        const timer = setInterval(() => {
          if (wsRef.current?.readyState === WebSocket.OPEN && wsUrlRef.current === targetUrl) {
            clearInterval(timer);
            resolve(true);
            return;
          }
          if (Date.now() >= deadline) {
            clearInterval(timer);
            resolve(false);
          }
        }, 100);
      });
    },
    [connectInternal, reconnect],
  );

  const send = useCallback((payload: Record<string, unknown>) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }, []);

  useEffect(() => {
    connectInternal();
    return () => disconnect();
  }, [connectInternal, disconnect]);

  return { status, connect, reconnect, disconnect, send, ensureConnected, wsUrl };
}
