import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import type { CustomerLookup } from "@/lib/types";
import { inputClass } from "./ui";
import clsx from "clsx";

/** Searchable customer selector ("Rahul Verma · CUST-02847 ▾"). */
export function CustomerPicker({ value, onChange, invalid, initialLabel }: { value: number | null; onChange: (c: CustomerLookup | null) => void; invalid?: boolean; initialLabel?: string }) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [label, setLabel] = useState(initialLabel ?? "");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (initialLabel) setLabel(initialLabel);
  }, [initialLabel]);

  const { data } = useQuery({
    queryKey: ["customer-lookup", q],
    queryFn: () => api.get<CustomerLookup[]>("/api/customers/lookup", { params: { q } }).then((r) => r.data),
    enabled: open,
  });

  useEffect(() => {
    const h = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button type="button" onClick={() => setOpen((o) => !o)} className={clsx(inputClass, "flex items-center justify-between text-left", invalid && "border-red-300")}>
        <span className={clsx("truncate", !value && "text-ink-400")}>{value ? label : "Select a customer"}</span>
        <span className="text-xs text-ink-700">▾</span>
      </button>
      {open && (
        <div className="absolute z-20 mt-1 w-full overflow-hidden rounded-xl border border-sand-300 bg-white shadow-pop">
          <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email, code…" className="w-full border-b border-sand-200 px-3 py-2.5 text-sm focus:outline-none" />
          <div className="max-h-60 overflow-y-auto py-1">
            {data?.length === 0 && <p className="px-3 py-3 text-sm text-ink-500">No customers found</p>}
            {data?.map((c) => (
              <button
                type="button"
                key={c.id}
                onClick={() => {
                  setLabel(`${c.name} · ${c.customerCode}`);
                  onChange(c);
                  setOpen(false);
                }}
                className="block w-full px-3 py-2 text-left hover:bg-sand-50"
              >
                <span className="block text-sm font-semibold">
                  {c.name} <span className="font-normal text-ink-500">· {c.customerCode}</span>
                </span>
                <span className="block truncate text-xs text-ink-500">{c.companyName ?? c.email}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
