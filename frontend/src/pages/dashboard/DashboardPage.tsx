import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { count, money, moneyShort, shortDate, time } from "@/lib/format";
import { INTERACTION_ICON, INTERACTION_LABEL } from "@/lib/status";
import type { Interaction } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, Card, ErrorState, KpiCard, Pill, Skeleton } from "@/components/ui";
import { CustomerFormModal } from "../customers/CustomerFormModal";

interface Kpis {
  totalCustomers: number;
  customerGrowthPercent: number | null;
  newCustomersThisMonth: number;
  leadsConverted: number;
  monthLabel: string;
  revenueMtd: number;
  revenueChangePercent: number | null;
  previousMonthLabel: string;
  openTickets: number;
  nearSlaBreach: number;
  monthly: { month: string; revenue: number; newCustomers: number }[];
  segments: { segment: string; count: number; percent: number }[];
  atRisk: { id: number; name: string; companyName?: string; churnScore: number; lifetimeValue: number; ownerName?: string }[];
}

const SEGMENT_COLORS = ["#14776B", "#1FB5A3", "#F59E0B", "#9AA5B1"];

const arrow = (v: number | null) => (v == null ? "" : v >= 0 ? "▲" : "▼");

export default function DashboardPage() {
  const { user, hasRole } = useAuth();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const kpis = useQuery({ queryKey: ["dashboard", "kpis"], queryFn: () => api.get<Kpis>("/api/dashboard/kpis").then((r) => r.data), refetchInterval: 60_000 });
  const follow = useQuery({ queryKey: ["dashboard", "follow-ups"], queryFn: () => api.get<Interaction[]>("/api/dashboard/follow-ups").then((r) => r.data) });

  const now = new Date();
  const today = `${now.toLocaleDateString("en-GB", { weekday: "long" })}, ${shortDate(now.toISOString())} ${now.getFullYear()}`;
  const k = kpis.data;
  // one shared axis: new customers are scaled to the revenue range for display, tooltip shows real values
  const maxRevenue = Math.max(1, ...(k?.monthly.map((m) => m.revenue) ?? [1]));
  const maxCustomers = Math.max(1, ...(k?.monthly.map((m) => m.newCustomers) ?? [1]));
  const chartData = k?.monthly.map((m) => ({ ...m, customersScaled: (m.newCustomers / maxCustomers) * maxRevenue * 0.8 }));

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={`Welcome back, ${user?.fullName.split(" ")[0]} · ${today}`}
        actions={hasRole("ADMIN", "SALES_EXEC") && <Button onClick={() => setAdding(true)}>+ Add Customer</Button>}
      />

      {kpis.isError && <ErrorState message={apiError(kpis.error).message} onRetry={() => kpis.refetch()} />}

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {!k ? (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[86px] rounded-2xl" />)
        ) : (
          <>
            <KpiCard
              label="Total Customers"
              value={count(k.totalCustomers)}
              delta={k.customerGrowthPercent != null && `${arrow(k.customerGrowthPercent)} ${Math.abs(k.customerGrowthPercent)}% vs last month`}
              deltaTone={k.customerGrowthPercent != null && k.customerGrowthPercent < 0 ? "warn" : "up"}
            />
            <KpiCard label={`New Customers (${k.monthLabel})`} value={count(k.newCustomersThisMonth)} delta={`▲ ${k.leadsConverted} from leads converted`} />
            <KpiCard
              label="Revenue (MTD)"
              value={moneyShort(k.revenueMtd)}
              delta={k.revenueChangePercent != null ? `${arrow(k.revenueChangePercent)} ${Math.abs(Math.round(k.revenueChangePercent))}% vs ${k.previousMonthLabel}` : `No revenue in ${k.previousMonthLabel} yet`}
              deltaTone={k.revenueChangePercent != null && k.revenueChangePercent < 0 ? "warn" : "up"}
            />
            <button className="text-left" onClick={() => hasRole("ADMIN", "SUPPORT_AGENT") && navigate("/tickets")}>
              <KpiCard highlight label="Open Tickets" value={k.openTickets} delta={`${k.nearSlaBreach} near SLA breach`} deltaTone="warn" className="h-full" />
            </button>
          </>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
        <Card className="px-5 pb-4 pt-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-[15px] font-semibold">Monthly Revenue &amp; New Customers</h2>
            <span className="text-[13px] text-ink-500">Last 6 months</span>
          </div>
          <div className="h-[230px]">
            {k ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} barGap={6} margin={{ top: 4, right: 4, left: 4, bottom: 0 }}>
                  <XAxis dataKey="month" tickLine={false} axisLine={{ stroke: "#E8E1D4" }} tick={{ fontSize: 12, fill: "#6B7280" }} />
                  <YAxis hide />
                  <Tooltip
                    cursor={{ fill: "rgba(20,119,107,0.06)" }}
                    contentStyle={{ borderRadius: 12, border: "1px solid #E8E1D4", fontSize: 13 }}
                    formatter={(v: number, name: string, item: { payload?: { newCustomers: number } }) => (name === "revenue" ? [money(v), "Revenue"] : [item.payload?.newCustomers ?? 0, "New customers"])}
                  />
                  <Bar dataKey="revenue" fill="#14776B" radius={[4, 4, 0, 0]} maxBarSize={34} isAnimationActive={false} />
                  <Bar dataKey="customersScaled" fill="#F59E0B" radius={[4, 4, 0, 0]} maxBarSize={34} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <Skeleton className="h-full" />
            )}
          </div>
          <div className="mt-2 flex gap-4 text-[12.5px] text-ink-700">
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm bg-teal-700" />
              Revenue
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm bg-amber-500" />
              New customers
            </span>
          </div>
        </Card>

        <div className="space-y-4">
          <Card className="px-4 py-4">
            <h2 className="mb-3 text-[15px] font-semibold">Customers by Segment</h2>
            {k ? (
              <>
                <div className="flex h-2.5 overflow-hidden rounded-full">
                  {k.segments.map((s, i) => (
                    <div key={s.segment} style={{ width: `${s.percent}%`, background: SEGMENT_COLORS[i] }} title={`${s.segment}: ${s.count}`} />
                  ))}
                </div>
                <div className="mt-3 grid grid-cols-2 gap-y-1.5 text-[13px] text-ink-700">
                  {k.segments.map((s, i) => (
                    <button key={s.segment} onClick={() => navigate(`/customers?segment=${s.segment.toUpperCase()}`)} className="flex items-center gap-1.5 text-left hover:underline">
                      <span className="h-2 w-2 rounded-full" style={{ background: SEGMENT_COLORS[i] }} />
                      {s.segment} {Math.round(s.percent)}%
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <Skeleton className="h-16" />
            )}
          </Card>

          <Card className="px-4 py-4">
            <h2 className="mb-2 text-[15px] font-semibold">Follow-ups due today</h2>
            {follow.isLoading && <Skeleton className="h-20" />}
            {follow.data?.length === 0 && <p className="py-3 text-[13px] text-ink-500">No follow-ups due today 🎉</p>}
            <ul className="space-y-2">
              {follow.data?.slice(0, 6).map((f) => (
                <li key={f.id}>
                  <button onClick={() => navigate(`/customers/${f.customerId}`)} className="flex w-full items-center justify-between gap-2 text-left text-[13.5px] hover:underline">
                    <span className="truncate">
                      {INTERACTION_ICON[f.type]} {INTERACTION_LABEL[f.type]} · <b>{f.customerName}</b>
                      {f.companyName && !f.companyName.startsWith(f.customerName) && <span className="text-ink-700"> ({f.companyName.replace(/ (Pvt Ltd|LLP|Ltd)$/, "")})</span>}
                    </span>
                    <span className="shrink-0 text-ink-500">{time(f.followUpAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>

      {k && k.atRisk.length > 0 && (
        <Card className="mt-4 pb-4" title={<span>At-risk customers <span className="ml-1 text-[12.5px] font-normal text-ink-500">churn-risk score from recency, frequency, value and open tickets</span></span>}>
          <div className="mt-3 grid gap-3 px-5 md:grid-cols-3 xl:grid-cols-5">
            {k.atRisk.map((c) => (
              <button key={c.id} onClick={() => navigate(`/customers/${c.id}`)} className="rounded-xl border border-sand-300 bg-sand-50 px-3 py-2.5 text-left hover:border-red-300">
                <div className="flex items-center justify-between">
                  <span className="truncate text-[13.5px] font-semibold">{c.name}</span>
                  <Pill tone={c.churnScore >= 60 ? "red" : "amber"}>{c.churnScore}</Pill>
                </div>
                <p className="truncate text-[12px] text-ink-500">
                  {moneyShort(c.lifetimeValue)} LTV · {c.ownerName ?? "Unassigned"}
                </p>
              </button>
            ))}
          </div>
        </Card>
      )}

      <CustomerFormModal open={adding} onClose={() => setAdding(false)} onSaved={(c) => navigate(`/customers/${c.id}`)} />
    </>
  );
}
