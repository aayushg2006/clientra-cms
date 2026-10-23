import clsx from "clsx";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { money } from "@/lib/format";
import type { Product } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, Card, EmptyState, Field, Input, KpiCard, Modal, Pill, Skeleton } from "@/components/ui";
import { useToast } from "@/components/Toast";

const schema = z.object({
  sku: z.string().trim().min(1, "SKU is required").max(40).regex(/^[A-Za-z0-9-]+$/, "Letters, numbers and dashes only"),
  name: z.string().trim().min(1, "Name is required").max(150),
  category: z.string().trim().max(80).optional(),
  unitPrice: z.coerce.number({ invalid_type_error: "Enter a price" }).positive("Price must be positive"),
  gstRate: z.coerce.number().min(0).max(28, "GST cannot exceed 28%"),
});
type Form = z.infer<typeof schema>;

export default function ProductsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const qc = useQueryClient();
  const toast = useToast();
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<Product | "new" | null>(null);

  const list = useQuery({ queryKey: ["products", "all"], queryFn: () => api.get<Product[]>("/api/products").then((r) => r.data) });
  const toggle = useMutation({
    mutationFn: (p: Product) => api.patch(`/api/products/${p.id}/active`, { active: !p.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["products"] }),
    onError: (e) => toast(apiError(e).message, "error"),
  });

  const rows = (list.data ?? []).filter((p) => !q || `${p.name} ${p.sku} ${p.category}`.toLowerCase().includes(q.toLowerCase()));
  const active = list.data?.filter((p) => p.active).length ?? 0;
  const categories = new Set(list.data?.map((p) => p.category).filter(Boolean)).size;

  return (
    <>
      <PageHeader
        title="Products"
        subtitle={`${list.data?.length ?? 0} products in the catalogue`}
        actions={isAdmin && <Button onClick={() => setEditing("new")}>+ Add Product</Button>}
      />
      <div className="mb-4 grid grid-cols-3 gap-3">
        <KpiCard label="Active products" value={active} />
        <KpiCard label="Categories" value={categories} />
        <KpiCard label="Inactive" value={(list.data?.length ?? 0) - active} deltaTone="muted" delta="Hidden from new orders" />
      </div>
      <Card className="overflow-hidden">
        <div className="border-b border-sand-200 px-4 py-3">
          <Input className="h-9 max-w-xs" placeholder="Filter by name, SKU or category" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <table className="w-full text-left">
          <thead>
            <tr className="border-b border-sand-200 bg-sand-50/60 text-[11.5px] font-semibold uppercase tracking-wide text-ink-500 [&>th]:px-4 [&>th]:py-3">
              <th>SKU</th>
              <th>Product</th>
              <th>Category</th>
              <th>Unit price</th>
              <th>GST</th>
              <th>Status</th>
              {isAdmin && <th className="text-right">Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.isLoading && (
              <tr>
                <td colSpan={7} className="p-4">
                  <Skeleton className="h-24" />
                </td>
              </tr>
            )}
            {rows.map((p) => (
              <tr key={p.id} className={clsx("border-b border-sand-200 text-[14px] last:border-0 [&>td]:px-4 [&>td]:py-2.5", !p.active && "text-ink-400")}>
                <td className="font-mono text-[12.5px]">{p.sku}</td>
                <td className="font-semibold">{p.name}</td>
                <td>{p.category ?? "—"}</td>
                <td className="font-semibold">{money(p.unitPrice)}</td>
                <td>{p.gstRate}%</td>
                <td>
                  <Pill tone={p.active ? "green" : "gray"}>{p.active ? "Active" : "Inactive"}</Pill>
                </td>
                {isAdmin && (
                  <td className="text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(p)}>
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => toggle.mutate(p)}>
                      {p.active ? "Deactivate" : "Activate"}
                    </Button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {list.data && rows.length === 0 && <EmptyState icon="📦" title="No products found" />}
      </Card>
      <ProductModal product={editing} onClose={() => setEditing(null)} />
    </>
  );
}

function ProductModal({ product, onClose }: { product: Product | "new" | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const existing = product && product !== "new" ? product : null;
  const { register, handleSubmit, reset, setError, formState } = useForm<Form>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (product) reset(existing ? { sku: existing.sku, name: existing.name, category: existing.category ?? "", unitPrice: existing.unitPrice, gstRate: existing.gstRate } : { sku: "", name: "", category: "", unitPrice: undefined, gstRate: 5 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product]);

  const save = useMutation({
    mutationFn: (f: Form) => (existing ? api.put(`/api/products/${existing.id}`, f) : api.post("/api/products", f)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["products"] });
      toast(existing ? "Product updated" : "Product added");
      onClose();
    },
    onError: (e) => {
      const err = apiError(e);
      if (err.fieldErrors) Object.entries(err.fieldErrors).forEach(([k, v]) => setError(k as keyof Form, { message: v }));
      else setError("sku", { message: err.message });
    },
  });

  return (
    <Modal open={!!product} onClose={onClose} title={existing ? "Edit product" : "Add product"} width={520}>
      <form onSubmit={handleSubmit((f) => save.mutate(f))} className="grid grid-cols-2 gap-3.5" noValidate>
        <Field label="SKU *" error={formState.errors.sku?.message}>
          <Input {...register("sku")} placeholder="GRN-BAS-25" />
        </Field>
        <Field label="Category">
          <Input {...register("category")} placeholder="Grains" />
        </Field>
        <Field label="Name *" error={formState.errors.name?.message} className="col-span-2">
          <Input {...register("name")} />
        </Field>
        <Field label="Unit price (₹) *" error={formState.errors.unitPrice?.message}>
          <Input type="number" step="0.01" {...register("unitPrice")} />
        </Field>
        <Field label="GST rate (%)" error={formState.errors.gstRate?.message}>
          <Input type="number" step="0.01" {...register("gstRate")} />
        </Field>
        <div className="col-span-2 flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  );
}
