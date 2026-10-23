import clsx from "clsx";
import { count } from "@/lib/format";

/** "Showing 1–6 of 2,846" on the left and "‹ 1 2 3 … 475 ›" on the right. */
export function Pagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);

  const items: (number | "…")[] = [];
  const push = (n: number) => !items.includes(n) && items.push(n);
  [0, 1, 2].forEach((n) => n < pages && push(n));
  if (page > 3) items.push("…");
  [page - 1, page, page + 1].forEach((n) => n > 2 && n < pages - 1 && push(n));
  if (page < pages - 4) items.push("…");
  if (pages > 3) push(pages - 1);

  return (
    <div className="flex items-center justify-between border-t border-sand-200 px-4 py-3 text-[13px] text-ink-500">
      <span>
        Showing {count(from)}–{count(to)} of {count(total)}
      </span>
      <div className="flex items-center gap-0.5">
        <button disabled={page === 0} onClick={() => onChange(page - 1)} className="rounded px-1.5 py-0.5 hover:bg-sand-200 disabled:opacity-40" aria-label="Previous page">
          ‹
        </button>
        {items.map((it, i) =>
          it === "…" ? (
            <span key={`e${i}`} className="px-1">
              …
            </span>
          ) : (
            <button key={it} onClick={() => onChange(it)} className={clsx("min-w-[22px] rounded px-1.5 py-0.5", it === page ? "bg-teal-700 font-semibold text-white" : "hover:bg-sand-200")}>
              {it + 1}
            </button>
          ),
        )}
        <button disabled={page >= pages - 1} onClick={() => onChange(page + 1)} className="rounded px-1.5 py-0.5 hover:bg-sand-200 disabled:opacity-40" aria-label="Next page">
          ›
        </button>
      </div>
    </div>
  );
}
