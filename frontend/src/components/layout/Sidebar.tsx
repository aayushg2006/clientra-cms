import clsx from "clsx";
import { NavLink, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { authStore, useAuth } from "@/lib/auth";
import api from "@/lib/api";
import type { Role } from "@/lib/types";

interface NavItem {
  to: string;
  label: string;
  icon: string;
  roles: Role[];
  badge?: boolean;
}

const ALL: Role[] = ["ADMIN", "SALES_EXEC", "SUPPORT_AGENT"];

export const NAV: NavItem[] = [
  { to: "/dashboard", label: "Dashboard", icon: "▦", roles: ALL },
  { to: "/customers", label: "Customers", icon: "👥", roles: ALL },
  { to: "/interactions", label: "Interactions", icon: "💬", roles: ["ADMIN", "SALES_EXEC"] },
  { to: "/orders", label: "Orders", icon: "🧾", roles: ["ADMIN", "SALES_EXEC"] },
  { to: "/tickets", label: "Support Tickets", icon: "🎫", roles: ["ADMIN", "SUPPORT_AGENT"], badge: true },
  { to: "/products", label: "Products", icon: "📦", roles: ["ADMIN", "SALES_EXEC"] },
  { to: "/reports", label: "Reports", icon: "📈", roles: ["ADMIN"] },
  { to: "/settings", label: "Settings", icon: "⚙️", roles: ALL },
];

export function Sidebar() {
  const { user, role } = useAuth();
  const navigate = useNavigate();
  const showBadge = role === "ADMIN" || role === "SUPPORT_AGENT";
  const { data: openTickets } = useQuery({
    queryKey: ["nav", "open-tickets"],
    queryFn: () => api.get<{ count: number }>("/api/tickets/open-count").then((r) => r.data.count),
    enabled: showBadge,
    refetchInterval: 60_000,
  });

  return (
    <div className="w-[196px] shrink-0 bg-teal-900">
    <aside className="sticky top-0 flex h-screen flex-col px-3 py-4 text-white">
      <div className="mb-6 flex items-center gap-2.5 px-1.5">
        <span className="flex h-[30px] w-[30px] items-center justify-center rounded-lg bg-white text-[15px] font-extrabold text-teal-700">C</span>
        <span className="text-[17px] font-bold tracking-tight">Clientra CMS</span>
      </div>

      <nav className="flex flex-1 flex-col gap-1">
        {NAV.filter((n) => role && n.roles.includes(role)).map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            className={({ isActive }) =>
              clsx(
                "flex items-center gap-2 whitespace-nowrap rounded-lg px-2.5 py-[7px] text-[13.5px] transition",
                isActive ? "bg-white/[0.13] font-semibold text-white" : "text-white/85 hover:bg-white/[0.07] hover:text-white",
              )
            }
          >
            <span className="w-4 text-center text-[13px] leading-none">{n.icon}</span>
            <span>{n.label}</span>
            {n.badge && showBadge && !!openTickets && (
              <span className="ml-1 rounded-full bg-[#E07B14] px-2 py-px text-[11px] font-bold text-white">{openTickets}</span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="group relative border-t border-white/15 px-1.5 pt-3">
        <button className="flex w-full items-center gap-2.5 text-left" onClick={() => navigate("/settings")}>
          <span className="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-full bg-[#D97706] text-[11px] font-bold">{user?.initials}</span>
          <span className="min-w-0">
            <span className="block truncate text-[13px] font-semibold">{user?.fullName}</span>
            <span className="block truncate text-[12px] text-white/70">{user?.title ?? user?.role}</span>
          </span>
        </button>
        <button
          onClick={() => {
            authStore.signOut();
            navigate("/login");
          }}
          className="mt-2 w-full rounded-lg px-2 py-1.5 text-left text-[12px] text-white/70 hover:bg-white/10 hover:text-white"
        >
          ⎋ Sign out
        </button>
      </div>
    </aside>
    </div>
  );
}
