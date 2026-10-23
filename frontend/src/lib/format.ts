const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const num = new Intl.NumberFormat("en-IN");

/** ₹4,82,000 */
export const money = (v: number | null | undefined) => inr.format(Number(v ?? 0));

/** 2,846 */
export const count = (v: number | null | undefined) => num.format(Number(v ?? 0));

/** ₹18.4L / ₹1.2Cr / ₹62.4K style used on KPI cards. */
export function moneyShort(v: number | null | undefined) {
  const n = Number(v ?? 0);
  const abs = Math.abs(n);
  if (abs >= 1e7) return `₹${trim(n / 1e7)}Cr`;
  if (abs >= 1e5) return `₹${trim(n / 1e5)}L`;
  if (abs >= 1e3) return `₹${trim(n / 1e3)}K`;
  return `₹${Math.round(n)}`;
}

function trim(n: number) {
  const fixed = n >= 100 ? n.toFixed(0) : n.toFixed(n >= 10 ? 1 : 2);
  return fixed.replace(/\.0+$/, "").replace(/(\.\d)0$/, "$1");
}

const dayMs = 86_400_000;

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** Today / Yesterday / 2 days ago / 5 Oct / 28 Jun 2025 */
export function relativeDay(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  const diff = Math.round((startOfDay(new Date()) - startOfDay(d)) / dayMs);
  if (diff === 0) return "Today";
  if (diff === 1) return "Yesterday";
  if (diff > 1 && diff < 4) return `${diff} days ago`;
  return shortDate(iso);
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** 12 Oct (adds the year when it's not the current year) */
export function shortDate(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  const base = `${String(d.getDate()).padStart(2, "0")} ${MONTHS[d.getMonth()]}`;
  return d.getFullYear() === new Date().getFullYear() ? base : `${base} ${d.getFullYear()}`;
}

/** 28 Sep, 11:02 */
export function dateTime(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  return `${shortDate(iso)}, ${time(d.toISOString())}`;
}

/** 11:30 */
export function time(iso?: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

/** Today, 10:15 AM */
export function friendlyDateTime(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  const t = d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
  const day = relativeDay(iso);
  return `${day}, ${t}`;
}

/** Mar 2024 */
export function monthYear(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  return `${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

/** "5h 42m", "2d", "25m" */
export function duration(ms: number) {
  const abs = Math.abs(ms);
  const m = Math.floor(abs / 60_000);
  const h = Math.floor(m / 60);
  const d = Math.floor(h / 24);
  if (d >= 2) return `${d}d`;
  if (h >= 1) return m % 60 && h < 10 ? `${h}h ${m % 60}m` : `${h}h`;
  return `${m}m`;
}

export type SlaTone = "red" | "amber" | "green" | "gray";

/**
 * SLA countdown for an unresolved ticket: red when breached or under 25% of the window left,
 * amber under 60%, otherwise green.
 */
export function sla(slaDueAt: string, createdAt: string, now = Date.now()) {
  const due = new Date(slaDueAt).getTime();
  const total = Math.max(1, due - new Date(createdAt).getTime());
  const left = due - now;
  const ratio = left / total;
  const tone: SlaTone = left <= 0 || ratio < 0.25 ? "red" : ratio < 0.6 ? "amber" : "green";
  const label = left <= 0 ? `${duration(left)} overdue` : `${duration(left)} left`;
  return { left, ratio: Math.max(0, Math.min(1, ratio)), tone, label };
}

export const titleCase = (s?: string | null) =>
  (s ?? "")
    .toLowerCase()
    .split("_")
    .map((w) => (w === "smb" ? "SMB" : w.charAt(0).toUpperCase() + w.slice(1)))
    .join(" ");

export const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((p, i, a) => (i === 0 || i === a.length - 1 ? p[0] : ""))
    .join("")
    .toUpperCase()
    .slice(0, 2);
