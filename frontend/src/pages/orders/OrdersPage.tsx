import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function OrdersPage() {
  return (
    <>
      <PageHeader title="Orders" />
      <Card>
        <EmptyState icon="🚧" title="Orders is being built" />
      </Card>
    </>
  );
}
