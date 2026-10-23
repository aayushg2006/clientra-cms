import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { money, shortDate } from "@/lib/format";
import { useUsers } from "@/lib/queries";
import { CHANNEL_LABEL } from "@/lib/status";
import type { OrderSummary, Page, TicketChannel, TicketDetail } from "@/lib/types";
import { Button, Field, Input, Modal, Select, Textarea } from "@/components/ui";
import { CustomerPicker } from "@/components/CustomerPicker";
import { useToast } from "@/components/Toast";

const schema = z.object({
  customerId: z.number({ required_error: "Customer is required", invalid_type_error: "Customer is required" }),
  orderId: z.string(),
  subject: z.string().trim().min(1, "Subject is required").max(200),
  description: z.string().max(4000).optional(),
  channel: z.enum(["EMAIL", "PHONE", "CHAT", "WEB", "WALK_IN"]),
  category: z.enum(["DELIVERY", "PRODUCT_QUALITY", "BILLING", "ACCOUNT", "OTHER"]),
  priority: z.enum(["LOW", "MEDIUM", "HIGH"]),
  assigneeId: z.string(),
});
type Form = z.infer<typeof schema>;

export function NewTicketModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (t: TicketDetail) => void }) {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const agents = useUsers("SUPPORT_AGENT");
  const qc = useQueryClient();
  const toast = useToast();
  const { register, handleSubmit, control, watch, reset, formState } = useForm<Form>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (open) reset({ customerId: undefined, orderId: "", subject: "", description: "", channel: "EMAIL", category: "DELIVERY", priority: "MEDIUM", assigneeId: "" });
  }, [open, reset]);

  const customerId = watch("customerId");
  const priority = watch("priority");
  // Orders are visible to admins only (support agents cannot read the orders API)
  const orders = useQuery({
    queryKey: ["orders", "for-customer", customerId],
    queryFn: () => api.get<Page<OrderSummary>>("/api/orders", { params: { customerId, size: 10 } }).then((r) => r.data.content),
    enabled: !!customerId && isAdmin,
  });

  const save = useMutation({
    mutationFn: (f: Form) =>
      api
        .post<TicketDetail>("/api/tickets", {
          ...f,
          orderId: f.orderId ? Number(f.orderId) : null,
          assigneeId: f.assigneeId ? Number(f.assigneeId) : null,
          description: f.description || null,
        })
        .then((r) => r.data),
    onSuccess: (t) => {
      qc.invalidateQueries({ queryKey: ["tickets"] });
      qc.invalidateQueries({ queryKey: ["nav"] });
      toast(`${t.ticketNumber} created${t.assignee ? ` and assigned to ${t.assignee.name}` : ""}`);
      onCreated(t);
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  const slaHours = { HIGH: 8, MEDIUM: 24, LOW: 48 }[priority ?? "MEDIUM"];

  return (
    <Modal open={open} onClose={onClose} title="New Ticket" subtitle={`SLA is set from priority — this one will be due in ${slaHours}h`} width={600}>
      <form onSubmit={handleSubmit((f) => save.mutate(f))} className="grid grid-cols-2 gap-3.5" noValidate>
        <Field label="Customer *" error={formState.errors.customerId?.message} className="col-span-2">
          <Controller control={control} name="customerId" render={({ field }) => <CustomerPicker value={field.value ?? null} onChange={(c) => field.onChange(c?.id)} invalid={!!formState.errors.customerId} />} />
        </Field>
        {isAdmin && (
          <Field label="Related order" className="col-span-2">
            <Select {...register("orderId")} disabled={!customerId}>
              <option value="">None</option>
              {orders.data?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.orderNumber} · {shortDate(o.orderDate)} · {money(o.totalAmount)}
                </option>
              ))}
            </Select>
          </Field>
        )}
        <Field label="Subject *" error={formState.errors.subject?.message} className="col-span-2">
          <Input placeholder="e.g. Wrong shade delivered (3 rolls)" {...register("subject")} invalid={!!formState.errors.subject} />
        </Field>
        <Field label="Description" className="col-span-2">
          <Textarea rows={3} placeholder="What the customer reported" {...register("description")} />
        </Field>
        <Field label="Channel">
          <Select {...register("channel")}>
            {(Object.keys(CHANNEL_LABEL) as TicketChannel[]).map((c) => (
              <option key={c} value={c}>
                {CHANNEL_LABEL[c]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Category">
          <Select {...register("category")}>
            <option value="DELIVERY">Delivery</option>
            <option value="PRODUCT_QUALITY">Product quality</option>
            <option value="BILLING">Billing</option>
            <option value="ACCOUNT">Account</option>
            <option value="OTHER">Other</option>
          </Select>
        </Field>
        <Field label="Priority">
          <Select {...register("priority")}>
            <option value="LOW">Low · 48h</option>
            <option value="MEDIUM">Medium · 24h</option>
            <option value="HIGH">High · 8h</option>
          </Select>
        </Field>
        <Field label="Assignee">
          <Select {...register("assigneeId")} disabled={!isAdmin}>
            <option value="">Auto-assign (least loaded)</option>
            {isAdmin &&
              agents.data?.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.fullName}
                </option>
              ))}
          </Select>
        </Field>
        <div className="col-span-2 flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Create Ticket
          </Button>
        </div>
      </form>
    </Modal>
  );
}
