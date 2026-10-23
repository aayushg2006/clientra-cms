import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function TicketDetailPage() {
  return (
    <>
      <PageHeader title="Ticket" />
      <Card>
        <EmptyState icon="🚧" title="Ticket is being built" />
      </Card>
    </>
  );
}
