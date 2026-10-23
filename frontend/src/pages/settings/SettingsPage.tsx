import { Fragment, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { authStore, ROLE_LABEL, useAuth } from "@/lib/auth";
import { dateTime, relativeDay } from "@/lib/format";
import { useUsers } from "@/lib/queries";
import type { Page, Role, User } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Pagination } from "@/components/Pagination";
import { Avatar, Button, Card, Field, Input, Modal, Pill, Select, Skeleton, Tabs } from "@/components/ui";
import { useToast } from "@/components/Toast";

type Tab = "profile" | "security" | "users" | "audit" | "system";

export default function SettingsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const [tab, setTab] = useState<Tab>("profile");
  const tabs: { value: Tab; label: string }[] = [
    { value: "profile", label: "Profile" },
    { value: "security", label: "Password" },
    ...(isAdmin
      ? ([
          { value: "users", label: "Users & Roles" },
          { value: "audit", label: "Audit Log" },
          { value: "system", label: "System" },
        ] as const)
      : []),
  ];
  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile, password and workspace administration" />
      <Card>
        <Tabs<Tab> tabs={tabs} value={tab} onChange={setTab} />
        <div className="p-5">
          {tab === "profile" && <ProfileTab />}
          {tab === "security" && <PasswordTab />}
          {tab === "users" && <UsersTab />}
          {tab === "audit" && <AuditTab />}
          {tab === "system" && <SystemTab />}
        </div>
      </Card>
    </>
  );
}

// ---------------------------------------------------------------- profile

const profileSchema = z.object({
  fullName: z.string().trim().min(1, "Name is required").max(100),
  title: z.string().trim().max(80).optional(),
  phone: z.string().trim().regex(/^$|^[0-9+ ]{7,15}$/, "Enter a valid phone number").optional(),
});

function ProfileTab() {
  const { user } = useAuth();
  const toast = useToast();
  const { register, handleSubmit, formState } = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user?.fullName, title: user?.title ?? "", phone: user?.phone ?? "" },
  });
  const save = useMutation({
    mutationFn: (v: z.infer<typeof profileSchema>) => api.put<User>("/api/auth/me", v).then((r) => r.data),
    onSuccess: (u) => {
      authStore.setUser(u);
      toast("Profile updated");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });
  return (
    <div className="max-w-xl">
      <div className="mb-5 flex items-center gap-3">
        <Avatar name={user?.fullName ?? ""} initials={user?.initials} size={52} color="#D97706" />
        <div>
          <p className="text-lg font-bold">{user?.fullName}</p>
          <p className="text-[13px] text-ink-500">
            {user?.email} · {user && ROLE_LABEL[user.role]}
          </p>
        </div>
      </div>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="grid grid-cols-2 gap-3.5">
        <Field label="Full name" error={formState.errors.fullName?.message} className="col-span-2">
          <Input {...register("fullName")} />
        </Field>
        <Field label="Job title">
          <Input {...register("title")} />
        </Field>
        <Field label="Phone" error={formState.errors.phone?.message}>
          <Input {...register("phone")} />
        </Field>
        <div className="col-span-2">
          <Button type="submit" loading={save.isPending}>
            Save profile
          </Button>
        </div>
      </form>
    </div>
  );
}

// ---------------------------------------------------------------- password

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: z
      .string()
      .min(8, "At least 8 characters")
      .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, "Use at least one letter and one number"),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords do not match" });

function PasswordTab() {
  const toast = useToast();
  const { register, handleSubmit, reset, setError, formState } = useForm<z.infer<typeof passwordSchema>>({ resolver: zodResolver(passwordSchema) });
  const save = useMutation({
    mutationFn: (v: z.infer<typeof passwordSchema>) => api.post("/api/auth/change-password", { currentPassword: v.currentPassword, newPassword: v.newPassword }),
    onSuccess: () => {
      reset({ currentPassword: "", newPassword: "", confirm: "" });
      toast("Password changed");
    },
    onError: (e) => setError("currentPassword", { message: apiError(e).message }),
  });
  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} className="max-w-sm space-y-3.5">
      <Field label="Current password" error={formState.errors.currentPassword?.message}>
        <Input type="password" autoComplete="current-password" {...register("currentPassword")} />
      </Field>
      <Field label="New password" error={formState.errors.newPassword?.message}>
        <Input type="password" autoComplete="new-password" {...register("newPassword")} />
      </Field>
      <Field label="Confirm new password" error={formState.errors.confirm?.message}>
        <Input type="password" autoComplete="new-password" {...register("confirm")} />
      </Field>
      <Button type="submit" loading={save.isPending}>
        Change password
      </Button>
    </form>
  );
}

