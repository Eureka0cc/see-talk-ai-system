// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 健康检查 GET /health */
export async function health(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringObject>("/health", {
    method: "GET",
    ...(options || {}),
  });
}
