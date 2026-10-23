import type { ReactNode } from "react";

/** Split-screen frame shared by Login and Forgot password: teal brand panel left, sand form panel right. */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <section className="relative hidden w-[46%] flex-col justify-between overflow-hidden bg-gradient-to-b from-[#137567] via-[#0F665A] to-[#0B4D44] px-11 py-11 text-white lg:flex">
        <div className="pointer-events-none absolute -right-28 top-[100px] h-[316px] w-[316px] rounded-full border-[52px] border-white/[0.07]" />
        <div className="relative flex items-center gap-2.5">
          <span className="flex h-[30px] w-[30px] items-center justify-center rounded-lg bg-white text-[15px] font-extrabold text-teal-700">C</span>
          <span className="text-[17px] font-bold">Clientra CMS</span>
        </div>

        <div className="relative">
          <h1 className="text-[34px] font-extrabold leading-[1.15] tracking-tight">
            Know every customer.
            <br />
            Close every loop.
            <br />
            <span className="text-[#F6C744]">Grow every account.</span>
          </h1>
          <p className="mt-4 max-w-[350px] text-[13.5px] leading-relaxed text-white/85">
            A unified Customer Management System to manage customer profiles, interactions, orders and support tickets — from first contact to loyal customer.
          </p>
        </div>

        <div className="relative flex gap-6">
          {[
            ["360°", "Customer view"],
            ["3", "User roles"],
            ["SLA", "Ticket tracking"],
          ].map(([big, small]) => (
            <div key={small}>
              <p className="text-lg font-bold leading-tight">{big}</p>
              <p className="text-[12px] text-white/80">{small}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="flex flex-1 items-center justify-center bg-sand-100 px-6 py-10">
        <div className="w-full max-w-[350px]">{children}</div>
      </section>
    </div>
  );
}
