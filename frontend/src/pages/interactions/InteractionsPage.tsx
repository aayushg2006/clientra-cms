import clsx from "clsx";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api, { apiError } from "@/lib/api";
import { friendlyDateTime, shortDate, time } from "@/lib/format";
import { INTERACTION_ICON, INTERACTION_LABEL } from "@/lib/status";
import type { Interaction, InteractionType, Page } from "@/lib/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { Pagination } from "@/components/Pagination";
import { Avatar, Button, Card, EmptyState, Segmented, Select, Skeleton, Tabs } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { LogInteractionModal } from "./LogInteractionModal";

interface FollowUps {
  overdue: Interaction[];
  today: Interaction[];
  upcoming: Interaction[];
}
type FollowTab = "today" | "overdue" | "upcoming";
const PAGE_SIZE = 15;

export default function InteractionsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const [logging, setLogging] = useState(false);
  const [followTab, setFollowTab] = useState<FollowTab>("today");
  const [scope, setScope] = useState<"all" | "mine">("all");
  const [type, setType] = useState<"" | InteractionType>("");
  const [page, setPage] = useState(0);

  const follow = useQuery({ queryKey: ["interactions", "follow-ups"], queryFn: () => api.get<FollowUps>("/api/interactions/follow-ups").then((r) => r.data) });
  const list = useQuery({
    queryKey: ["interactions", "list", scope, type, page],
    queryFn: () => api.get<Page<Interaction>>("/api/interactions", { params: { mine: scope === "mine", type: type || undefined, page, size: PAGE_SIZE } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  const done = useMutation({
    mutationFn: (id: number) => api.patch(`/api/interactions/${id}/follow-up-done`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["interactions"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      toast("Follow-up marked done");
    },
    onError: (e) => toast(apiError(e).message, "error"),
  });

  const items = follow.data?.[followTab] ?? [];

  return (
    <>
      <PageHeader title="Interactions" subtitle="Calls, emails, meetings and notes with follow-up reminders" actions={<Button onClick={() => setLogging(true)}>+ Log Interaction</Button>} />

      <div className="grid gap-4 lg:grid-cols-[340px_1fr]">
        <Card>
          <div className="px-4 pt-4">
            <h2 className="text-[15px] font-semibold">Follow-ups</h2>
          </div>
          <Tabs<FollowTab>
            value={followTab}
            onChange={setFollowTab}
            tabs={[
              { value: "today", label: `Today (${follow.data?.today.length ?? 0})` },
              { value: "overdue", label: <span className={clsx(!!follow.data?.overdue.length && "text-red-700")}>Overdue ({follow.data?.overdue.length ?? 0})</span> },
              { value: "upcoming", label: `Next 7 days (${follow.data?.upcoming.length ?? 0})` },
            ]}
          />
          <ul className="divide-y divide-sand-100 px-4 pb-2">
            {follow.isLoading && <Skeleton className="my-3 h-16" />}
            {follow.data && items.length === 0 && <li className="py-8 text-center text-sm text-ink-500">Nothing here 🎉</li>}
            {items.map((i) => (
              <li key={i.id} className="flex items-start gap-2.5 py-3">
                <span className="mt-0.5">{INTERACTION_ICON[i.type]}</span>
                <div className="min-w-0 flex-1 cursor-pointer" onClick={() => navigate(`/customers/${i.customerId}`)}>
                  <p className="text-[13.5px]">
                    {INTERACTION_LABEL[i.type]} · <b>{i.customerName}</b>
                    {i.companyName && <span className="text-ink-500"> ({i.companyName})</span>}
                  </p>
                  <p className="truncate text-[12.5px] text-ink-500">{i.subject}</p>
                  <p className={clsx("text-[12px]", followTab === "overdue" ? "font-semibold text-red-700" : "text-ink-500")}>
                    {followTab === "today" ? time(i.followUpAt) : shortDate(i.followUpAt) + ", " + time(i.followUpAt)} · {i.user.name}
                  </p>
                </div>
                <Button size="sm" variant="outline" onClick={() => done.mutate(i.id)} loading={done.isPending && done.variables === i.id}>
                  Done
                </Button>
              </li>
            ))}
          </ul>
        </Card>

        <Card className="overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-sand-200 px-4 py-3">
            <Segmented
              value={scope}
              onChange={(v) => {
                setScope(v);
                setPage(0);
              }}
              options={[
                { value: "all", label: "All activity" },
                { value: "mine", label: "Mine" },
              ]}
            />
            <Select
              className="h-9 w-44 text-[13px]"
              value={type}
              onChange={(e) => {
                setType(e.target.value as InteractionType | "");
                setPage(0);
              }}
            >
              <option value="">Type: All</option>
              {(Object.keys(INTERACTION_LABEL) as InteractionType[]).map((t) => (
                <option key={t} value={t}>
                  Type: {INTERACTION_LABEL[t]}
                </option>
              ))}
            </Select>
          </div>
          <ul className={clsx("divide-y divide-sand-100", list.isPlaceholderData && "opacity-60")}>
            {list.isLoading && <Skeleton className="m-4 h-40" />}
            {list.data?.content.map((i) => (
              <li key={i.id} className="flex cursor-pointer gap-3 px-4 py-3 hover:bg-sand-50" onClick={() => navigate(`/customers/${i.customerId}`)}>
                <Avatar name={i.customerName} size={34} />
                <div className="min-w-0 flex-1">
                  <p className="text-[14px]">
                    <b>{i.customerName}</b> <span className="text-ink-500">· {INTERACTION_ICON[i.type]} {INTERACTION_LABEL[i.type]}</span>
                  </p>
                  <p className="text-[13.5px] text-ink-900">{i.subject}</p>
                  {i.notes && <p className="line-clamp-2 text-[13px] text-ink-500">{i.notes}</p>}
                </div>
                <div className="shrink-0 text-right text-[12px] text-ink-500">
                  <p>{friendlyDateTime(i.interactionAt)}</p>
                  <p>{i.user.name}</p>
                  {i.followUpAt && !i.followUpDone && <p className="font-semibold text-teal-700">Follow-up {shortDate(i.followUpAt)}</p>}
                </div>
              </li>
            ))}
          </ul>
          {list.data?.totalElements === 0 && <EmptyState icon="💬" title="No interactions logged yet" />}
          {!!list.data?.totalElements && <Pagination page={page} size={PAGE_SIZE} total={list.data.totalElements} onChange={setPage} />}
        </Card>
      </div>
      <LogInteractionModal open={logging} onClose={() => setLogging(false)} />
    </>
  );
}
