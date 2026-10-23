import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { dateTime, money } from "@/lib/format";
import { ORDER_STATUS, PAYMENT_STATUS } from "@/lib/status";
import type { OrderDetail, OrderStatus, PaymentStatus } from "@/lib/types";
import { Button, Drawer, Pill, Skeleton } from "@/components/ui";
import { useToast } from "@/components/Toast";

const NEXT: Record<OrderStatus, OrderStatus[]> = {
  CONFIRMED: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED", "CANCELLED"],
  DELIVERED: [],
  CANCELLED: [],
};

export function OrderDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({ queryKey: ["orders", "detail", id], queryFn: () => api.get<OrderDetail>(`/api/orders/${id}`).then((r) => r.data), enabled: !!id });

  const refresh = () => ["orders", "customers", "customer", "dashboard"].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
  const pay = useMutation({
    mutationFn: (s: PaymentStatus) => api.patch(`/api/orders/${id}/payment`, { paymentStatus: s }),
    onSuccess: (_, s) => {
      refresh();
      toast(s === "PAID" ? "Marked paid — lifetime value updated" : "Order refunded");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });
  const status = useMutation({
    mutationFn: (s: OrderStatus) => api.patch(`/api/orders/${id}/status`, { status: s }),
    onSuccess: () => {
      refresh();
      toast("Order status updated");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  const o = q.data;
  return (
    <Drawer open={!!id} onClose={onClose} title={o?.orderNumber ?? "Order"} width={380}>
      {!o ? (
        <Skeleton className="h-60 w-full" />
      ) : (
        <div className="space-y-4 text-[13.5px]">
          <div className="flex gap-1.5">
            <Pill tone={PAYMENT_STATUS[o.paymentStatus].tone}>{PAYMENT_STATUS[o.paymentStatus].label}</Pill>
            <Pill tone={ORDER_STATUS[o.status].tone}>{ORDER_STATUS[o.status].label}</Pill>
          </div>
          <dl className="grid grid-cols-[100px_1fr] gap-y-1.5">
            <dt className="text-ink-500">Customer</dt>
            <dd>
              <Link to={`/customers/${o.customerId}`} className="font-semibold text-teal-700 hover:underline" onClick={onClose}>
                {o.customerName}
              </Link>{" "}
              <span className="text-ink-500">· {o.customerCode}</span>
            </dd>
            <dt className="text-ink-500">Placed</dt>
            <dd>{dateTime(o.orderDate)}</dd>
            <dt className="text-ink-500">Created by</dt>
            <dd>{o.createdBy?.name}</dd>
            {o.paidAt && (
              <>
                <dt className="text-ink-500">Paid</dt>
                <dd>{dateTime(o.paidAt)}</dd>
              </>
            )}
          </dl>
          <div className="overflow-hidden rounded-xl border border-sand-300">
            {o.items.map((i) => (
              <div key={i.id} className="flex justify-between border-b border-sand-200 px-3 py-2 last:border-0">
                <span>
                  {i.productName} <span className="text-ink-500">× {i.quantity}</span>
                </span>
                <b>{money(i.lineTotal)}</b>
              </div>
            ))}
          </div>
          <div className="space-y-1 rounded-xl bg-sand-100 px-3 py-3">
            <div className="flex justify-between text-ink-500">
              <span>GST (included)</span>
              <span>{money(o.taxAmount)}</span>
            </div>
            <div className="flex justify-between text-base font-bold">
              <span>Total</span>
              <span>{money(o.totalAmount)}</span>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {o.paymentStatus === "PENDING" && o.status !== "CANCELLED" && (
              <Button onClick={() => pay.mutate("PAID")} loading={pay.isPending}>
                ✓ Mark Paid
              </Button>
            )}
            {o.paymentStatus === "PAID" && (
              <Button variant="outline" onClick={() => confirm(`Refund ${money(o.totalAmount)}? Lifetime value will be reduced.`) && pay.mutate("REFUNDED")} loading={pay.isPending}>
                ↩ Refund
              </Button>
            )}
            {NEXT[o.status]
              .filter((s) => !(s === "CANCELLED" && o.paymentStatus === "PAID"))
              .map((s) => (
                <Button key={s} variant={s === "CANCELLED" ? "ghost" : "outline"} onClick={() => status.mutate(s)} loading={status.isPending && status.variables === s}>
                  {s === "CANCELLED" ? "Cancel order" : `Mark ${ORDER_STATUS[s].label}`}
                </Button>
              ))}
          </div>
        </div>
      )}
    </Drawer>
  );
}
