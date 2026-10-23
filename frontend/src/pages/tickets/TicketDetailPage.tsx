import clsx from "clsx";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { dateTime, sla, time } from "@/lib/format";
import { useUsers } from "@/lib/queries";
import { CHANNEL_LABEL, PRIORITY, TICKET_STATUS } from "@/lib/status";
import type { TicketDetail, TicketStatus } from "@/lib/types";
import { Avatar, Button, Card, ErrorState, Pill, Select, Skeleton } from "@/components/ui";
import { NotificationBell } from "@/components/layout/NotificationBell";
import { useToast } from "@/components/Toast";
import { useNow } from "./TicketsBoardPage";

const CANNED = [
  "Thanks for reaching out — we're looking into this and will update you shortly.",
  "Apologies for the inconvenience. A replacement has been dispatched today.",
  "Could you please share a photo of the product and your order number?",
  "The refund has been initiated and should reflect in 5–7 working days.",
  "We've updated your account details as requested.",
];

export default function TicketDetailPage() {
  const { id } = useParams();
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const agents = useUsers();
  const qc = useQueryClient();
  const toast = useToast();
  const now = useNow(30_000);
  const [reply, setReply] = useState("");
  const [showCanned, setShowCanned] = useState(false);

  const q = useQuery({ queryKey: ["tickets", "detail", id], queryFn: () => api.get<TicketDetail>(`/api/tickets/${id}`).then((r) => r.data) });

  const onDone = (msg: string) => (data: { data: TicketDetail }) => {
    qc.setQueryData(["tickets", "detail", id], data.data);
    qc.invalidateQueries({ queryKey: ["tickets", "board"] });
    qc.invalidateQueries({ queryKey: ["nav"] });
    toast(msg);
  };
  const onError = (e: unknown) => toast(apiError(e).message, "error");

  const comment = useMutation({
    mutationFn: (v: { internal: boolean; fromCustomer?: boolean }) => api.post<TicketDetail>(`/api/tickets/${id}/comments`, { body: reply, internal: v.internal, fromCustomer: !!v.fromCustomer }),
    onSuccess: (d, v) => {
      setReply("");
      onDone(v.fromCustomer ? "Customer message logged" : v.internal ? "Internal note added" : "Reply sent")(d);
    },
    onError,
  });
  const status = useMutation({ mutationFn: (s: TicketStatus) => api.patch<TicketDetail>(`/api/tickets/${id}/status`, { status: s }), onSuccess: onDone("Status updated"), onError });
  const escalate = useMutation({ mutationFn: () => api.post<TicketDetail>(`/api/tickets/${id}/escalate`), onSuccess: onDone("Ticket escalated — admins notified"), onError });
  const assign = useMutation({ mutationFn: (assigneeId: number) => api.patch<TicketDetail>(`/api/tickets/${id}/assign`, { assigneeId }), onSuccess: onDone("Assignee updated"), onError });

  if (q.isLoading) return <Skeleton className="h-[500px] rounded-2xl" />;
  if (q.isError || !q.data) return <ErrorState message={apiError(q.error).message} onRetry={() => q.refetch()} />;
  const t = q.data;
  const resolved = t.status === "RESOLVED";
  const s = sla(t.slaDueAt, t.createdAt, now);
  const slaHours = Math.round((new Date(t.slaDueAt).getTime() - new Date(t.createdAt).getTime()) / 3_600_000);

  return (
    <>
      <div className="mb-1 flex items-center justify-between">
        <p className="text-[13px] text-ink-500">
          <Link to="/tickets" className="hover:underline">
            Support Tickets
          </Link>{" "}
          › <span className="font-semibold text-ink-900">{t.ticketNumber}</span>
        </p>
        <NotificationBell />
      </div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-bold tracking-tight">{t.subject}</h1>
          <p className="text-[13px] text-ink-500">
            Raised by{" "}
            <Link to={`/customers/${t.customerId}`} className="hover:underline">
              {t.customerName}
            </Link>{" "}
            · via {CHANNEL_LABEL[t.channel]}
            {t.orderNumber && ` · linked to ${t.orderNumber}`}
          </p>
        </div>
        <div className="flex gap-2">
          {resolved ? (
            <Button variant="outline" onClick={() => status.mutate("IN_PROGRESS")} loading={status.isPending}>
              ↺ Reopen
            </Button>
          ) : (
            <>
              <Button variant="outline" onClick={() => escalate.mutate()} disabled={t.escalated} loading={escalate.isPending}>
                {t.escalated ? "Escalated" : "Escalate"}
              </Button>
              <Button onClick={() => status.mutate("RESOLVED")} loading={status.isPending}>
                Mark Resolved
              </Button>
            </>
          )}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_312px]">
        <Card className="p-4">
          <ul className="space-y-3">
            {t.comments.length === 0 && <li className="py-6 text-center text-sm text-ink-500">No messages yet</li>}
            {t.comments.map((c) => (
              <li key={c.id} className="flex gap-3">
                {c.internal ? (
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-400 text-[13px]">🔒</span>
                ) : (
                  <Avatar name={c.authorName} initials={c.fromCustomer ? undefined : c.author?.initials} size={32} color={c.fromCustomer ? "#14776B" : "#6D28D9"} />
                )}
                <div className={clsx("flex-1 rounded-xl px-3.5 py-2.5", c.internal ? "border border-dashed border-amber-300 bg-[#FFF9E0]" : c.fromCustomer ? "bg-sand-100" : "bg-teal-50")}>
                  <p className="text-[13px]">
                    <b>{c.internal ? "Internal note" : c.authorName}</b>
                    {!c.internal && !c.fromCustomer && <span className="ml-1.5 rounded px-1 text-[11px] font-semibold text-teal-700">Agent</span>}
                    <span className="ml-1.5 text-[12px] text-ink-500">{dateTime(c.createdAt)}</span>
                  </p>
                  <p className="mt-0.5 whitespace-pre-line text-[13.5px] text-ink-900">{c.body}</p>
                </div>
              </li>
            ))}
          </ul>

          {!resolved && (
            <div className="mt-4">
              <textarea
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                placeholder="Type a reply to the customer..."
                rows={3}
                className="w-full resize-y rounded-xl border border-sand-300 px-3.5 py-2.5 text-sm focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-600/15"
              />
              <div className="mt-2 flex items-center justify-between">
                <div className="relative flex items-center gap-1 text-[12.5px] text-ink-500">
                  <button onClick={() => comment.mutate({ internal: false, fromCustomer: true })} disabled={!reply.trim()} className="hover:text-ink-900 disabled:opacity-50" title="Record a message the customer sent by phone or email">
                    📎 Log customer message
                  </button>
                  ·
                  <button onClick={() => setShowCanned((v) => !v)} className="hover:text-ink-900">
                    ⚡ Canned replies
                  </button>
                  {showCanned && (
                    <div className="absolute bottom-7 left-0 z-10 w-80 overflow-hidden rounded-xl border border-sand-300 bg-white py-1 shadow-pop">
                      {CANNED.map((c) => (
                        <button
                          key={c}
                          onClick={() => {
                            setReply(c);
                            setShowCanned(false);
                          }}
                          className="block w-full px-3 py-2 text-left text-[13px] text-ink-900 hover:bg-sand-50"
                        >
                          {c}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" disabled={!reply.trim()} onClick={() => comment.mutate({ internal: true })} loading={comment.isPending && comment.variables?.internal}>
                    Add internal note
                  </Button>
                  <Button disabled={!reply.trim()} onClick={() => comment.mutate({ internal: false })} loading={comment.isPending && !comment.variables?.internal && !comment.variables?.fromCustomer}>
                    Send reply
                  </Button>
                </div>
              </div>
            </div>
          )}
        </Card>

        <div className="space-y-4">
          <SlaCard t={t} s={s} slaHours={slaHours} />

          <Card className="px-4 py-4">
            <h2 className="mb-2 text-[15px] font-semibold">Details</h2>
            <dl className="space-y-2 text-[13.5px]">
              <Row label="Status">
                {resolved ? (
                  <Pill tone={TICKET_STATUS[t.status].tone}>{TICKET_STATUS[t.status].label}</Pill>
                ) : (
                  <select value={t.status} onChange={(e) => status.mutate(e.target.value as TicketStatus)} className={clsx("cursor-pointer rounded-full border-0 px-2.5 py-0.5 text-[11px] font-semibold focus:outline-none", pillBg[t.status])}>
                    {(Object.keys(TICKET_STATUS) as TicketStatus[]).map((st) => (
                      <option key={st} value={st}>
                        {TICKET_STATUS[st].label}
                      </option>
                    ))}
                  </select>
                )}
              </Row>
              <Row label="Priority">
                <Pill tone={PRIORITY[t.priority].tone}>{PRIORITY[t.priority].label}</Pill>
              </Row>
              <Row label="Assignee">
                {isAdmin && !resolved ? (
                  <Select className="h-8 w-40 rounded-lg py-0 text-right text-[13px] font-semibold" value={t.assignee?.id ?? ""} onChange={(e) => assign.mutate(Number(e.target.value))}>
                    {!t.assignee && <option value="">Unassigned</option>}
                    {agents.data
                      ?.filter((u) => u.role !== "SALES_EXEC")
                      .map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.fullName}
                        </option>
                      ))}
                  </Select>
                ) : (
                  <b>{t.assignee?.name ?? "Unassigned"}</b>
                )}
              </Row>
              <Row label="Channel">{CHANNEL_LABEL[t.channel]}</Row>
              <Row label="Category">{t.category.replace("_", " ").toLowerCase().replace(/^./, (x) => x.toUpperCase())}</Row>
              <Row label="Created">{dateTime(t.createdAt)}</Row>
              {t.resolvedAt && <Row label="Resolved">{dateTime(t.resolvedAt)}</Row>}
            </dl>
          </Card>

          <Card className="px-4 py-4">
            <h2 className="mb-2 text-[15px] font-semibold">Activity</h2>
            <ul className="space-y-1.5 text-[13px]">
              {t.activity.map((a) => (
                <li key={a.id} className="flex gap-2">
                  <span className="shrink-0 text-ink-500">{time(a.createdAt)}</span>
                  <span className="text-ink-900">{a.summary}</span>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </>
  );
}

const pillBg: Record<TicketStatus, string> = {
  OPEN: "bg-[#FDE9D9] text-[#B4520E]",
  IN_PROGRESS: "bg-[#E1ECFB] text-[#2556A8]",
  AWAITING_CUSTOMER: "bg-[#EEE7FD] text-[#6B3FD0]",
  RESOLVED: "bg-[#E3F4E8] text-[#1F7A3F]",
};

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

function SlaCard({ t, s, slaHours }: { t: TicketDetail; s: ReturnType<typeof sla>; slaHours: number }) {
  const label = `SLA (${PRIORITY[t.priority].label} · ${slaHours}h)`;
  if (t.status === "RESOLVED") {
    const ok = t.resolvedAt && new Date(t.resolvedAt) <= new Date(t.slaDueAt);
    return (
      <div className={clsx("rounded-2xl border-[1.5px] px-4 py-3.5", ok ? "border-green-300 bg-[#F0FAF3]" : "border-red-300 bg-[#FEF2F2]")}>
        <p className="text-[12.5px] text-ink-500">{label}</p>
        <p className={clsx("text-[22px] font-bold", ok ? "text-[#1F7A3F]" : "text-[#B42323]")}>{ok ? "Met ✔" : "Breached"}</p>
      </div>
    );
  }
  if (t.status === "AWAITING_CUSTOMER") {
    return (
      <div className="rounded-2xl border-[1.5px] border-purple-200 bg-[#F7F3FF] px-4 py-3.5">
        <p className="text-[12.5px] text-ink-500">{label}</p>
        <p className="text-[22px] font-bold text-[#6B3FD0]">Paused</p>
        <p className="text-[12px] text-ink-500">Waiting on the customer · {s.label}</p>
      </div>
    );
  }
  const colors = { red: ["border-red-300 bg-[#FEF2F2]", "text-[#B42323]", "bg-[#C62828]"], amber: ["border-amber-300 bg-[#FFFBEB]", "text-[#B4520E]", "bg-[#D97706]"], green: ["border-green-300 bg-[#F0FAF3]", "text-[#1F7A3F]", "bg-[#16A34A]"], gray: ["", "", ""] }[s.tone];
  return (
    <div className={clsx("rounded-2xl border-[1.5px] px-4 py-3.5", colors[0])}>
      <p className="text-[12.5px] text-ink-500">{label}</p>
      <p className={clsx("text-[22px] font-bold", colors[1])}>{s.label}</p>
      <div className="mt-2 h-2 overflow-hidden rounded-full bg-white/80">
        <div className={clsx("h-full rounded-full", colors[2])} style={{ width: `${Math.max(4, (1 - s.ratio) * 100)}%` }} />
      </div>
    </div>
  );
}
