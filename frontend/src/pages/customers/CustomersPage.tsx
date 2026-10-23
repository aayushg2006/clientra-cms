import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function CustomersPage() {
  return (
    <>
      <PageHeader title="Customers" />
      <Card>
        <EmptyState icon="🚧" title="Customers is being built" />
      </Card>
    </>
  );
}
