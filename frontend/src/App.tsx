import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout, RequireRole } from "@/components/layout/AppLayout";
import { Spinner } from "@/components/ui";
import { homeFor, useAuth } from "@/lib/auth";
import LoginPage from "@/pages/auth/LoginPage";
import ForgotPasswordPage from "@/pages/auth/ForgotPasswordPage";

const DashboardPage = lazy(() => import("@/pages/dashboard/DashboardPage"));
const CustomersPage = lazy(() => import("@/pages/customers/CustomersPage"));
const CustomerProfilePage = lazy(() => import("@/pages/customers/CustomerProfilePage"));
const InteractionsPage = lazy(() => import("@/pages/interactions/InteractionsPage"));
const OrdersPage = lazy(() => import("@/pages/orders/OrdersPage"));
const ProductsPage = lazy(() => import("@/pages/products/ProductsPage"));
const TicketsBoardPage = lazy(() => import("@/pages/tickets/TicketsBoardPage"));
const TicketDetailPage = lazy(() => import("@/pages/tickets/TicketDetailPage"));
const ReportsPage = lazy(() => import("@/pages/reports/ReportsPage"));
const SettingsPage = lazy(() => import("@/pages/settings/SettingsPage"));

function Home() {
  const { role } = useAuth();
  return <Navigate to={homeFor(role)} replace />;
}

const Loading = (
  <div className="flex h-[60vh] items-center justify-center text-teal-700">
    <Spinner className="h-7 w-7" />
  </div>
);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route element={<AppLayout />}>
        <Route
          path="*"
          element={
            <Suspense fallback={Loading}>
              <Routes>
                <Route index element={<Home />} />
                <Route path="dashboard" element={<DashboardPage />} />
                <Route path="customers" element={<CustomersPage />} />
                <Route path="customers/:id" element={<CustomerProfilePage />} />
                <Route path="interactions" element={<RequireRole roles={["ADMIN", "SALES_EXEC"]}><InteractionsPage /></RequireRole>} />
                <Route path="orders" element={<RequireRole roles={["ADMIN", "SALES_EXEC"]}><OrdersPage /></RequireRole>} />
                <Route path="products" element={<RequireRole roles={["ADMIN", "SALES_EXEC"]}><ProductsPage /></RequireRole>} />
                <Route path="tickets" element={<RequireRole roles={["ADMIN", "SUPPORT_AGENT"]}><TicketsBoardPage /></RequireRole>} />
                <Route path="tickets/:id" element={<RequireRole roles={["ADMIN", "SUPPORT_AGENT"]}><TicketDetailPage /></RequireRole>} />
                <Route path="reports" element={<RequireRole roles={["ADMIN"]}><ReportsPage /></RequireRole>} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="*" element={<Home />} />
              </Routes>
            </Suspense>
          }
        />
      </Route>
    </Routes>
  );
}
