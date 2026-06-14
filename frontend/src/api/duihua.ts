// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 创建会话 POST /api/sessions */
export async function createSession(options?: { [key: string]: any }) {
  return request<API.BaseResponseSessionCreateDto>("/api/sessions", {
    method: "POST",
    ...(options || {}),
  });
}

/** 清空热会话上下文 仅清除 Redis 中的对话上下文，不软删 MySQL 历史记录 POST /api/sessions/${param0}/clear */
export async function clearSession(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.clearSessionParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.BaseResponseVoid>(`/api/sessions/${param0}/clear`, {
    method: "POST",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 发送消息 POST /api/sessions/${param0}/messages */
export async function sendMessage(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.sendMessageParams,
  body: API.ChatRequestDto,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.BaseResponseChatResponseDto>(
    `/api/sessions/${param0}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}
