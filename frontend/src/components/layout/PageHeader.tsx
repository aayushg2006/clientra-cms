import type { ReactNode } from "react";
import { GlobalSearch } from "./GlobalSearch";
import { NotificationBell } from "./NotificationBell";

/** Page title row used on every screen: title + subtitle on the left, search, bell and actions on the right. */
export function PageHeader({ title, subtitle, actions, search = true }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode; search?: boolean }) {
  return (
    <header className="mb-5 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-[26px] font-bold leading-tight tracking-tight text-ink-900">{title}</h1>
        {subtitle && <p className="mt-0.5 text-[13px] text-ink-500">{subtitle}</p>}
      </div>
      <div className="flex items-center gap-3">
        {search && <GlobalSearch />}
        <NotificationBell />
        {actions}
      </div>
    </header>
  );
}
