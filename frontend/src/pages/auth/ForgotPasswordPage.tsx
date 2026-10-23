import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router-dom";
import api, { apiError } from "@/lib/api";
import { Button, Field, Input } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { AuthShell } from "./AuthShell";

const emailSchema = z.object({ email: z.string().min(1, "Email is required").email("Enter a valid email") });

const resetSchema = z
  .object({
    otp: z.string().regex(/^\d{6}$/, "Enter the 6-digit code"),
    newPassword: z
      .string()
      .min(8, "At least 8 characters")
      .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, "Use at least one letter and one number"),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords do not match" });

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const toast = useToast();

  const step1 = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema) });
  const step2 = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });

  const sendCode = async (v: z.infer<typeof emailSchema>) => {
    setError(null);
    try {
      await api.post("/api/auth/forgot-password", v);
      setEmail(v.email);
    } catch (e) {
      setError(apiError(e).message);
    }
  };

  const reset = async (v: z.infer<typeof resetSchema>) => {
    setError(null);
    try {
      await api.post("/api/auth/reset-password", { email, otp: v.otp, newPassword: v.newPassword });
      toast("Password updated. Please sign in.");
      navigate("/login");
    } catch (e) {
      const err = apiError(e);
      setError(err.fieldErrors ? Object.values(err.fieldErrors)[0] : err.message);
    }
  };

  return (
    <AuthShell>
      <h2 className="text-[26px] font-bold tracking-tight text-ink-900">Reset your password</h2>
      {!email ? (
        <>
          <p className="mt-1 text-sm text-ink-500">We'll email you a 6-digit code to reset it.</p>
          <form onSubmit={step1.handleSubmit(sendCode)} className="mt-6 space-y-4" noValidate>
            <Field label="Work Email" error={step1.formState.errors.email?.message}>
              <Input type="email" placeholder="name@clientra.in" {...step1.register("email")} />
            </Field>
            {error && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
            <Button type="submit" className="h-11 w-full" loading={step1.formState.isSubmitting}>
              Send reset code
            </Button>
          </form>
        </>
      ) : (
        <>
          <p className="mt-1 text-sm text-ink-500">
            If <b>{email}</b> has an account, a code is on its way. It expires in 10 minutes.
          </p>
          <form onSubmit={step2.handleSubmit(reset)} className="mt-6 space-y-4" noValidate>
            <Field label="6-digit code" error={step2.formState.errors.otp?.message}>
              <Input inputMode="numeric" maxLength={6} placeholder="123456" className="tracking-[0.4em]" {...step2.register("otp")} />
            </Field>
            <Field label="New password" error={step2.formState.errors.newPassword?.message}>
              <Input type="password" autoComplete="new-password" {...step2.register("newPassword")} />
            </Field>
            <Field label="Confirm password" error={step2.formState.errors.confirm?.message}>
              <Input type="password" autoComplete="new-password" {...step2.register("confirm")} />
            </Field>
            {error && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
            <Button type="submit" className="h-11 w-full" loading={step2.formState.isSubmitting}>
              Update password
            </Button>
            <button type="button" className="w-full text-center text-[13px] text-ink-500 hover:underline" onClick={() => setEmail(null)}>
              Use a different email
            </button>
          </form>
        </>
      )}
      <p className="mt-6 text-center text-[13px]">
        <Link to="/login" className="font-semibold text-teal-700 hover:underline">
          ← Back to sign in
        </Link>
      </p>
    </AuthShell>
  );
}
