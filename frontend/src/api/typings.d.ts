declare namespace API {
  type BaseResponseChatResponseDto = {
    code?: number;
    data?: ChatResponseDto;
    message?: string;
  };

  type BaseResponseListMessageDto = {
    code?: number;
    data?: MessageDto[];
    message?: string;
  };

  type BaseResponseMapStringObject = {
    code?: number;
    data?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageResponseSessionSummaryDto = {
    code?: number;
    data?: PageResponseSessionSummaryDto;
    message?: string;
  };

  type BaseResponseSessionCreateDto = {
    code?: number;
    data?: SessionCreateDto;
    message?: string;
  };

  type BaseResponseVoid = {
    code?: number;
    data?: Record<string, any>;
    message?: string;
  };

  type ChatRequestDto = {
    /** 用户文本消息 */
    text?: string;
    /** Base64 编码的 JPEG/PNG 图像（可选） */
    image?: string;
  };

  type ChatResponseDto = {
    text?: string;
    usedVision?: boolean;
    sessionId?: number;
  };

  type clearSessionParams = {
    /** 会话 ID */
    id: number;
  };

  type deleteSessionParams = {
    /** 会话 ID */
    id: number;
  };

  type listMessagesParams = {
    /** 会话 ID */
    id: number;
  };

  type listSessionsParams = {
    /** 页码，从 0 开始 */
    page?: number;
    /** 每页条数，最大 10000 */
    size?: number;
  };

  type MessageDto = {
    id?: number;
    role?: string;
    content?: string;
    usedVision?: boolean;
    createTime?: string;
  };

  type PageResponseSessionSummaryDto = {
    content?: SessionSummaryDto[];
    page?: number;
    size?: number;
    totalElements?: number;
    totalPages?: number;
  };

  type sendMessageParams = {
    /** 会话 ID */
    id: number;
  };

  type SessionCreateDto = {
    id?: number;
  };

  type SessionSummaryDto = {
    id?: number;
    title?: string;
    preview?: string;
    lastActiveTime?: string;
    messageCount?: number;
    createTime?: string;
  };
}
