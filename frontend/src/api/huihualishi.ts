// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 查询全部会话列表 GET /api/sessions */
export async function listSessions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listSessionsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageResponseSessionSummaryDto>(
    "/api/sessions",
    {
      method: "GET",
      params: {
        // size has a default value: 10000
        size: "10000",
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 删除会话（软删除） DELETE /api/sessions/${param0} */
export async function deleteSession(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteSessionParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<any>(`/api/sessions/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 查询会话消息 GET /api/sessions/${param0}/messages */
export async function listMessages(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listMessagesParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.BaseResponseListMessageDto>(
    `/api/sessions/${param0}/messages`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
