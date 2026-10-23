import clsx from "clsx";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTags, useUsers } from "@/lib/queries";
import type { Customer360, CustomerSummary, DuplicateCheck } from "@/lib/types";
import { Button, Field, Input, Modal, Select } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { TagInput } from "./TagInput";

const schema = z
  .object({
    customerType: z.enum(["INDIVIDUAL", "BUSINESS"]),
    firstName: z.string().trim().min(1, "First name is required").max(60),
    lastName: z.string().trim().min(1, "Last name is required").max(60),
    email: z.string().trim().min(1, "Email is required").email("Enter a valid email"),
    phone: z
      .string()
      .trim()
      .min(1, "Phone is required")
      .refine((v) => v.replace(/[^0-9]/g, "").length >= 10, "Enter a valid 10-digit phone number"),
    companyName: z.string().trim().max(150).optional(),
    jobTitle: z.string().trim().max(100).optional(),
    segment: z.enum(["RETAIL", "SMB", "ENTERPRISE", "OTHER"]),
    source: z.enum(["WEBSITE", "REFERRAL", "WALK_IN", "CAMPAIGN", "OTHER"]),
    assignedTo: z.string(),
    billing: z.string().trim().max(200).optional(),
    city: z.string().trim().max(100).optional(),
    state: z.string().trim().max(100).optional(),
    postalCode: z
      .string()
      .trim()
      .regex(/^$|^[0-9A-Za-z -]{3,10}$/, "Enter a valid postal code")
      .optional(),
    tags: z.array(z.string()),
    sendWelcomeEmail: z.boolean(),
  })
  .refine((v) => v.customerType === "INDIVIDUAL" || !!v.companyName, { path: ["companyName"], message: "Company name is required for businesses" });

type Form = z.infer<typeof schema>;

const SOURCES = [
  ["WEBSITE", "Website Form"],
  ["REFERRAL", "Referral"],
  ["WALK_IN", "Walk-in"],
  ["CAMPAIGN", "Campaign"],
  ["OTHER", "Other"],
] as const;

