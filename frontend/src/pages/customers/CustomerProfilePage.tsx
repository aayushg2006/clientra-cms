import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function CustomerProfilePage() {
  return (
    <>
      <PageHeader title="Customer" />
      <Card>
        <EmptyState icon="🚧" title="Customer is being built" />
      </Card>
    </>
  );
}