// ---------------------------------------------------------------- users

const ROLE_TONE: Record<Role, "purple" | "teal" | "amber"> = { ADMIN: "purple", SALES_EXEC: "teal", SUPPORT_AGENT: "amber" };

function UsersTab() {
  const { user: me } = useAuth();
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const users = useQuery({ queryKey: ["users", "admin"], queryFn: () => api.get<User[]>("/api/users", { params: { includeInactive: true } }).then((r) => r.data) });
  return (
    <>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-[13px] text-ink-500">Admins have full access · Sales Executives manage customers, interactions and orders · Support Agents work tickets with read-only customers.</p>
        <Button onClick={() => setEditing("new")}>+ Add User</Button>
      </div>
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-sand-200 text-[11.5px] font-semibold uppercase tracking-wide text-ink-500 [&>th]:px-3 [&>th]:py-2.5">
            <th>User</th>
            <th>Role</th>
            <th>Title</th>
            <th>Last sign-in</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {users.isLoading && (
            <tr>
              <td colSpan={6}>
                <Skeleton className="m-3 h-20" />
              </td>
            </tr>
          )}
          {users.data?.map((u) => (
            <tr key={u.id} className="border-b border-sand-200 text-[14px] last:border-0 [&>td]:px-3 [&>td]:py-2.5">
              <td>
                <div className="flex items-center gap-2.5">
                  <Avatar name={u.fullName} initials={u.initials} size={32} />
                  <div>
                    <p className="font-semibold">
                      {u.fullName} {u.id === me?.id && <span className="text-xs font-normal text-ink-500">(you)</span>}
                    </p>
                    <p className="text-[12.5px] text-ink-500">{u.email}</p>
                  </div>
                </div>
              </td>
              <td>
                <Pill tone={ROLE_TONE[u.role]}>{ROLE_LABEL[u.role]}</Pill>
              </td>
              <td className="text-ink-700">{u.title ?? "—"}</td>
              <td className="text-ink-500">{u.lastLoginAt ? relativeDay(u.lastLoginAt) : "Never"}</td>
              <td>
                <Pill tone={u.active ? "green" : "gray"}>{u.active ? "Active" : "Disabled"}</Pill>
              </td>
              <td className="text-right">
                <Button size="sm" variant="ghost" onClick={() => setEditing(u)}>
                  Edit
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <UserModal user={editing} onClose={() => setEditing(null)} />
    </>
  );
}

const userSchema = z.object({
  fullName: z.string().trim().min(1, "Name is required").max(100),
  email: z.string().trim().email("Enter a valid email"),
  role: z.enum(["ADMIN", "SALES_EXEC", "SUPPORT_AGENT"]),
  title: z.string().max(80).optional(),
  phone: z.string().regex(/^$|^[0-9+ ]{7,15}$/, "Enter a valid phone number").optional(),
  password: z.string().optional(),
  active: z.boolean(),
});

function UserModal({ user, onClose }: { user: User | "new" | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const existing = user && user !== "new" ? user : null;
  const { register, handleSubmit, reset, setError, formState } = useForm<z.infer<typeof userSchema>>({ resolver: zodResolver(userSchema) });

  useEffect(() => {
    if (user) reset(existing ? { fullName: existing.fullName, email: existing.email, role: existing.role, title: existing.title ?? "", phone: existing.phone ?? "", password: "", active: existing.active } : { fullName: "", email: "", role: "SALES_EXEC", title: "", phone: "", password: "", active: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const save = useMutation({
    mutationFn: (v: z.infer<typeof userSchema>) => {
      if (!existing && (!v.password || v.password.length < 8)) {
        setError("password", { message: "Temporary password must be at least 8 characters" });
        return Promise.reject(new Error("validation"));
      }
      return existing ? api.put(`/api/users/${existing.id}`, v) : api.post("/api/users", v);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      toast(existing ? "User updated" : "User created — welcome email sent");
      onClose();
    },
    onError: (e) => {
      if ((e as Error).message === "validation") return;
      const err = apiError(e);
      if (err.fieldErrors) Object.entries(err.fieldErrors).forEach(([k, m]) => setError(k as "email", { message: m }));
      else setError("email", { message: err.message });
    },
  });

  return (
    <Modal open={!!user} onClose={onClose} title={existing ? "Edit user" : "Add user"} width={520}>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="grid grid-cols-2 gap-3.5" noValidate>
        <Field label="Full name *" error={formState.errors.fullName?.message} className="col-span-2">
          <Input {...register("fullName")} />
        </Field>
        <Field label="Email *" error={formState.errors.email?.message} className="col-span-2">
          <Input type="email" disabled={!!existing} {...register("email")} />
        </Field>
        <Field label="Role">
          <Select {...register("role")}>
            <option value="ADMIN">Admin</option>
            <option value="SALES_EXEC">Sales Executive</option>
            <option value="SUPPORT_AGENT">Support Agent</option>
          </Select>
        </Field>
        <Field label="Job title">
          <Input {...register("title")} />
        </Field>
        <Field label="Phone" error={formState.errors.phone?.message}>
          <Input {...register("phone")} />
        </Field>
        {existing ? (
          <label className="mt-7 flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-teal-700" {...register("active")} />
            Account active
          </label>
        ) : (
          <Field label="Temporary password *" error={formState.errors.password?.message}>
            <Input type="password" autoComplete="new-password" {...register("password")} />
          </Field>
        )}
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

// ---------------------------------------------------------------- audit log

interface AuditRow {
  id: number;
  entityType: string;
  entityId: number;
  customerId?: number;
  action: string;
  summary: string;
  details?: string;
  userName: string;
  createdAt: string;
}

function AuditTab() {
  const users = useUsers();
  const [entity, setEntity] = useState("");
  const [userId, setUserId] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<number | null>(null);
  const logs = useQuery({
    queryKey: ["audit", entity, userId, q, page],
    queryFn: () => api.get<Page<AuditRow>>("/api/audit-logs", { params: { entityType: entity || undefined, userId: userId || undefined, q: q || undefined, page, size: 20 } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <div className="mb-3 flex flex-wrap gap-2.5">
        <Select className="h-9 w-44 text-[13px]" value={entity} onChange={(e) => (setEntity(e.target.value), setPage(0))}>
          <option value="">Entity: All</option>
          {["CUSTOMER", "ORDER", "TICKET", "INTERACTION", "PRODUCT", "USER"].map((x) => (
            <option key={x} value={x}>
              Entity: {x.charAt(0) + x.slice(1).toLowerCase()}
            </option>
          ))}
        </Select>
        <Select className="h-9 w-48 text-[13px]" value={userId} onChange={(e) => (setUserId(e.target.value), setPage(0))}>
          <option value="">User: Anyone</option>
          {users.data?.map((u) => (
            <option key={u.id} value={u.id}>
              User: {u.fullName}
            </option>
          ))}
        </Select>
        <Input className="h-9 w-60 text-[13px]" placeholder="Search action or summary" value={q} onChange={(e) => (setQ(e.target.value), setPage(0))} />
      </div>
      <div className="overflow-hidden rounded-xl border border-sand-300">
        <table className="w-full text-left text-[13px]">
          <thead className="bg-sand-100 text-[11px] font-semibold uppercase tracking-wide text-ink-500 [&_th]:px-3 [&_th]:py-2">
            <tr>
              <th>When</th>
              <th>User</th>
              <th>Entity</th>
              <th>Action</th>
              <th>Summary</th>
            </tr>
          </thead>
          <tbody>
            {logs.data?.content.map((a) => (
              <Fragment key={a.id}>
                <tr onClick={() => setOpen(open === a.id ? null : a.id)} className="cursor-pointer border-t border-sand-200 hover:bg-sand-50 [&>td]:px-3 [&>td]:py-2">
                  <td className="whitespace-nowrap text-ink-500">{dateTime(a.createdAt)}</td>
                  <td className="whitespace-nowrap">{a.userName}</td>
                  <td className="whitespace-nowrap">
                    {a.entityType} #{a.entityId}
                  </td>
                  <td className="whitespace-nowrap font-mono text-[12px]">{a.action}</td>
                  <td>{a.summary}</td>
                </tr>
                {open === a.id && a.details && (
                  <tr className="bg-sand-50">
                    <td colSpan={5} className="px-3 py-2">
                      <pre className="whitespace-pre-wrap break-all font-mono text-[12px] text-ink-700">{JSON.stringify(JSON.parse(a.details), null, 2)}</pre>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
        {logs.data && <Pagination page={page} size={20} total={logs.data.totalElements} onChange={setPage} />}
      </div>
    </>
  );
}

// ---------------------------------------------------------------- system

function SystemTab() {
  const toast = useToast();
  const qc = useQueryClient();
  const sys = useQuery({ queryKey: ["admin", "system"], queryFn: () => api.get<{ mailEnabled: boolean; redisUp: boolean }>("/api/admin/system").then((r) => r.data) });
  const churn = useMutation({
    mutationFn: () => api.post<{ newlyHighRisk: number }>("/api/admin/churn/recalculate").then((r) => r.data),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      toast(`Churn scores updated · ${r.newlyHighRisk} newly high-risk`);
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });
  const sla = useMutation({
    mutationFn: () => api.post<{ newlyBreached: number }>("/api/admin/sla/check").then((r) => r.data),
    onSuccess: (r) => toast(`SLA check done · ${r.newlyBreached} newly breached`),
    onError: (e) => toast(apiError(e).message, "error"),
  });
  return (
    <div className="grid max-w-3xl gap-4 md:grid-cols-2">
      <div className="rounded-xl border border-sand-300 p-4">
        <p className="font-semibold">Email (SMTP)</p>
        <p className="mt-1 text-[13px] text-ink-500">Welcome emails, password reset codes, ticket replies and SLA alerts.</p>
        <div className="mt-3">{sys.data && <Pill tone={sys.data.mailEnabled ? "green" : "amber"}>{sys.data.mailEnabled ? "Enabled" : "Disabled — messages are written to the server log"}</Pill>}</div>
      </div>
      <div className="rounded-xl border border-sand-300 p-4">
        <p className="font-semibold">Redis cache</p>
        <p className="mt-1 text-[13px] text-ink-500">Dashboard KPIs (60s) and reports (5 min). The app keeps working without it.</p>
        <div className="mt-3">{sys.data && <Pill tone={sys.data.redisUp ? "green" : "red"}>{sys.data.redisUp ? "Connected" : "Unavailable"}</Pill>}</div>
      </div>
      <div className="rounded-xl border border-sand-300 p-4">
        <p className="font-semibold">Churn-risk scoring</p>
        <p className="mt-1 text-[13px] text-ink-500">Runs nightly at 02:00. Recalculate now after bulk changes.</p>
        <Button className="mt-3" variant="outline" size="sm" onClick={() => churn.mutate()} loading={churn.isPending}>
          Recalculate now
        </Button>
      </div>
      <div className="rounded-xl border border-sand-300 p-4">
        <p className="font-semibold">SLA monitor</p>
        <p className="mt-1 text-[13px] text-ink-500">Checks every 5 minutes and alerts admins on breaches.</p>
        <Button className="mt-3" variant="outline" size="sm" onClick={() => sla.mutate()} loading={sla.isPending}>
          Run check now
        </Button>
      </div>
    </div>
  );
}