export function CustomerFormModal({ open, onClose, customer, onSaved }: { open: boolean; onClose: () => void; customer?: Customer360; onSaved?: (c: CustomerSummary | Customer360) => void }) {
  const { user } = useAuth();
  const users = useUsers();
  const tags = useTags();
  const qc = useQueryClient();
  const toast = useToast();
  const [dup, setDup] = useState<DuplicateCheck | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const editing = !!customer;

  const billing = customer?.addresses.find((a) => a.addressType === "BILLING") ?? customer?.addresses[0];
  const defaults: Form = {
    customerType: customer?.customerType ?? "BUSINESS",
    firstName: customer?.firstName ?? "",
    lastName: customer?.lastName ?? "",
    email: customer?.email ?? "",
    phone: customer?.phone ?? "",
    companyName: customer?.companyName ?? "",
    jobTitle: customer?.jobTitle ?? "",
    segment: customer?.segment ?? "SMB",
    source: customer?.source ?? "WEBSITE",
    assignedTo: String(customer?.owner?.id ?? user?.id ?? ""),
    billing: billing?.line1 ?? "",
    city: billing?.city ?? "",
    state: billing?.state ?? "",
    postalCode: billing?.postalCode ?? "",
    tags: customer?.tags.map((t) => t.name) ?? [],
    sendWelcomeEmail: !editing,
  };

  const { register, handleSubmit, control, watch, reset, setError, formState } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: defaults });

  useEffect(() => {
    if (open) {
      reset(defaults);
      setDup(null);
      setFormError(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, customer?.id]);

  const type = watch("customerType");

  const checkDuplicate = async (field: "email" | "phone", value: string) => {
    if (!value || (field === "email" && !value.includes("@"))) return;
    const { data } = await api.get<DuplicateCheck>("/api/customers/check-duplicate", { params: { [field]: value, excludeId: customer?.id } });
    setDup((prev) => (data.duplicate ? data : prev?.field === field ? null : prev));
  };

  const save = useMutation({
    mutationFn: (f: Form) => {
      const body = {
        firstName: f.firstName,
        lastName: f.lastName,
        email: f.email,
        phone: f.phone,
        companyName: f.customerType === "BUSINESS" ? f.companyName : f.companyName || null,
        jobTitle: f.jobTitle || null,
        customerType: f.customerType,
        segment: f.segment,
        source: f.source,
        assignedTo: f.assignedTo ? Number(f.assignedTo) : null,
        addresses: f.billing ? [{ addressType: "BILLING", line1: f.billing, city: f.city || null, state: f.state || null, postalCode: f.postalCode || null, country: "India", primary: true }] : [],
        tags: f.tags,
        sendWelcomeEmail: f.sendWelcomeEmail,
        version: customer?.version,
      };
      return editing ? api.put<Customer360>(`/api/customers/${customer.id}`, body).then((r) => r.data) : api.post<CustomerSummary>("/api/customers", body).then((r) => r.data);
    },
    onSuccess: (c) => {
      qc.invalidateQueries({ queryKey: ["customers"] });
      qc.invalidateQueries({ queryKey: ["customer"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      toast(editing ? "Customer updated" : `Customer ${c.customerCode} created`);
      onSaved?.(c);
      onClose();
    },
    onError: (e) => {
      const err = apiError(e);
      if (err.error === "DUPLICATE_CUSTOMER") {
        const field = err.fieldErrors?.phone ? "phone" : "email";
        setDup({ duplicate: true, field, existingId: err.existingId, existingCode: err.existingCode });
      } else if (err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([k, v]) => setError(k as keyof Form, { message: v }));
      } else {
        setFormError(err.message);
      }
    },
  });

  const dupMessage = (field: "email" | "phone") =>
    dup?.field === field ? (
      <span className="mt-1 flex items-center gap-1 text-xs font-medium text-red-700">
        ⚠ A customer with this {field} already exists —{" "}
        <Link to={`/customers/${dup.existingId}`} onClick={onClose} className="underline">
          {dup.existingCode}
        </Link>
      </span>
    ) : null;

  return (
    <Modal open={open} onClose={onClose} title={editing ? "Edit Customer" : "Add New Customer"} subtitle="Fields marked * are required" width={650}>
      <form onSubmit={handleSubmit((f) => save.mutate(f))} noValidate>
        <Controller
          control={control}
          name="customerType"
          render={({ field }) => (
            <div className="mb-4 grid grid-cols-2 gap-3">
              {(
                [
                  ["INDIVIDUAL", "👤 Individual"],
                  ["BUSINESS", "🏢 Business"],
                ] as const
              ).map(([v, label]) => (
                <button
                  type="button"
                  key={v}
                  onClick={() => field.onChange(v)}
                  className={clsx(
                    "h-[38px] rounded-xl border-[1.5px] px-3 text-left text-sm font-semibold transition",
                    field.value === v ? "border-teal-700 bg-teal-50 text-teal-700" : "border-sand-300 bg-white text-ink-900 hover:bg-sand-50",
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          )}
        />

        <div className="grid grid-cols-2 gap-x-3 gap-y-3.5">
          <Field label="First Name *" error={formState.errors.firstName?.message}>
            <Input {...register("firstName")} invalid={!!formState.errors.firstName} />
          </Field>
          <Field label="Last Name *" error={formState.errors.lastName?.message}>
            <Input {...register("lastName")} invalid={!!formState.errors.lastName} />
          </Field>
          <div>
            <Field label="Email *" error={formState.errors.email?.message}>
              <Input type="email" {...register("email", { onBlur: (e) => checkDuplicate("email", e.target.value) })} invalid={!!formState.errors.email || dup?.field === "email"} />
            </Field>
            {dupMessage("email")}
          </div>
          <div>
            <Field label="Phone *" error={formState.errors.phone?.message}>
              <Input placeholder="+91 99300 12000" {...register("phone", { onBlur: (e) => checkDuplicate("phone", e.target.value) })} invalid={!!formState.errors.phone || dup?.field === "phone"} />
            </Field>
            {dupMessage("phone")}
          </div>
          <Field label={`Company Name${type === "BUSINESS" ? " *" : ""}`} error={formState.errors.companyName?.message}>
            <Input {...register("companyName")} invalid={!!formState.errors.companyName} />
          </Field>
          <Field label="Segment">
            <Select {...register("segment")}>
              <option value="RETAIL">Retail</option>
              <option value="SMB">SMB</option>
              <option value="ENTERPRISE">Enterprise</option>
              <option value="OTHER">Other</option>
            </Select>
          </Field>
          <Field label="Source">
            <Select {...register("source")}>
              {SOURCES.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Assign Owner">
            <Select {...register("assignedTo")}>
              {users.data
                ?.filter((u) => u.role !== "SUPPORT_AGENT" || String(u.id) === defaults.assignedTo)
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName}
                  </option>
                ))}
            </Select>
          </Field>
          {type === "BUSINESS" && (
            <Field label="Job Title" className="col-span-2">
              <Input placeholder="e.g. Purchase Head" {...register("jobTitle")} />
            </Field>
          )}
          <Field label="Billing Address" className="col-span-2">
            <Input placeholder="Shop 7, Sector 17, Vashi" {...register("billing")} />
          </Field>
          <div className="col-span-2 grid grid-cols-3 gap-3">
            <Input placeholder="City" {...register("city")} />
            <Input placeholder="State" {...register("state")} />
            <Field error={formState.errors.postalCode?.message}>
              <Input placeholder="PIN code" {...register("postalCode")} />
            </Field>
          </div>
          <Field label="Tags" className="col-span-2">
            <Controller control={control} name="tags" render={({ field }) => <TagInput value={field.value} onChange={field.onChange} options={tags.data ?? []} />} />
          </Field>
        </div>

        {formError && <p className="mt-4 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>}

        <div className="mt-5 flex items-center justify-between">
          {!editing ? (
            <label className="flex cursor-pointer items-center gap-1.5 text-[13.5px] text-ink-700">
              <input type="checkbox" className="h-3.5 w-3.5 accent-blue-600" {...register("sendWelcomeEmail")} />
              Send welcome email
            </label>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={save.isPending}>
              {editing ? "Save Changes" : "Save Customer"}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
