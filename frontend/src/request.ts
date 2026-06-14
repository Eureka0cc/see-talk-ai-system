import axios, { type AxiosRequestConfig } from "axios";

const instance = axios.create({
  timeout: 15_000,
});

// 响应拦截器：解包 axios response，直接返回 body 数据
instance.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message =
      error.response?.data?.message ??
      error.message ??
      "请求失败";
    return Promise.reject(new Error(message));
  },
);

/**
 * 包装函数：匹配 @umijs/openapi 生成代码的调用约定
 *   request<T>(url, config) → Promise<T>
 *
 * 由于 axios 拦截器已在运行时解包 response.data，
 * 这里通过类型断言让 TS 也认为返回值就是 T。
 */
function request<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance(url, config) as Promise<T>;
}

export default request;