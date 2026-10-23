import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import type { InteractionType } from "@/lib/types";
import { INTERACTION_ICON, INTERACTION_LABEL } from "@/lib/status";
import { Button, Field, Input, Modal, Select, Textarea } from "@/components/ui";
import { CustomerPicker } from "@/components/CustomerPicker";
import { useToast } from "@/components/Toast";

const schema = z
  .object({
    customerId: z.number({ required_error: "Customer is required", invalid_type_error: "Customer is required" }),
    type: z.enum(["CALL", "EMAIL", "MEETING", "NOTE", "DEMO", "QUOTE"]),
    subject: z.string().trim().min(1, "Subject is required").max(200),
    notes: z.string().max(4000).optional(),
    interactionAt: z.string().min(1, "When did it happen?"),
    followUpAt: z.string().optional(),
  })
  .refine((v) => !v.followUpAt || new Date(v.followUpAt) > new Date(v.interactionAt), { path: ["followUpAt"], message: "Follow-up must be after the interaction" });

type Form = z.infer<typeof schema>;

/** yyyy-MM-ddTHH:mm in local time for datetime-local inputs. */
export const localInput = (d = new Date()) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);

export function LogInteractionModal({ open, onClose, customerId, customerLabel, type = "CALL" }: { open: boolean; onClose: () => void; customerId?: number; customerLabel?: string; type?: InteractionType }) {
  const qc = useQueryClient();
  const toast = useToast();
  const { register, handleSubmit, control, reset, formState } = useForm<Form>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (open) reset({ customerId: customerId, type, subject: "", notes: "", interactionAt: localInput(), followUpAt: "" });
  }, [open, customerId, type, reset]);

  const save = useMutation({
    mutationFn: (f: Form) =>
      api.post("/api/interactions", {
        customerId: f.customerId,
        type: f.type,
        subject: f.subject,
        notes: f.notes || null,
        interactionAt: new Date(f.interactionAt).toISOString(),
        followUpAt: f.followUpAt ? new Date(f.followUpAt).toISOString() : null,
      }),
    onSuccess: () => {
      ["customer", "customers", "interactions", "dashboard"].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      toast("Interaction logged");
      onClose();
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  return (
    <Modal open={open} onClose={onClose} title="Log interaction" subtitle="Calls, emails, meetings and notes appear on the customer timeline" width={560}>
      <form onSubmit={handleSubmit((f) => save.mutate(f))} className="space-y-3.5" noValidate>
        {!customerId && (
          <Field label="Customer *" error={formState.errors.customerId?.message}>
            <Controller control={control} name="customerId" render={({ field }) => <CustomerPicker value={field.value ?? null} onChange={(c) => field.onChange(c?.id)} invalid={!!formState.errors.customerId} initialLabel={customerLabel} />} />
          </Field>
        )}
        <div className="grid grid-cols-[160px_1fr] gap-3">
          <Field label="Type">
            <Select {...register("type")}>
              {(Object.keys(INTERACTION_LABEL) as InteractionType[]).map((t) => (
                <option key={t} value={t}>
                  {INTERACTION_ICON[t]} {INTERACTION_LABEL[t]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Subject *" error={formState.errors.subject?.message}>
            <Input placeholder="e.g. Quarterly bulk order discussion" {...register("subject")} invalid={!!formState.errors.subject} />
          </Field>
        </div>
        <Field label="Notes">
          <Textarea rows={3} placeholder="What was discussed, what was promised…" {...register("notes")} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="When" error={formState.errors.interactionAt?.message}>
            <Input type="datetime-local" {...register("interactionAt")} />
          </Field>
          <Field label="Follow-up (optional)" error={formState.errors.followUpAt?.message} hint="Shows on the dashboard on the due day">
            <Input type="datetime-local" {...register("followUpAt")} />
          </Field>
        </div>
        <div className="flex justify-end gap-2 pt-2">
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
