export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  text: string;
  usedVision?: boolean;
  timestamp: number;
}

export type ConnectionStatus = "disconnected" | "connecting" | "connected" | "error";

export interface WsIncoming {
  type: string;
  text?: string;
  message?: string;
  session_id?: string;
  used_vision?: boolean;
}
