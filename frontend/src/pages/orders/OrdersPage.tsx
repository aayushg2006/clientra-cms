import clsx from "clsx";
import { useSearchParams } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { count, money, moneyShort, shortDate } from "@/lib/format";
import { ORDER_STATUS, PAYMENT_STATUS } from "@/lib/status";
import type { OrderSummary, Page, PaymentStatus } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Pagination } from "@/components/Pagination";
import { Button, Card, EmptyState, ErrorState, KpiCard, Pill, Segmented, Skeleton } from "@/components/ui";
import { NewOrderPanel } from "./NewOrderPanel";
import { OrderDrawer } from "./OrderDrawer";

interface OrderKpis {
  paid: number;
  pending: number;
  averageOrderValue: number;
  ordersThisQuarter: number;
  revenueThisQuarter: number;
}

const PAGE_SIZE = 15;
type PayFilter = "ALL" | PaymentStatus;

export default function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const newFor = params.get("new");
  const openId = params.get("open");
  const page = Number(params.get("page") ?? 0);
  const payFilter = (params.get("payment") ?? "ALL") as PayFilter;
  const panelOpen = newFor !== null;

  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v === null ? next.delete(k) : next.set(k, v)));
    setParams(next, { replace: true });
  };

  const kpis = useQuery({ queryKey: ["orders", "kpis"], queryFn: () => api.get<OrderKpis>("/api/orders/kpis").then((r) => r.data) });
  const list = useQuery({
    queryKey: ["orders", "list", page, payFilter],
    queryFn: () =>
      api
        .get<Page<OrderSummary>>("/api/orders", { params: { page, size: PAGE_SIZE, paymentStatus: payFilter === "ALL" ? undefined : payFilter } })
        .then((r) => r.data),
    placeholderData: keepPreviousData,
  });

  return (
    <div className={clsx("grid gap-5", panelOpen ? "lg:grid-cols-[1fr_300px]" : "grid-cols-1")}>
      <div className="min-w-0">
        <PageHeader
          title="Orders"
          subtitle={kpis.data ? `${count(kpis.data.ordersThisQuarter)} orders this quarter · ${moneyShort(kpis.data.revenueThisQuarter)} revenue` : " "}
          actions={!panelOpen && <Button onClick={() => set({ new: "" })}>+ New Order</Button>}
        />

        <div className="mb-4 grid grid-cols-3 gap-3">
          <KpiCard label="Paid" value={<span className="text-[#1F7A3F]">{moneyShort(kpis.data?.paid)}</span>} />
          <KpiCard label="Pending payment" value={<span className="text-[#B4520E]">{moneyShort(kpis.data?.pending)}</span>} />
          <KpiCard label="Avg. order value" value={money(kpis.data?.averageOrderValue)} />
        </div>

        <div className="mb-3">
          <Segmented<PayFilter>
            value={payFilter}
            onChange={(v) => set({ payment: v === "ALL" ? null : v, page: null })}
            options={[
              { value: "ALL", label: "All" },
              { value: "PENDING", label: "Pending" },
              { value: "PAID", label: "Paid" },
              { value: "REFUNDED", label: "Refunded" },
            ]}
          />
        </div>

        <Card className="overflow-x-auto">
          {list.isError ? (
            <ErrorState message={apiError(list.error).message} onRetry={() => list.refetch()} />
          ) : (
            <table className={clsx("w-full text-left", panelOpen && "[&_td]:!px-2 [&_th]:!px-2 [&_td]:!text-[12.5px]")}>
              <thead>
                <tr className="border-b border-sand-200 bg-sand-50/60 text-[11.5px] font-semibold uppercase tracking-wide text-ink-500 [&>th]:whitespace-nowrap [&>th]:px-3 [&>th]:py-3">
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Date</th>
                  <th>Items</th>
                  <th>Total</th>
                  <th>Payment</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody className={clsx(list.isPlaceholderData && "opacity-60")}>
                {list.isLoading &&
                  Array.from({ length: 6 }).map((_, i) => (
                    <tr key={i}>
                      <td colSpan={7} className="px-4 py-2.5">
                        <Skeleton className="h-6" />
                      </td>
                    </tr>
                  ))}
                {list.data?.content.map((o) => (
                  <tr key={o.id} onClick={() => set({ open: String(o.id) })} className="cursor-pointer border-b border-sand-200 text-[14px] last:border-0 hover:bg-sand-50 [&>td]:whitespace-nowrap [&>td]:px-3 [&>td]:py-[11px]">
                    <td className="font-bold">{o.orderNumber}</td>
                    <td className="text-ink-700">{o.customerName}</td>
                    <td className="text-ink-500">{shortDate(o.orderDate)}</td>
                    <td>{o.itemCount}</td>
                    <td className="font-semibold">{money(o.totalAmount)}</td>
                    <td>
                      <Pill tone={PAYMENT_STATUS[o.paymentStatus].tone}>{PAYMENT_STATUS[o.paymentStatus].label}</Pill>
                    </td>
                    <td>
                      <Pill tone={ORDER_STATUS[o.status].tone}>{ORDER_STATUS[o.status].label}</Pill>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {list.data?.totalElements === 0 && <EmptyState icon="🧾" title="No orders" />}
          {!!list.data?.totalElements && <Pagination page={page} size={PAGE_SIZE} total={list.data.totalElements} onChange={(p) => set({ page: String(p) })} />}
        </Card>
      </div>

      {panelOpen && (
        <NewOrderPanel
          initialCustomerId={newFor ? Number(newFor) : undefined}
          onClose={() => set({ new: null })}
          onCreated={(o) => set({ new: null, open: String(o.id), page: null })}
        />
      )}
      <OrderDrawer id={openId ? Number(openId) : null} onClose={() => set({ open: null })} />
    </div>
  );
}
