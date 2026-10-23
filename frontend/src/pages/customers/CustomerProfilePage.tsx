import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { duration, friendlyDateTime, money, moneyShort, monthYear, relativeDay, shortDate, titleCase } from "@/lib/format";
import { CUSTOMER_STATUS, ORDER_STATUS, PAYMENT_STATUS, PRIORITY, TICKET_STATUS, tagTone, type Tone } from "@/lib/status";
import type { Customer360, CustomerStatus, InteractionType, TimelineEvent } from "@/lib/types";
import { Avatar, Button, Card, EmptyState, ErrorState, Pill, Skeleton, Tabs, Textarea } from "@/components/ui";
import { NotificationBell } from "@/components/layout/NotificationBell";
import { useToast } from "@/components/Toast";
import { CustomerFormModal } from "./CustomerFormModal";
import { LogInteractionModal } from "../interactions/LogInteractionModal";

type Tab = "timeline" | "orders" | "tickets" | "notes";

const SOURCE_LABEL: Record<string, string> = { WEBSITE: "Website", REFERRAL: "Referral", WALK_IN: "Walk-in", CAMPAIGN: "Campaign", OTHER: "Other" };

const iconBg: Record<TimelineEvent["kind"], string> = {
  INTERACTION: "bg-[#E3F4E8]",
  ORDER: "bg-[#E1ECFB]",
  TICKET: "bg-[#FCEFC7]",
  ACTIVITY: "bg-sand-200",
};

export default function CustomerProfilePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole("ADMIN", "SALES_EXEC");
  const [tab, setTab] = useState<Tab>("timeline");
  const [editing, setEditing] = useState(false);
  const [logType, setLogType] = useState<InteractionType | null>(null);

  const q = useQuery({
    queryKey: ["customer", id],
    queryFn: () => api.get<Customer360>(`/api/customers/${id}`).then((r) => r.data),
  });

  if (q.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-24 w-full rounded-2xl" />
        <div className="grid grid-cols-[312px_1fr] gap-4">
          <Skeleton className="h-80 rounded-2xl" />
          <Skeleton className="h-80 rounded-2xl" />
        </div>
      </div>
    );
  }
  if (q.isError || !q.data) return <ErrorState message={apiError(q.error).message} onRetry={() => q.refetch()} />;

  const c = q.data;
  const st = CUSTOMER_STATUS[c.status];
  const billing = c.addresses.find((a) => a.addressType === "BILLING") ?? c.addresses[0];
  const shipping = c.addresses.find((a) => a.addressType === "SHIPPING");
  const fmtAddress = (a: typeof billing) => (a ? [a.line1, a.line2, a.city, a.state].filter(Boolean).join(", ") + (a.postalCode ? ` ${a.postalCode}` : "") : "—");
  const subtitle = [c.customerCode, [c.jobTitle, c.companyName].filter(Boolean).join(", "), titleCase(c.segment), c.source && `Source: ${SOURCE_LABEL[c.source]}`].filter(Boolean).join(" · ");

  return (
    <>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-[13px] text-ink-500">
          <Link to="/customers" className="hover:underline">
            Customers
          </Link>{" "}
          › <span className="font-semibold text-ink-900">{c.fullName}</span>
        </p>
        <NotificationBell />
      </div>

      <Card className="mb-4 flex items-center justify-between gap-4 px-5 py-4">
        <div className="flex min-w-0 flex-1 items-center gap-4">
          <Avatar name={c.fullName} size={52} color="#14776B" />
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-[22px] font-bold tracking-tight">{c.fullName}</h1>
              <Pill tone={st.tone}>{st.label}</Pill>
              {c.tags.map((t) => (
                <Pill key={t.id} tone={tagTone(t.color)}>
                  {t.name}
                </Pill>
              ))}
            </div>
            <p className="mt-0.5 text-[13px] text-ink-500">{subtitle}</p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {canEdit && (
            <>
              <Button variant="outline" onClick={() => setLogType("CALL")}>
                📞 Log Call
              </Button>
              <Button variant="outline" onClick={() => setLogType("EMAIL")}>
                ✉️ Email
              </Button>
              <Button onClick={() => navigate(`/orders?new=${c.id}`)}>+ New Order</Button>
              <MoreMenu customer={c} onEdit={() => setEditing(true)} />
            </>
          )}
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-[312px_1fr]">
        <div className="space-y-4">
          <Card className="px-4 py-4">
            <h2 className="mb-2.5 text-[15px] font-semibold">Contact Details</h2>
            <ul className="space-y-2 text-[13.5px] text-ink-700">
              <li>
                📧{" "}
                <a href={`mailto:${c.email}`} className="hover:underline">
                  {c.email}
                </a>
              </li>
              <li>📱 {c.phone ? formatPhone(c.phone) : "—"}</li>
              <li>🏢 Billing: {fmtAddress(billing)}</li>
              <li>🚚 Shipping: {shipping ? fmtAddress(shipping) : "Same as billing"}</li>
              <li>👤 Owner: {c.owner?.name ?? "Unassigned"}</li>
            </ul>
          </Card>

          <Card className="px-4 py-4">
            <h2 className="mb-3 text-[15px] font-semibold">Account Summary</h2>
            <div className="grid grid-cols-2 gap-2.5">
              <SummaryTile label="Lifetime Value" value={moneyShort(c.lifetimeValue)} />
              <SummaryTile label="Orders" value={String(c.orderCount)} />
              <SummaryTile label="Open Tickets" value={String(c.openTickets)} tone={c.openTickets > 0 ? "amber" : undefined} />
              <SummaryTile label="Customer Since" value={monthYear(c.createdAt)} />
            </div>
          </Card>

          <ChurnCard customer={c} />
        </div>

        <Card className="min-h-[340px]">
          <Tabs<Tab>
            value={tab}
            onChange={setTab}
            tabs={[
              { value: "timeline", label: "Timeline" },
              { value: "orders", label: `Orders (${c.orderCount})` },
              { value: "tickets", label: `Tickets (${c.ticketCount})` },
              { value: "notes", label: "Notes" },
            ]}
          />
          <div className="px-4 py-3">
            {tab === "timeline" && <Timeline events={c.timeline} />}
            {tab === "orders" && <OrdersTab c={c} />}
            {tab === "tickets" && <TicketsTab c={c} />}
            {tab === "notes" && <NotesTab c={c} canEdit={canEdit} />}
          </div>
        </Card>
      </div>

      <CustomerFormModal open={editing} onClose={() => setEditing(false)} customer={c} />
      <LogInteractionModal open={!!logType} onClose={() => setLogType(null)} customerId={c.id} type={logType ?? "CALL"} />
    </>
  );
}

