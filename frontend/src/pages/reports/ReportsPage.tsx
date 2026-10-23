import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import api, { apiError, download } from "@/lib/api";
import { count, moneyShort } from "@/lib/format";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, Card, EmptyState, ErrorState, KpiCard, Select, Skeleton } from "@/components/ui";
import { useToast } from "@/components/Toast";

interface Kpi {
  value: number | null;
  delta: number | null;
  note: string;
}
interface Report {
  period: string;
  periodLabel: string;
  rangeLabel: string;
  retention: Kpi;
  conversion: Kpi;
  avgResolutionHours: Kpi;
  slaCompliance: Kpi;
  slaBreaches: number;
  funnel: { stage: string; count: number; percent: number }[];
  ticketTotal: number;
  ticketCategories: { category: string; count: number; percent: number }[];
  executives: { userId: number; name: string; customersOwned: number; interactions: number; ordersClosed: number; revenue: number; conversionPercent: number }[];
  revenue: number;
  ordersPaid: number;
}

const FUNNEL_COLORS = ["#14776B", "#1FB5A3", "#2DD4BF", "#F59E0B", "#D97706"];
const DONUT_COLORS = ["#14776B", "#1FB5A3", "#F59E0B", "#D97706", "#9AA5B1"];

/** "▲ 2.1 pts" — for resolution time a decrease is the good direction. */
function deltaText(k: Kpi, unit: string) {
  if (k.delta == null) return "vs previous period: n/a";
  return `${k.delta >= 0 ? "▲" : "▼"} ${Math.abs(k.delta)}${unit}`;
}

export default function ReportsPage() {
  const [period, setPeriod] = useState("quarter");
  const [exporting, setExporting] = useState(false);
  const toast = useToast();
  const q = useQuery({ queryKey: ["reports", period], queryFn: () => api.get<Report>("/api/reports/summary", { params: { period } }).then((r) => r.data) });
  const r = q.data;

  const exportPdf = async () => {
    setExporting(true);
    try {
      await download("/api/reports/summary.pdf", { period }, "clientra-report.pdf");
    } catch (e) {
      toast(apiError(e).message, "error");
    } finally {
      setExporting(false);
    }
  };

  const maxFunnel = Math.max(1, ...(r?.funnel.map((f) => f.count) ?? [1]));

  return (
    <>
      <PageHeader
        title="Reports & Analytics"
        subtitle={r ? `${r.periodLabel} · ${r.rangeLabel}` : " "}
        actions={
          <>
            <Select className="h-10 w-40 text-[13.5px]" value={period} onChange={(e) => setPeriod(e.target.value)} aria-label="Period">
              <option value="quarter">This quarter</option>
              <option value="last-quarter">Last quarter</option>
              <option value="year">Last 12 months</option>
            </Select>
            <Button variant="outline" onClick={exportPdf} loading={exporting}>
              ⬇ Export PDF
            </Button>
          </>
        }
      />

      {q.isError && <ErrorState message={apiError(q.error).message} onRetry={() => q.refetch()} />}

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {!r ? (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[86px] rounded-2xl" />)
        ) : (
          <>
            <KpiCard label="Customer Retention" value={r.retention.value != null ? `${r.retention.value}%` : "—"} delta={deltaText(r.retention, " pts")} deltaTone={(r.retention.delta ?? 0) >= 0 ? "up" : "warn"} />
            <KpiCard label="Lead → Customer Conversion" value={r.conversion.value != null ? `${r.conversion.value}%` : "—"} delta={r.conversion.note} deltaTone="muted" />
            <KpiCard
              label="Avg. Ticket Resolution"
              value={r.avgResolutionHours.value != null ? `${r.avgResolutionHours.value} h` : "—"}
              delta={deltaText(r.avgResolutionHours, " h")}
              deltaTone={(r.avgResolutionHours.delta ?? 0) <= 0 ? "up" : "warn"}
            />
            <KpiCard label="SLA Compliance" value={r.slaCompliance.value != null ? `${r.slaCompliance.value}%` : "—"} delta={`${r.slaBreaches} breaches`} deltaTone="warn" />
          </>
        )}
      </div>

      <div className="mb-4 grid gap-4 lg:grid-cols-[1.35fr_1fr]">
        <Card className="px-5 py-4">
          <h2 className="mb-4 text-[15px] font-semibold">Customer Lifecycle Funnel</h2>
          {!r ? (
            <Skeleton className="h-44" />
          ) : (
            <ul className="space-y-3">
              {r.funnel.map((f, i) => (
                <li key={f.stage} className="grid grid-cols-[120px_1fr_44px] items-center gap-3 text-[13.5px]">
                  <span className="text-ink-700">{f.stage}</span>
                  <div className="h-[22px] rounded-md bg-sand-200">
                    <div className="flex h-full items-center rounded-md px-2.5 text-[12px] font-bold text-white" style={{ width: `${Math.max(8, (f.count / maxFunnel) * 100)}%`, background: FUNNEL_COLORS[i] }}>
                      {count(f.count)}
                    </div>
                  </div>
                  <span className="text-right text-ink-500">{Math.round(f.percent)}%</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="px-5 py-4">
          <h2 className="mb-2 text-[15px] font-semibold">Tickets by Category</h2>
          {!r ? (
            <Skeleton className="h-44" />
          ) : r.ticketTotal === 0 ? (
            <EmptyState icon="🎫" title="No tickets in this period" />
          ) : (
            <div className="flex items-center gap-4">
              <div className="relative h-[180px] w-[180px] shrink-0">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={r.ticketCategories} dataKey="count" nameKey="category" innerRadius={48} outerRadius={84} startAngle={90} endAngle={-270} strokeWidth={0}>
                      {r.ticketCategories.map((c, i) => (
                        <Cell key={c.category} fill={DONUT_COLORS[i % DONUT_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ borderRadius: 12, fontSize: 13 }} />
                  </PieChart>
                </ResponsiveContainer>
                <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-[22px] font-bold">{r.ticketTotal}</span>
              </div>
              <ul className="space-y-1.5 text-[13.5px] text-ink-700">
                {r.ticketCategories.map((c, i) => (
                  <li key={c.category} className="flex items-center gap-2">
                    <span className="h-2.5 w-2.5 rounded-full" style={{ background: DONUT_COLORS[i % DONUT_COLORS.length] }} />
                    {c.category} {Math.round(c.percent)}%
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-left">
          <thead>
            <tr className="border-b border-sand-200 bg-sand-50/60 text-[11.5px] font-semibold uppercase tracking-wide text-ink-500 [&>th]:px-4 [&>th]:py-3">
              <th>Sales Executive</th>
              <th>Customers Owned</th>
              <th>Interactions</th>
              <th>Orders Closed</th>
              <th>Revenue</th>
              <th>Conversion</th>
            </tr>
          </thead>
          <tbody>
            {r?.executives.map((e) => (
              <tr key={e.userId} className="border-b border-sand-200 text-[14px] last:border-0 [&>td]:px-4 [&>td]:py-3">
                <td className="font-semibold">{e.name}</td>
                <td>{count(e.customersOwned)}</td>
                <td>{count(e.interactions)}</td>
                <td>{count(e.ordersClosed)}</td>
                <td className="font-semibold">{moneyShort(e.revenue)}</td>
                <td>{Math.round(e.conversionPercent)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
        {r?.executives.length === 0 && <EmptyState title="No sales activity in this period" />}
      </Card>
    </>
  );
}
