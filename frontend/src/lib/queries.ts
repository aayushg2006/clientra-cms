import { useQuery } from "@tanstack/react-query";
import api from "./api";
import type { Product, Role, TagDto, User } from "./types";

/** Shared reference data used by forms and filters. */

export function useUsers(role?: Role) {
  return useQuery({
    queryKey: ["users", role ?? "all"],
    queryFn: () => api.get<User[]>("/api/users", { params: role ? { role } : {} }).then((r) => r.data),
    staleTime: 5 * 60_000,
  });
}

export function useTags() {
  return useQuery({
    queryKey: ["tags"],
    queryFn: () => api.get<TagDto[]>("/api/tags").then((r) => r.data),
    staleTime: 5 * 60_000,
  });
}

export function useActiveProducts() {
  return useQuery({
    queryKey: ["products", "active"],
    queryFn: () => api.get<Product[]>("/api/products", { params: { activeOnly: true } }).then((r) => r.data),
    staleTime: 60_000,
  });
}

/** "Neha Sharma" -> "Neha S." as shown in the owner column. */
export const shortName = (name?: string | null) => {
  if (!name) return "—";
  const parts = name.trim().split(/\s+/);
  return parts.length > 1 ? `${parts[0]} ${parts[parts.length - 1][0]}.` : parts[0];
};
