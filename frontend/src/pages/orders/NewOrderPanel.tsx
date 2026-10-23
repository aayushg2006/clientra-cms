import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { money } from "@/lib/format";
import { useActiveProducts } from "@/lib/queries";
import type { Customer360, OrderDetail, PaymentStatus } from "@/lib/types";
import { Button, Field, Input, Select } from "@/components/ui";
import { CustomerPicker } from "@/components/CustomerPicker";
import { useToast } from "@/components/Toast";

interface Line {
  productId: number;
  quantity: number;
}

/** Right-hand "New Order" panel from the FA-II Orders screen. */
export function NewOrderPanel({ initialCustomerId, onClose, onCreated }: { initialCustomerId?: number; onClose: () => void; onCreated: (o: OrderDetail) => void }) {
  const products = useActiveProducts();
  const qc = useQueryClient();
  const toast = useToast();
  const [customerId, setCustomerId] = useState<number | null>(initialCustomerId ?? null);
  const [lines, setLines] = useState<Line[]>([]);
  const [adding, setAdding] = useState(false);
  const [pick, setPick] = useState<{ productId: string; quantity: string }>({ productId: "", quantity: "1" });
  const [payment, setPayment] = useState<PaymentStatus>("PENDING");
  const [error, setError] = useState<string | null>(null);

  const preset = useQuery({
    queryKey: ["customer", String(initialCustomerId)],
    queryFn: () => api.get<Customer360>(`/api/customers/${initialCustomerId}`).then((r) => r.data),
    enabled: !!initialCustomerId,
  });

  useEffect(() => setCustomerId(initialCustomerId ?? null), [initialCustomerId]);

  const byId = useMemo(() => new Map((products.data ?? []).map((p) => [p.id, p])), [products.data]);
  const rows = lines.map((l) => {
    const p = byId.get(l.productId);
    const total = (p?.unitPrice ?? 0) * l.quantity;
    const gst = p ? total - total / (1 + p.gstRate / 100) : 0;
    return { ...l, product: p, total, gst };
  });
  const subtotal = rows.reduce((s, r) => s + r.total, 0);
  const gst = rows.reduce((s, r) => s + r.gst, 0);

  const addLine = () => {
    const productId = Number(pick.productId);
    const quantity = Math.max(1, Math.min(10000, Number(pick.quantity) || 1));
    if (!productId) return;
    setLines((ls) => {
      const existing = ls.find((l) => l.productId === productId);
      return existing ? ls.map((l) => (l.productId === productId ? { ...l, quantity: l.quantity + quantity } : l)) : [...ls, { productId, quantity }];
    });
    setPick({ productId: "", quantity: "1" });
    setAdding(false);
  };

  const create = useMutation({
    mutationFn: () => api.post<OrderDetail>("/api/orders", { customerId, items: lines, paymentStatus: payment }).then((r) => r.data),
    onSuccess: (o) => {
      ["orders", "customers", "customer", "dashboard"].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      toast(`Order ${o.orderNumber} created`);
      onCreated(o);
    },
    onError: (e) => setError(apiError(e).message),
  });

  const submit = () => {
    setError(null);
    if (!customerId) return setError("Select a customer");
    if (!lines.length) return setError("Add at least one product");
    create.mutate();
  };

  return (
    <aside className="sticky top-5 rounded-2xl border border-sand-300/70 bg-white p-5 shadow-card">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold">New Order</h2>
        <button onClick={onClose} className="rounded-lg px-1.5 text-lg leading-none text-ink-500 hover:bg-sand-200" aria-label="Close">
          ×
        </button>
      </div>

      <Field label="Customer">
        <CustomerPicker value={customerId} onChange={(c) => setCustomerId(c?.id ?? null)} initialLabel={preset.data ? `${preset.data.fullName} · ${preset.data.customerCode}` : undefined} />
      </Field>

      <p className="mb-1.5 mt-4 text-[13px] font-semibold text-ink-700">Items</p>
      <div className="overflow-hidden rounded-xl border border-sand-300">
        {rows.length === 0 && <p className="px-3 py-3 text-[13px] text-ink-500">No products added yet</p>}
        {rows.map((r) => (
          <div key={r.productId} className="group flex items-center justify-between border-b border-sand-200 px-3 py-2.5 text-[13.5px] last:border-0">
            <span>
              {r.product?.name} <span className="text-ink-500">× {r.quantity}</span>
            </span>
            <span className="flex items-center gap-2">
              <b>{money(r.total)}</b>
              <button onClick={() => setLines((ls) => ls.filter((l) => l.productId !== r.productId))} className="text-ink-400 opacity-0 hover:text-red-600 group-hover:opacity-100" aria-label="Remove line">
                ×
              </button>
            </span>
          </div>
        ))}
      </div>

      {adding ? (
        <div className="mt-2 flex gap-2">
          <Select className="h-10 flex-1 text-[13px]" value={pick.productId} onChange={(e) => setPick((p) => ({ ...p, productId: e.target.value }))}>
            <option value="">Choose product…</option>
            {products.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} — {money(p.unitPrice)}
              </option>
            ))}
          </Select>
          <Input className="h-10 w-16 px-2 text-center" type="number" min={1} value={pick.quantity} onChange={(e) => setPick((p) => ({ ...p, quantity: e.target.value }))} />
          <Button size="sm" className="h-10" onClick={addLine} disabled={!pick.productId}>
            Add
          </Button>
        </div>
      ) : (
        <button onClick={() => setAdding(true)} className="mt-2 text-[13.5px] font-semibold text-teal-700 hover:underline">
          + Add product
        </button>
      )}

      <div className="mt-4 space-y-1 rounded-xl bg-sand-100 px-3 py-3 text-[13.5px]">
        <div className="flex justify-between text-ink-700">
          <span>Subtotal</span>
          <span>{money(subtotal)}</span>
        </div>
        <div className="flex justify-between text-ink-500">
          <span>GST (included)</span>
          <span>{money(gst)}</span>
        </div>
        <div className="flex justify-between pt-1 text-base font-bold">
          <span>Total</span>
          <span>{money(subtotal)}</span>
        </div>
      </div>

      <Field label="Payment Status" className="mt-4">
        <Select value={payment} onChange={(e) => setPayment(e.target.value as PaymentStatus)}>
          <option value="PENDING">Pending</option>
          <option value="PAID">Paid</option>
        </Select>
      </Field>

      {error && <p className="mt-3 rounded-xl bg-red-50 px-3 py-2 text-[13px] text-red-700">{error}</p>}

      <Button className="mt-4 h-11 w-full" onClick={submit} loading={create.isPending}>
        Create Order
      </Button>
      <p className="mt-2 text-center text-[12px] text-ink-500">Lifetime value updates automatically when marked Paid</p>
    </aside>
  );
}