function formatPhone(p: string) {
  return p.length === 10 ? `+91 ${p.slice(0, 5)} ${p.slice(5)}` : p;
}

function SummaryTile({ label, value, tone }: { label: string; value: string; tone?: "amber" }) {
  return (
    <div className={clsx("rounded-xl px-3 py-2.5", tone === "amber" ? "bg-[#FDF0C4]" : "bg-teal-50")}>
      <p className="text-[12px] text-ink-500">{label}</p>
      <p className="text-[19px] font-bold leading-tight text-ink-900">{value}</p>
    </div>
  );
}

function ChurnCard({ customer: c }: { customer: Customer360 }) {
  const r = c.churnRisk;
  const color = r.level === "HIGH" ? "#DC2626" : r.level === "MEDIUM" ? "#D97706" : "#16A34A";
  const tone: Tone = r.level === "HIGH" ? "red" : r.level === "MEDIUM" ? "amber" : "green";
  return (
    <Card className="px-4 py-4">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-[15px] font-semibold">Churn Risk</h2>
        <Pill tone={tone}>
          {titleCase(r.level)} · {r.score}/100
        </Pill>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-sand-200">
        <div className="h-full rounded-full" style={{ width: `${Math.max(3, r.score)}%`, background: color }} />
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-center text-[11.5px] text-ink-500">
        <div>
          <p className="text-[15px] font-bold text-ink-900">{r.recencyDays ?? "—"}</p>days since order
        </div>
        <div>
          <p className="text-[15px] font-bold text-ink-900">{r.frequency}</p>paid orders
        </div>
        <div>
          <p className="text-[15px] font-bold text-ink-900">{moneyShort(r.monetary)}</p>monetary
        </div>
      </div>
      <ul className="mt-3 space-y-1 text-[12.5px] text-ink-700">
        {r.reasons.map((x) => (
          <li key={x}>• {x}</li>
        ))}
      </ul>
    </Card>
  );
}

