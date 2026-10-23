import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" />
      <Card>
        <EmptyState icon="🚧" title="Dashboard is being built" />
      </Card>
    </>
  );
}
