export interface SessionSummary {
  id: string;
  title: string;
  preview: string;
  lastActiveTime: string;
  messageCount: number;
  createTime: string;
}

export interface HistoryMessage {
  id: string;
  role: "user" | "assistant" | string;
  content: string;
  usedVision: boolean;
  createTime: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
