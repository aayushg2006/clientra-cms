import { Navigate, Outlet, useLocation } from "react-router-dom";
import { homeFor, useAuth } from "@/lib/auth";
import type { Role } from "@/lib/types";
import { Sidebar } from "./Sidebar";

export function AppLayout() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return (
    <div className="flex min-h-screen bg-sand-100">
      <Sidebar />
      <main className="min-w-0 flex-1 px-6 py-5">
        <Outlet />
      </main>
    </div>
  );
}

/** Route guard: sends users without the role back to their home screen. */
export function RequireRole({ roles, children }: { roles: Role[]; children: React.ReactNode }) {
  const { role } = useAuth();
  if (!role || !roles.includes(role)) return <Navigate to={homeFor(role)} replace />;
  return <>{children}</>;
}
