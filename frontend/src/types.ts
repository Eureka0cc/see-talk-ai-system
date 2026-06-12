export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  text: string;
  usedVision?: boolean;
  timestamp: number;
}
