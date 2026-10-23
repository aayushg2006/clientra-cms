import { useSyncExternalStore } from "react";
import axios from "axios";
import type { AuthResponse, Role, User } from "./types";

// Tokens live in localStorage when "Keep me signed in" is ticked, otherwise in sessionStorage.
const KEY = "clientra.auth";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  persistent: boolean;
}

const empty: AuthState = { accessToken: null, refreshToken: null, user: null, persistent: false };

function read(): AuthState {
  try {
    const raw = localStorage.getItem(KEY) ?? sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as AuthState) : empty;
  } catch {
    return empty;
  }
}

let state: AuthState = read();
const listeners = new Set<() => void>();

function write(next: AuthState) {
  state = next;
  try {
    localStorage.removeItem(KEY);
    sessionStorage.removeItem(KEY);
    if (next.accessToken) {
      (next.persistent ? localStorage : sessionStorage).setItem(KEY, JSON.stringify(next));
    }
  } catch {
    /* storage unavailable: keep in memory only */
  }
  listeners.forEach((l) => l());
}

let refreshing: Promise<string> | null = null;

export const authStore = {
  getAccessToken: () => state.accessToken,
  getUser: () => state.user,

  signIn(res: AuthResponse, persistent: boolean) {
    write({ accessToken: res.accessToken, refreshToken: res.refreshToken, user: res.user, persistent });
  },

  setUser(user: User) {
    write({ ...state, user });
  },

  signOut() {
    write(empty);
  },

  /** POST /api/auth/refresh; concurrent 401s share one refresh call. */
  refresh(): Promise<string> {
    if (!state.refreshToken) return Promise.reject(new Error("No refresh token"));
    refreshing ??= axios
      .post<AuthResponse>(`${import.meta.env.VITE_API_URL}/api/auth/refresh`, { refreshToken: state.refreshToken })
      .then((r) => {
        write({ ...state, accessToken: r.data.accessToken, refreshToken: r.data.refreshToken, user: r.data.user });
        return r.data.accessToken;
      })
      .finally(() => {
        refreshing = null;
      });
    return refreshing;
  },

  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useAuth() {
  const snapshot = useSyncExternalStore(authStore.subscribe, () => state);
  const role = snapshot.user?.role;
  return {
    user: snapshot.user,
    isAuthenticated: !!snapshot.accessToken,
    role,
    hasRole: (...roles: Role[]) => !!role && roles.includes(role),
  };
}

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Admin",
  SALES_EXEC: "Sales Executive",
  SUPPORT_AGENT: "Support Agent",
};

export function homeFor(role?: Role) {
  return role === "SUPPORT_AGENT" ? "/tickets" : "/dashboard";
}
