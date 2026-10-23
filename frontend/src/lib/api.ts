import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { authStore } from "./auth";
import type { ApiError } from "./types";

export const API_URL = import.meta.env.VITE_API_URL as string;

const api = axios.create({ baseURL: API_URL });

api.interceptors.request.use((cfg) => {
  const token = authStore.getAccessToken();
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

// Silent refresh: on 401, get a new access token once and retry the original request.
api.interceptors.response.use(
  (r) => r,
  async (err: AxiosError) => {
    const cfg = err.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthCall = cfg?.url?.includes("/api/auth/");
    if (err.response?.status === 401 && cfg && !cfg._retry && !isAuthCall) {
      cfg._retry = true;
      try {
        const token = await authStore.refresh();
        cfg.headers.Authorization = `Bearer ${token}`;
        return api(cfg);
      } catch {
        authStore.signOut();
        window.location.assign("/login?expired=1");
      }
    }
    return Promise.reject(err);
  },
);

export default api;

/** Extracts the uniform JSON error body from a failed request. */
export function apiError(err: unknown): ApiError {
  if (axios.isAxiosError(err) && err.response?.data && typeof err.response.data === "object") {
    return err.response.data as ApiError;
  }
  if (axios.isAxiosError(err) && !err.response) {
    return { status: 0, error: "NETWORK", message: "Cannot reach the server. Is the API running?" };
  }
  return { status: 500, error: "UNKNOWN", message: "Something went wrong" };
}

/** Downloads a binary endpoint (CSV / PDF) with the auth header and saves it. */
export async function download(url: string, params: Record<string, unknown> = {}, fallbackName = "export") {
  const res = await api.get(url, { params, responseType: "blob" });
  const disposition = res.headers["content-disposition"] as string | undefined;
  const match = disposition?.match(/filename="?([^"]+)"?/);
  const name = match?.[1] ?? fallbackName;
  const href = URL.createObjectURL(res.data as Blob);
  const a = document.createElement("a");
  a.href = href;
  a.download = name;
  a.click();
  URL.revokeObjectURL(href);
}