function Timeline({ events }: { events: TimelineEvent[] }) {
  const navigate = useNavigate();
  if (!events.length) return <EmptyState icon="🕘" title="No activity yet" message="Calls, orders and tickets will appear here." />;
  return (
    <ul className="divide-y divide-sand-100">
      {events.map((e, i) => {
        const sub: React.ReactNode[] = [e.actor, friendlyDateTime(e.at).replace(/^(Today|Yesterday|\d+ days ago),.*/, (m) => m)];
        return (
          <li key={i} className={clsx("flex gap-3 py-3", e.link && "cursor-pointer hover:bg-sand-50")} onClick={() => e.link && navigate(e.link)}>
            <span className={clsx("flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[14px]", iconBg[e.kind])}>{e.icon}</span>
            <div className="min-w-0">
              <p className="text-[14px] font-semibold text-ink-900">{e.title}</p>
              <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[12.5px] text-ink-500">
                <span>{sub[0]}</span>
                <span>·</span>
                <span>{e.kind === "INTERACTION" ? friendlyDateTime(e.at) : shortDate(e.at)}</span>
                {e.badges.map((b) => (
                  <Pill key={b.label} tone={b.tone as Tone}>
                    {b.label}
                  </Pill>
                ))}
                {e.followUpAt && <span>· Follow-up set for {shortDate(e.followUpAt)}</span>}
                {e.slaDueAt && <span>· {new Date(e.slaDueAt).getTime() > Date.now() ? `SLA due in ${duration(new Date(e.slaDueAt).getTime() - Date.now())}` : "SLA breached"}</span>}
              </p>
              {e.body && <p className="mt-1 text-[13.5px] text-ink-700">{e.body}</p>}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function OrdersTab({ c }: { c: Customer360 }) {
  const navigate = useNavigate();
  if (!c.recentOrders.length) return <EmptyState icon="🧾" title="No orders yet" />;
  return (
    <table className="w-full text-left text-[13.5px]">
      <thead className="text-[11px] font-semibold uppercase text-ink-500">
        <tr>
          <th className="py-2">Order</th>
          <th className="py-2">Date</th>
          <th className="py-2">Items</th>
          <th className="py-2">Total</th>
          <th className="py-2">Payment</th>
          <th className="py-2">Status</th>
        </tr>
      </thead>
      <tbody>
        {c.recentOrders.map((o) => (
          <tr key={o.id} className="cursor-pointer border-t border-sand-200 hover:bg-sand-50" onClick={() => navigate(`/orders?open=${o.id}`)}>
            <td className="py-2 font-semibold">{o.orderNumber}</td>
            <td className="py-2 text-ink-500">{shortDate(o.orderDate)}</td>
            <td className="py-2">{o.itemCount}</td>
            <td className="py-2 font-semibold">{money(o.totalAmount)}</td>
            <td className="py-2">
              <Pill tone={PAYMENT_STATUS[o.paymentStatus].tone}>{PAYMENT_STATUS[o.paymentStatus].label}</Pill>
            </td>
            <td className="py-2">
              <Pill tone={ORDER_STATUS[o.status].tone}>{ORDER_STATUS[o.status].label}</Pill>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TicketsTab({ c }: { c: Customer360 }) {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canOpen = hasRole("ADMIN", "SUPPORT_AGENT");
  if (!c.recentTickets.length) return <EmptyState icon="🎫" title="No support tickets" />;
  return (
    <ul className="divide-y divide-sand-100">
      {c.recentTickets.map((t) => (
        <li key={t.id} className={clsx("flex items-center justify-between gap-3 py-3", canOpen && "cursor-pointer hover:bg-sand-50")} onClick={() => canOpen && navigate(`/tickets/${t.id}`)}>
          <div>
            <p className="text-[14px] font-semibold">
              {t.ticketNumber} — {t.subject}
            </p>
            <p className="text-[12.5px] text-ink-500">
              {t.assignee?.name ?? "Unassigned"} · {relativeDay(t.createdAt)}
            </p>
          </div>
          <div className="flex gap-1.5">
            <Pill tone={PRIORITY[t.priority].tone}>{PRIORITY[t.priority].label}</Pill>
            <Pill tone={TICKET_STATUS[t.status].tone}>{TICKET_STATUS[t.status].label}</Pill>
          </div>
        </li>
      ))}
    </ul>
  );
}

function NotesTab({ c, canEdit }: { c: Customer360; canEdit: boolean }) {
  const [text, setText] = useState(c.notes ?? "");
  const qc = useQueryClient();
  const toast = useToast();
  const save = useMutation({
    mutationFn: () => api.patch(`/api/customers/${c.id}/notes`, { notes: text }),
    onSuccess: () => {
      toast("Notes saved");
      qc.invalidateQueries({ queryKey: ["customer", String(c.id)] });
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });
  const notes = c.interactions.filter((i) => i.type === "NOTE");
  return (
    <div className="space-y-4">
      <div>
        <p className="mb-1.5 text-[13px] font-semibold text-ink-700">Account notes</p>
        <Textarea rows={5} value={text} onChange={(e) => setText(e.target.value)} disabled={!canEdit} placeholder="Preferences, credit terms, key contacts…" />
        {canEdit && (
          <div className="mt-2 flex justify-end">
            <Button size="sm" onClick={() => save.mutate()} loading={save.isPending} disabled={text === (c.notes ?? "")}>
              Save notes
            </Button>
          </div>
        )}
      </div>
      {notes.length > 0 && (
        <div>
          <p className="mb-1.5 text-[13px] font-semibold text-ink-700">Logged notes</p>
          <ul className="space-y-2">
            {notes.map((n) => (
              <li key={n.id} className="rounded-xl bg-sand-100 px-3 py-2 text-[13.5px]">
                <p className="font-semibold">{n.subject}</p>
                {n.notes && <p className="text-ink-700">{n.notes}</p>}
                <p className="mt-0.5 text-[12px] text-ink-500">
                  {n.user.name} · {friendlyDateTime(n.interactionAt)}
                </p>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function MoreMenu({ customer: c, onEdit }: { customer: Customer360; onEdit: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => {
    const h = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  const status = useMutation({
    mutationFn: (s: CustomerStatus) => api.patch(`/api/customers/${c.id}/status`, { status: s }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["customer"] });
      qc.invalidateQueries({ queryKey: ["customers"] });
      toast("Status updated");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });
  const del = useMutation({
    mutationFn: () => api.delete(`/api/customers/${c.id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["customers"] });
      toast(`${c.fullName} deleted`);
      navigate("/customers");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  return (
    <div className="relative" ref={ref}>
      <Button variant="ghost" className="px-2.5" onClick={() => setOpen((o) => !o)} aria-label="More actions">
        ⋯
      </Button>
      {open && (
        <div className="absolute right-0 z-20 mt-1 w-52 overflow-hidden rounded-xl border border-sand-300 bg-white py-1 text-sm shadow-pop">
          <button className="block w-full px-3 py-2 text-left hover:bg-sand-50" onClick={() => (setOpen(false), onEdit())}>
            ✏️ Edit details
          </button>
          <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase text-ink-400">Change status</p>
          {(Object.keys(CUSTOMER_STATUS) as CustomerStatus[])
            .filter((s) => s !== c.status)
            .map((s) => (
              <button key={s} className="block w-full px-3 py-1.5 text-left hover:bg-sand-50" onClick={() => (setOpen(false), status.mutate(s))}>
                → {CUSTOMER_STATUS[s].label}
              </button>
            ))}
          {hasRole("ADMIN") && (
            <button
              className="mt-1 block w-full border-t border-sand-200 px-3 py-2 text-left text-red-700 hover:bg-red-50"
              onClick={() => {
                setOpen(false);
                if (confirm(`Delete ${c.fullName}? The record is hidden from lists but kept for order and ticket history.`)) del.mutate();
              }}
            >
              🗑 Delete customer
            </button>
          )}
        </div>
      )}
    </div>
  );
}
