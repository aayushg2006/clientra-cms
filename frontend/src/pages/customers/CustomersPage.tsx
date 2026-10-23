import clsx from "clsx";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import api, { apiError, download } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { count, money, relativeDay, titleCase } from "@/lib/format";
import { shortName, useTags, useUsers } from "@/lib/queries";
import { CUSTOMER_STATUS, tagTone } from "@/lib/status";
import type { CustomerStats, CustomerStatus, CustomerSummary, Page } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Pagination } from "@/components/Pagination";
import { Avatar, Button, Card, EmptyState, ErrorState, Pill, Segmented, Select, Skeleton } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { CustomerFormModal } from "./CustomerFormModal";
import { ImportCustomersModal } from "./ImportCustomersModal";

const PAGE_SIZE = 20;
type StatusFilter = "ALL" | CustomerStatus;

const filterSelect = "h-[38px] w-auto min-w-[148px] rounded-xl text-[13.5px] font-medium";

export default function CustomersPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const toast = useToast();
  const users = useUsers();
  const tags = useTags();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const canEdit = hasRole("ADMIN", "SALES_EXEC");

  const filters = {
    q: params.get("q") ?? "",
    status: (params.get("status") ?? "ALL") as StatusFilter,
    segment: params.get("segment") ?? "",
    ownerId: params.get("ownerId") ?? "",
    tagId: params.get("tagId") ?? "",
    page: Number(params.get("page") ?? 0),
  };

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value && value !== "ALL") next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next, { replace: true });
  };

  const apiParams = {
    q: filters.q || undefined,
    status: filters.status === "ALL" ? undefined : filters.status,
    segment: filters.segment || undefined,
    ownerId: filters.ownerId || undefined,
    tagId: filters.tagId || undefined,
  };

  const list = useQuery({
    queryKey: ["customers", apiParams, filters.page],
    queryFn: () => api.get<Page<CustomerSummary>>("/api/customers", { params: { ...apiParams, page: filters.page, size: PAGE_SIZE } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  const stats = useQuery({ queryKey: ["customers", "stats"], queryFn: () => api.get<CustomerStats>("/api/customers/stats").then((r) => r.data) });

  const exportCsv = async () => {
    try {
      await download("/api/customers/export", apiParams, "customers.csv");
    } catch (e) {
      toast(apiError(e).message, "error");
    }
  };

  return (
    <>
      <PageHeader
        title="Customers"
        subtitle={stats.data ? `${count(stats.data.total)} customers · ${count(stats.data.leads)} leads` : " "}
        actions={
          <>
            {canEdit && (
              <Button variant="outline" onClick={() => setImporting(true)} title="Import from CSV">
                ⬆ Import
              </Button>
            )}
            <Button variant="outline" onClick={exportCsv}>
              ⬇ Export
            </Button>
            {canEdit && <Button onClick={() => setAdding(true)}>+ Add Customer</Button>}
          </>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2.5">
        <Segmented<StatusFilter>
          value={filters.status}
          onChange={(v) => set("status", v)}
          options={[
            { value: "ALL", label: "All" },
            { value: "LEAD", label: "Leads" },
            { value: "ACTIVE", label: "Active" },
            { value: "INACTIVE", label: "Inactive" },
            { value: "CHURNED", label: "Churned" },
          ]}
        />
        <Select className={filterSelect} value={filters.segment} onChange={(e) => set("segment", e.target.value)} aria-label="Segment">
          <option value="">Segment: All</option>
          {["RETAIL", "SMB", "ENTERPRISE", "OTHER"].map((s) => (
            <option key={s} value={s}>
              Segment: {titleCase(s)}
            </option>
          ))}
        </Select>
        <Select className={filterSelect} value={filters.ownerId} onChange={(e) => set("ownerId", e.target.value)} aria-label="Owner">
          <option value="">Owner: Anyone</option>
          {users.data?.map((u) => (
            <option key={u.id} value={u.id}>
              Owner: {u.fullName}
            </option>
          ))}
        </Select>
        <Select className={filterSelect} value={filters.tagId} onChange={(e) => set("tagId", e.target.value)} aria-label="Tag">
          <option value="">Tag: Any</option>
          {tags.data?.map((t) => (
            <option key={t.id} value={t.id}>
              Tag: {t.name}
            </option>
          ))}
        </Select>
        {filters.q && (
          <button onClick={() => set("q", "")} className="rounded-full bg-teal-50 px-3 py-1.5 text-[13px] font-medium text-teal-700 hover:bg-teal-100">
            “{filters.q}” ×
          </button>
        )}
      </div>

      <Card className="overflow-hidden">
        {list.isError ? (
          <ErrorState message={apiError(list.error).message} onRetry={() => list.refetch()} />
        ) : (
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-sand-200 bg-sand-50/60 text-[11.5px] font-semibold uppercase tracking-wide text-ink-500 [&>th]:whitespace-nowrap">
                <th className="px-4 py-3">Customer</th>
                <th className="px-4 py-3">Company</th>
                <th className="px-4 py-3">Segment</th>
                <th className="px-4 py-3">Owner</th>
                <th className="px-4 py-3">Lifetime Value</th>
                <th className="px-4 py-3">Last Contact</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody className={clsx(list.isPlaceholderData && "opacity-60")}>
              {list.isLoading &&
                Array.from({ length: 6 }).map((_, i) => (
                  <tr key={i} className="border-b border-sand-200">
                    <td className="px-4 py-3" colSpan={7}>
                      <Skeleton className="h-8 w-full" />
                    </td>
                  </tr>
                ))}
              {list.data?.content.map((c) => {
                const st = CUSTOMER_STATUS[c.status];
                const tag = c.tags[0];
                return (
                  <tr key={c.id} onClick={() => navigate(`/customers/${c.id}`)} className="cursor-pointer border-b border-sand-200 text-[14px] last:border-0 hover:bg-sand-50 [&>td]:whitespace-nowrap">
                    <td className="px-4 py-2.5">
                      <div className="flex items-center gap-2.5">
                        <Avatar name={c.fullName} size={34} />
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="font-semibold text-ink-900">{c.fullName}</span>
                            {tag && <Pill tone={tagTone(tag.color)}>{tag.name === "Hot Lead" ? "Hot" : tag.name}</Pill>}
                          </div>
                          <p className="truncate text-[12.5px] text-ink-500">{c.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="max-w-[190px] truncate px-4 py-2.5 text-ink-700">{c.companyName ?? "—"}</td>
                    <td className="px-4 py-2.5 text-ink-700">{titleCase(c.segment)}</td>
                    <td className="px-4 py-2.5 text-ink-700">{shortName(c.owner?.name)}</td>
                    <td className="px-4 py-2.5 font-semibold text-ink-900">{money(c.lifetimeValue)}</td>
                    <td className="px-4 py-2.5 text-ink-500">{relativeDay(c.lastContactAt)}</td>
                    <td className="px-4 py-2.5">
                      <Pill tone={st.tone}>{st.label}</Pill>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {list.data && list.data.totalElements === 0 && (
          <EmptyState icon="🔎" title="No customers match these filters" message="Try a different status, segment, owner or tag." />
        )}
        {list.data && list.data.totalElements > 0 && <Pagination page={filters.page} size={PAGE_SIZE} total={list.data.totalElements} onChange={(p) => set("page", String(p))} />}
      </Card>

      <CustomerFormModal open={adding} onClose={() => setAdding(false)} onSaved={(c) => navigate(`/customers/${c.id}`)} />
      <ImportCustomersModal open={importing} onClose={() => setImporting(false)} />
    </>
  );
}
