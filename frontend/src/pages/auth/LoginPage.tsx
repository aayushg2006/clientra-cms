import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import api, { apiError } from "@/lib/api";
import { authStore, homeFor, useAuth } from "@/lib/auth";
import type { AuthResponse } from "@/lib/types";
import { Button, Field, Input } from "@/components/ui";
import { AuthShell } from "./AuthShell";

const schema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
  remember: z.boolean(),
});
type Form = z.infer<typeof schema>;

export default function LoginPage() {
  const { isAuthenticated, role } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(params.get("expired") ? "Your session expired. Please sign in again." : null);

  const { register, handleSubmit, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "", remember: true },
  });

  if (isAuthenticated) return <Navigate to={homeFor(role)} replace />;

  const onSubmit = async (f: Form) => {
    setError(null);
    try {
      const { data } = await api.post<AuthResponse>("/api/auth/login", { email: f.email, password: f.password });
      authStore.signIn(data, f.remember);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== "/login" ? from : homeFor(data.user.role), { replace: true });
    } catch (e) {
      setError(apiError(e).message);
    }
  };

  return (
    <AuthShell>
      <h2 className="text-[26px] font-bold tracking-tight text-ink-900">Sign in to Clientra</h2>
      <p className="mt-1 text-sm text-ink-500">Use your company account to continue</p>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        <Field label="Work Email" error={formState.errors.email?.message}>
          <Input type="email" autoComplete="username" placeholder="name@clientra.in" invalid={!!formState.errors.email} {...register("email")} />
        </Field>
        <Field label="Password" error={formState.errors.password?.message}>
          <Input type="password" autoComplete="current-password" placeholder="••••••••••" invalid={!!formState.errors.password} {...register("password")} />
        </Field>

        <div className="flex items-center justify-between text-[13.5px]">
          <label className="flex cursor-pointer items-center gap-1.5 text-ink-700">
            <input type="checkbox" className="h-3.5 w-3.5 accent-blue-600" {...register("remember")} />
            Keep me signed in
          </label>
          <Link to="/forgot-password" className="font-semibold text-teal-700 hover:underline">
            Forgot password?
          </Link>
        </div>

        {error && <p className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700">{error}</p>}

        <Button type="submit" className="h-11 w-full" loading={formState.isSubmitting}>
          Sign in →
        </Button>
      </form>

      <div className="mt-5 rounded-xl bg-sand-200/80 px-3 py-3">
        <p className="text-[12.5px] font-semibold text-ink-900">Role-based access</p>
        <div className="mt-1.5 flex flex-wrap gap-1.5 text-[11.5px] font-semibold">
          <span className="rounded-md bg-[#EEE7FD] px-2 py-0.5 text-[#6B3FD0]">Admin</span>
          <span className="rounded-md bg-[#DDF1EC] px-2 py-0.5 text-teal-700">Sales Executive</span>
          <span className="rounded-md bg-[#FCEFC7] px-2 py-0.5 text-[#9A6400]">Support Agent</span>
        </div>
      </div>

      <p className="mt-5 text-center text-[12.5px] text-ink-500">🔒 Secured with JWT · Spring Security</p>
    </AuthShell>
  );
}
