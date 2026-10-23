import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import { Avatar, Pill } from "@/components/ui";
import { CUSTOMER_STATUS, TICKET_STATUS } from "@/lib/status";
import type { CustomerStatus, TicketStatus } from "@/lib/types";

interface SearchResult {
  customers: { id: number; name: string; code: string; email: string; companyName?: string; status: CustomerStatus }[];
  tickets: { id: number; number: string; subject: string; customerName: string; status: TicketStatus }[];
}

function useDebounced<T>(value: T, ms = 250) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function GlobalSearch() {
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(false);
  const term = useDebounced(q.trim());
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const { data, isFetching } = useQuery({
    queryKey: ["search", term],
    queryFn: () => api.get<SearchResult>("/api/search", { params: { q: term } }).then((r) => r.data),
    enabled: term.length >= 2,
  });

  useEffect(() => {
    const h = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  const go = (path: string) => {
    setOpen(false);
    setQ("");
    navigate(path);
  };

  const empty = data && data.customers.length === 0 && data.tickets.length === 0;

  return (
    <div className="relative w-[224px]" ref={ref}>
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[13px]">🔍</span>
      <input
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && q.trim()) go(`/customers?q=${encodeURIComponent(q.trim())}`);
          if (e.key === "Escape") setOpen(false);
        }}
        placeholder="Search customers, tickets..."
        className="h-[38px] w-full rounded-xl border border-sand-300 bg-white pl-9 pr-3 text-[13.5px] placeholder:text-ink-700 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-600/15"
      />
      {open && term.length >= 2 && (
        <div className="absolute right-0 top-11 z-30 w-[380px] overflow-hidden rounded-2xl border border-sand-300 bg-white shadow-pop">
          {isFetching && !data && <p className="px-4 py-5 text-sm text-ink-500">Searching…</p>}
          {empty && <p className="px-4 py-5 text-sm text-ink-500">No matches for “{term}”</p>}
          {!!data?.customers.length && (
            <div className="py-1.5">
              <p className="px-4 py-1 text-[11px] font-semibold uppercase tracking-wide text-ink-400">Customers</p>
              {data.customers.map((c) => (
                <button key={c.id} onClick={() => go(`/customers/${c.id}`)} className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-sand-50">
                  <Avatar name={c.name} size={30} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[13px] font-semibold">{c.name}</span>
                    <span className="block truncate text-xs text-ink-500">
                      {c.code} · {c.companyName ?? c.email}
                    </span>
                  </span>
                  <Pill tone={CUSTOMER_STATUS[c.status].tone}>{CUSTOMER_STATUS[c.status].label}</Pill>
                </button>
              ))}
            </div>
          )}
          {!!data?.tickets.length && (
            <div className="border-t border-sand-200 py-1.5">
              <p className="px-4 py-1 text-[11px] font-semibold uppercase tracking-wide text-ink-400">Tickets</p>
              {data.tickets.map((t) => (
                <button key={t.id} onClick={() => go(`/tickets/${t.id}`)} className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-sand-50">
                  <span className="text-lg">🎫</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[13px] font-semibold">{t.subject}</span>
                    <span className="block truncate text-xs text-ink-500">
                      {t.number} · {t.customerName}
                    </span>
                  </span>
                  <Pill tone={TICKET_STATUS[t.status].tone}>{TICKET_STATUS[t.status].label}</Pill>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
