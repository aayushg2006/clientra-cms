import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import type { NotificationItem, NotificationType, Page } from "@/lib/types";
import { relativeDay, time } from "@/lib/format";

const ICON: Record<NotificationType, string> = {
  FOLLOW_UP: "📞",
  SLA_BREACH: "⏱️",
  TICKET_ASSIGNED: "🎫",
  TICKET_UPDATE: "💬",
  ORDER_PAID: "💰",
  CHURN_RISK: "⚠️",
  SYSTEM: "ℹ️",
};

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const qc = useQueryClient();

  const unread = useQuery({
    queryKey: ["notifications", "unread"],
    queryFn: () => api.get<{ count: number }>("/api/notifications/unread-count").then((r) => r.data.count),
    refetchInterval: 30_000,
  });
  const list = useQuery({
    queryKey: ["notifications", "list"],
    queryFn: () => api.get<Page<NotificationItem>>("/api/notifications", { params: { size: 12 } }).then((r) => r.data.content),
    enabled: open,
  });
  const markAll = useMutation({
    mutationFn: () => api.post("/api/notifications/read-all"),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });
  const markOne = useMutation({
    mutationFn: (id: number) => api.patch(`/api/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  useEffect(() => {
    const h = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen((o) => !o)} className="relative rounded-lg p-1.5 text-xl leading-none hover:bg-sand-200" aria-label="Notifications">
        🔔
        {!!unread.data && (
          <span className="absolute -right-0.5 -top-0.5 flex h-[17px] min-w-[17px] items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread.data > 9 ? "9+" : unread.data}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-11 z-30 w-[360px] overflow-hidden rounded-2xl border border-sand-300 bg-white shadow-pop">
          <div className="flex items-center justify-between border-b border-sand-200 px-4 py-3">
            <p className="font-semibold">Notifications</p>
            <button className="text-xs font-semibold text-teal-700 hover:underline disabled:opacity-50" disabled={!unread.data} onClick={() => markAll.mutate()}>
              Mark all read
            </button>
          </div>
          <div className="max-h-[380px] overflow-y-auto">
            {list.isLoading && <p className="px-4 py-6 text-center text-sm text-ink-500">Loading…</p>}
            {list.data?.length === 0 && <p className="px-4 py-8 text-center text-sm text-ink-500">You're all caught up 🎉</p>}
            {list.data?.map((n) => (
              <button
                key={n.id}
                onClick={() => {
                  if (!n.read) markOne.mutate(n.id);
                  setOpen(false);
                  if (n.link) navigate(n.link);
                }}
                className={clsx("flex w-full gap-3 border-b border-sand-100 px-4 py-3 text-left hover:bg-sand-50", !n.read && "bg-teal-50/60")}
              >
                <span className="text-lg">{ICON[n.type]}</span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-semibold text-ink-900">{n.title}</span>
                  {n.message && <span className="mt-0.5 line-clamp-2 block text-xs text-ink-500">{n.message}</span>}
                  <span className="mt-1 block text-[11px] text-ink-400">
                    {relativeDay(n.createdAt)} · {time(n.createdAt)}
                  </span>
                </span>
                {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-teal-600" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
