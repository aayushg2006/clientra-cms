import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import api, { apiError, download } from "@/lib/api";
import type { ImportResult } from "@/lib/types";
import { Button, Modal } from "@/components/ui";

export function ImportCustomersModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();

  const upload = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      fd.append("file", file!);
      return api.post<ImportResult>("/api/customers/import", fd).then((r) => r.data);
    },
    onSuccess: (r) => {
      setResult(r);
      qc.invalidateQueries({ queryKey: ["customers"] });
    },
    onError: (e) => setError(apiError(e).message),
  });

  const close = () => {
    setFile(null);
    setResult(null);
    setError(null);
    onClose();
  };

  return (
    <Modal open={open} onClose={close} title="Import customers from CSV" subtitle="Each row is validated; duplicates and invalid rows are skipped and reported." width={600}>
      {!result ? (
        <>
          <button
            type="button"
            onClick={() => input.current?.click()}
            className="flex w-full flex-col items-center justify-center rounded-2xl border-2 border-dashed border-sand-400 bg-sand-50 px-6 py-10 text-center hover:border-teal-600"
          >
            <span className="text-3xl">📄</span>
            <span className="mt-2 font-semibold">{file ? file.name : "Choose a .csv file"}</span>
            <span className="mt-1 text-xs text-ink-500">{file ? `${(file.size / 1024).toFixed(1)} KB` : "Max 5 MB · up to 2,000 rows"}</span>
          </button>
          <input ref={input} type="file" accept=".csv,text/csv" className="hidden" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <p className="mt-3 text-xs text-ink-500">
            Columns: firstName, lastName, email, phone, companyName, customerType, segment, source, ownerEmail, tags (separate with ;), addressLine1, city, state, postalCode.{" "}
            <button className="font-semibold text-teal-700 underline" onClick={() => download("/api/customers/import/template", {}, "clientra-template.csv")}>
              Download template
            </button>
          </p>
          {error && <p className="mt-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <div className="mt-5 flex justify-end gap-2">
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button disabled={!file} loading={upload.isPending} onClick={() => upload.mutate()}>
              Import
            </Button>
          </div>
        </>
      ) : (
        <>
          <div className="grid grid-cols-3 gap-3">
            {[
              ["Rows read", result.totalRows, "text-ink-900"],
              ["Imported", result.imported, "text-[#1F7A3F]"],
              ["Skipped", result.skipped, "text-[#B42323]"],
            ].map(([l, v, c]) => (
              <div key={l as string} className="rounded-xl bg-sand-100 px-4 py-3">
                <p className="text-xs text-ink-500">{l}</p>
                <p className={`text-2xl font-bold ${c}`}>{v}</p>
              </div>
            ))}
          </div>
          {result.errors.length > 0 && (
            <div className="mt-4 max-h-64 overflow-y-auto rounded-xl border border-sand-300">
              <table className="w-full text-left text-[13px]">
                <thead className="sticky top-0 bg-sand-100 text-[11px] uppercase text-ink-500">
                  <tr>
                    <th className="px-3 py-2">Row</th>
                    <th className="px-3 py-2">Email</th>
                    <th className="px-3 py-2">Problem</th>
                  </tr>
                </thead>
                <tbody>
                  {result.errors.map((e, i) => (
                    <tr key={i} className="border-t border-sand-200">
                      <td className="px-3 py-1.5">{e.row}</td>
                      <td className="px-3 py-1.5">{e.email ?? "—"}</td>
                      <td className="px-3 py-1.5 text-red-700">{e.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-5 flex justify-end">
            <Button onClick={close}>Done</Button>
          </div>
        </>
      )}
    </Modal>
  );
}
