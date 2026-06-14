export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  text: string;
  usedVision?: boolean;
  interrupted?: boolean;
  timestamp: number;
}

export type ConnectionStatus = "disconnected" | "connecting" | "connected" | "error";

export type SessionPhase = "idle" | "active" | "ended";

export interface WsIncoming {
  type: string;
  text?: string;
  delta?: string;
  message_id?: string;
  message?: string;
  session_id?: string;
  used_vision?: boolean;
}
