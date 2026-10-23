import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function ReportsPage() {
  return (
    <>
      <PageHeader title="Reports & Analytics" />
      <Card>
        <EmptyState icon="🚧" title="Reports & Analytics is being built" />
      </Card>
    </>
  );
}
