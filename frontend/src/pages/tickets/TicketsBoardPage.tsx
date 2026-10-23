import clsx from "clsx";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { duration, sla } from "@/lib/format";
import { CHANNEL_LABEL, PRIORITY, TICKET_STATUS } from "@/lib/status";
import type { TicketPriority, TicketStatus, TicketSummary } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, ErrorState, Pill, Segmented, Select, Skeleton } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { NewTicketModal } from "./NewTicketModal";

interface Board {
  columns: { status: TicketStatus; count: number; tickets: TicketSummary[] }[];
  openCount: number;
  avgFirstResponseMinutes: number | null;
  slaCompliance: number | null;
}

/** Re-render every 30s so SLA countdowns stay current. */
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

export function SlaBadge({ t, now }: { t: TicketSummary; now: number }) {
  if (t.status === "RESOLVED" && t.resolvedAt) {
    const took = new Date(t.resolvedAt).getTime() - new Date(t.createdAt).getTime();
    const ok = new Date(t.resolvedAt) <= new Date(t.slaDueAt);
    return <span className={clsx("text-[12.5px] font-semibold", ok ? "text-[#1F7A3F]" : "text-[#B42323]")}>⏱{ok ? "✔" : "✖"} {fmtHours(took)}</span>;
  }
  if (t.status === "AWAITING_CUSTOMER") return <span className="text-[12.5px] text-ink-500">⏱ paused</span>;
  const s = sla(t.slaDueAt, t.createdAt, now);
  return (
    <span className={clsx("text-[12.5px] font-semibold", s.tone === "red" && "text-[#B42323]", s.tone === "amber" && "text-[#B4520E]", s.tone === "green" && "text-ink-700")}>
      ⏱ {s.label}
    </span>
  );
}

function fmtHours(ms: number) {
  const h = ms / 3_600_000;
  return h < 1 ? `${Math.max(1, Math.round(ms / 60_000))}m` : h < 48 ? `${h.toFixed(1).replace(/\.0$/, "")}h` : duration(ms);
}

export default function TicketsBoardPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const now = useNow();
  const [creating, setCreating] = useState(false);
  const [scope, setScope] = useState<"all" | "mine">("all");
  const [priority, setPriority] = useState<"" | TicketPriority>("");
  const [dragging, setDragging] = useState<TicketSummary | null>(null);
  const [over, setOver] = useState<TicketStatus | null>(null);

  const board = useQuery({
    queryKey: ["tickets", "board", scope, priority],
    queryFn: () => api.get<Board>("/api/tickets", { params: { mine: scope === "mine", priority: priority || undefined } }).then((r) => r.data),
    refetchInterval: 60_000,
  });

  const move = useMutation({
    mutationFn: ({ id, status }: { id: number; status: TicketStatus }) => api.patch(`/api/tickets/${id}/status`, { status }),
    onSuccess: (_, v) => {
      qc.invalidateQueries({ queryKey: ["tickets"] });
      qc.invalidateQueries({ queryKey: ["nav"] });
      toast(`Moved to ${TICKET_STATUS[v.status].label}`);
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  const b = board.data;
  const subtitle = b
    ? [`${b.openCount} open`, b.avgFirstResponseMinutes != null && `Avg. first response ${b.avgFirstResponseMinutes} min`, b.slaCompliance != null && `SLA compliance ${Math.round(b.slaCompliance)}%`]
        .filter(Boolean)
        .join(" · ")
    : " ";

  return (
    <>
      <PageHeader title="Support Tickets" subtitle={subtitle} actions={<Button onClick={() => setCreating(true)}>+ New Ticket</Button>} />

      <div className="mb-4 flex items-center gap-2.5">
        <Segmented
          value={scope}
          onChange={setScope}
          options={[
            { value: "all", label: "All tickets" },
            { value: "mine", label: "Assigned to me" },
          ]}
        />
        <Select className="h-[38px] w-44 text-[13.5px]" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority | "")}>
          <option value="">Priority: All</option>
          <option value="HIGH">Priority: High</option>
          <option value="MEDIUM">Priority: Medium</option>
          <option value="LOW">Priority: Low</option>
        </Select>
        <span className="ml-auto text-xs text-ink-500">Drag a card to change its status</span>
      </div>

      {board.isError && <ErrorState message={apiError(board.error).message} onRetry={() => board.refetch()} />}

      <div className="grid grid-cols-4 gap-4">
        {(b?.columns ?? (Object.keys(TICKET_STATUS) as TicketStatus[]).map((status) => ({ status, count: 0, tickets: [] }))).map((col) => {
          const meta = TICKET_STATUS[col.status];
          return (
            <section
              key={col.status}
              onDragOver={(e) => {
                e.preventDefault();
                setOver(col.status);
              }}
              onDragLeave={() => setOver(null)}
              onDrop={() => {
                if (dragging && dragging.status !== col.status) move.mutate({ id: dragging.id, status: col.status });
                setDragging(null);
                setOver(null);
              }}
              className={clsx("min-h-[360px] rounded-2xl bg-sand-200/80 p-3 transition", over === col.status && dragging?.status !== col.status && "ring-2 ring-teal-600/40")}
            >
              <header className="mb-3 flex items-center justify-between px-1">
                <h2 className="flex items-center gap-2 text-[14.5px] font-semibold">
                  <span className={clsx("h-2.5 w-2.5 rounded-full", meta.dot)} />
                  {meta.label}
                </h2>
                <span className="text-xs text-ink-500">{col.count}</span>
              </header>
              <div className="space-y-2.5">
                {board.isLoading && <Skeleton className="h-24 rounded-xl" />}
                {col.tickets.map((t) => (
                  <article
                    key={t.id}
                    draggable
                    onDragStart={() => setDragging(t)}
                    onDragEnd={() => setDragging(null)}
                    onClick={() => navigate(`/tickets/${t.id}`)}
                    className={clsx("cursor-pointer rounded-xl border border-white bg-white px-3 py-2.5 shadow-card transition hover:border-teal-600/30 hover:shadow-md", dragging?.id === t.id && "opacity-50", t.slaBreached && t.status !== "RESOLVED" && "border-l-4 border-l-red-500")}
                  >
                    <div className="flex items-center justify-between text-[12px] text-ink-500">
                      <span>
                        {t.ticketNumber}
                        {t.escalated && <span className="ml-1.5 font-semibold text-red-600">▲ escalated</span>}
                      </span>
                      <span>{CHANNEL_LABEL[t.channel]}</span>
                    </div>
                    <p className="mt-1 text-[14px] font-semibold leading-snug text-ink-900">{t.subject}</p>
                    <p className="mt-0.5 text-[12.5px] text-ink-500">👤 {t.customerName}</p>
                    <div className="mt-2.5 flex items-center justify-between">
                      <Pill tone={PRIORITY[t.priority].tone}>{PRIORITY[t.priority].label}</Pill>
                      <SlaBadge t={t} now={now} />
                    </div>
                  </article>
                ))}
              </div>
            </section>
          );
        })}
      </div>

      <NewTicketModal open={creating} onClose={() => setCreating(false)} onCreated={(t) => navigate(`/tickets/${t.id}`)} />
    </>
  );
}
