import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function TicketsBoardPage() {
  return (
    <>
      <PageHeader title="Support Tickets" />
      <Card>
        <EmptyState icon="🚧" title="Support Tickets is being built" />
      </Card>
    </>
  );
}
