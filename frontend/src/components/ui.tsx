import clsx from "clsx";
import { forwardRef, useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { createPortal } from "react-dom";
import type { Tone } from "@/lib/status";
import { initials as toInitials } from "@/lib/format";

// ---------------------------------------------------------------- Pill

const toneClass: Record<Tone, string> = {
  green: "bg-[#E3F4E8] text-[#1F7A3F]",
  blue: "bg-[#E1ECFB] text-[#2556A8]",
  amber: "bg-[#FCEFC7] text-[#9A6400]",
  red: "bg-[#FBE3E3] text-[#B42323]",
  purple: "bg-[#EEE7FD] text-[#6B3FD0]",
  teal: "bg-[#DDF1EC] text-[#14776B]",
  gray: "bg-sand-200 text-ink-700",
  orange: "bg-[#FDE9D9] text-[#B4520E]",
};

export function Pill({ tone = "gray", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={clsx("inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11px] font-semibold", toneClass[tone], className)}>
      {children}
    </span>
  );
}

// ---------------------------------------------------------------- Avatar

const avatarColors = ["#14776B", "#D97706", "#6D28D9", "#2563EB", "#B91C1C", "#64748B", "#0F766E", "#C2410C", "#7C3AED", "#0369A1"];

export function avatarColor(seed: string) {
  let h = 0;
  for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return avatarColors[h % avatarColors.length];
}

export function Avatar({ name, initials, size = 36, color, className }: { name: string; initials?: string; size?: number; color?: string; className?: string }) {
  return (
    <span
      className={clsx("inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white", className)}
      style={{ width: size, height: size, fontSize: size * 0.36, background: color ?? avatarColor(name) }}
      aria-hidden
    >
      {initials ?? toInitials(name)}
    </span>
  );
}

// ---------------------------------------------------------------- Card

export function Card({ children, className, title, action }: { children: ReactNode; className?: string; title?: ReactNode; action?: ReactNode }) {
  return (
    <section className={clsx("rounded-2xl border border-sand-300/70 bg-white shadow-card", className)}>
      {title && (
        <div className="flex items-center justify-between px-5 pt-5">
          <h2 className="text-[15px] font-semibold text-ink-900">{title}</h2>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

// ---------------------------------------------------------------- Buttons

type Variant = "primary" | "outline" | "ghost" | "danger" | "subtle";

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: "sm" | "md"; loading?: boolean }>(
  function Button({ variant = "primary", size = "md", loading, className, children, disabled, ...rest }, ref) {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={clsx(
          "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-xl font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-500/50 disabled:cursor-not-allowed disabled:opacity-60",
          size === "md" ? "h-10 px-4 text-sm" : "h-8 px-3 text-[13px]",
          variant === "primary" && "bg-teal-700 text-white shadow-sm hover:bg-teal-800",
          variant === "outline" && "border-[1.5px] border-teal-700 bg-white text-teal-700 hover:bg-teal-50",
          variant === "ghost" && "text-ink-700 hover:bg-sand-200",
          variant === "subtle" && "bg-sand-200 text-ink-900 hover:bg-sand-300",
          variant === "danger" && "bg-red-600 text-white hover:bg-red-700",
          className,
        )}
        {...rest}
      >
        {loading && <Spinner className="h-4 w-4" />}
        {children}
      </button>
    );
  },
);

export function Spinner({ className }: { className?: string }) {
  return <span className={clsx("inline-block animate-spin rounded-full border-2 border-current border-t-transparent", className ?? "h-5 w-5")} />;
}

// ---------------------------------------------------------------- Form controls

export const inputClass =
  "h-11 w-full rounded-xl border border-sand-300 bg-white px-3.5 text-sm text-ink-900 placeholder:text-ink-400 transition focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-600/15 disabled:bg-sand-100";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(function Input({ className, invalid, ...rest }, ref) {
  return <input ref={ref} className={clsx(inputClass, invalid && "border-red-300 bg-red-50/30 focus:border-red-400 focus:ring-red-400/15", className)} {...rest} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }>(function Select({ className, invalid, children, ...rest }, ref) {
  return (
    <select ref={ref} className={clsx(inputClass, "cursor-pointer appearance-none bg-[length:12px] bg-[right_14px_center] bg-no-repeat pr-9", invalid && "border-red-300", className)} style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 8'%3E%3Cpath d='M1 1l5 5 5-5' stroke='%233E4C59' stroke-width='1.6' fill='none'/%3E%3C/svg%3E\")" }} {...rest}>
      {children}
    </select>
  );
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }>(function Textarea({ className, invalid, ...rest }, ref) {
  return <textarea ref={ref} className={clsx(inputClass, "h-auto min-h-[88px] py-2.5", invalid && "border-red-300", className)} {...rest} />;
});

export function Field({ label, error, children, className, hint }: { label?: ReactNode; error?: string; children: ReactNode; className?: string; hint?: ReactNode }) {
  return (
    <label className={clsx("block", className)}>
      {label && <span className="mb-1.5 block text-[13px] font-semibold text-ink-700">{label}</span>}
      {children}
      {error ? <span className="mt-1 flex items-center gap-1 text-xs font-medium text-red-700">⚠ {error}</span> : hint && <span className="mt-1 block text-xs text-ink-500">{hint}</span>}
    </label>
  );
}

// ---------------------------------------------------------------- KPI card

export function KpiCard({ label, value, delta, deltaTone = "up", highlight, className }: { label: string; value: ReactNode; delta?: ReactNode; deltaTone?: "up" | "down" | "warn" | "muted"; highlight?: boolean; className?: string }) {
  return (
    <div className={clsx("rounded-2xl border px-4 py-3.5 shadow-card", highlight ? "border-amber-300 bg-[#FFF9E6]" : "border-sand-300/70 bg-white", className)}>
      <p className="text-[13px] text-ink-500">{label}</p>
      <p className={clsx("mt-0.5 text-[26px] font-bold leading-tight tracking-tight", highlight ? "text-[#B4520E]" : "text-ink-900")}>{value}</p>
      {delta && (
        <p className={clsx("mt-0.5 text-xs font-medium", deltaTone === "up" && "text-[#1F7A3F]", deltaTone === "down" && "text-[#1F7A3F]", deltaTone === "warn" && "text-[#B4520E]", deltaTone === "muted" && "text-ink-500")}>
          {delta}
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Overlays

function useEscape(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [open, onClose]);
}

export function Modal({ open, onClose, title, subtitle, children, width = 640 }: { open: boolean; onClose: () => void; title: ReactNode; subtitle?: ReactNode; children: ReactNode; width?: number }) {
  useEscape(open, onClose);
  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-[#4B5563]/55 p-4 py-10 backdrop-blur-[1px]" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="w-full rounded-2xl bg-white p-6 shadow-pop" style={{ maxWidth: width }} role="dialog" aria-modal>
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-ink-900">{title}</h2>
            {subtitle && <p className="mt-0.5 text-[13px] text-ink-500">{subtitle}</p>}
          </div>
          <button onClick={onClose} className="rounded-lg p-1 text-lg leading-none text-ink-500 hover:bg-sand-200" aria-label="Close">
            ×
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export function Drawer({ open, onClose, title, children, width = 340 }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; width?: number }) {
  useEscape(open, onClose);
  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-40" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="absolute bottom-4 right-4 top-4 flex flex-col overflow-y-auto rounded-2xl border border-sand-300 bg-white p-5 shadow-pop" style={{ width }}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink-900">{title}</h2>
          <button onClick={onClose} className="rounded-lg px-1.5 text-lg leading-none text-ink-500 hover:bg-sand-200" aria-label="Close">
            ×
          </button>
        </div>
        {children}
      </aside>
    </div>,
    document.body,
  );
}

// ---------------------------------------------------------------- Tabs, states

export function Tabs<T extends string>({ tabs, value, onChange, className }: { tabs: { value: T; label: ReactNode }[]; value: T; onChange: (v: T) => void; className?: string }) {
  return (
    <div className={clsx("flex gap-6 border-b border-sand-300/80 px-4", className)}>
      {tabs.map((t) => (
        <button
          key={t.value}
          onClick={() => onChange(t.value)}
          className={clsx("-mb-px border-b-2 py-3 text-sm font-medium transition", value === t.value ? "border-teal-700 font-semibold text-teal-700" : "border-transparent text-ink-500 hover:text-ink-900")}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** Pill-style segmented filter (All / Leads / Active ...). */
export function Segmented<T extends string>({ options, value, onChange }: { options: { value: T; label: string }[]; value: T; onChange: (v: T) => void }) {
  return (
    <div className="inline-flex rounded-xl border border-sand-300 bg-sand-200/70 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={clsx("rounded-lg px-3.5 py-1.5 text-[13px] transition", value === o.value ? "bg-white font-semibold text-teal-700 shadow-sm" : "text-ink-500 hover:text-ink-900")}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx("animate-pulse rounded-lg bg-sand-200", className)} />;
}

export function EmptyState({ icon = "🗂️", title, message, action }: { icon?: string; title: string; message?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-3 text-4xl">{icon}</div>
      <p className="font-semibold text-ink-900">{title}</p>
      {message && <p className="mt-1 max-w-sm text-sm text-ink-500">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <EmptyState icon="⚠️" title="Could not load data" message={message} action={onRetry && <Button variant="outline" size="sm" onClick={onRetry}>Retry</Button>} />;
}
