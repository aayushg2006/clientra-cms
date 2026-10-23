import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function ProductsPage() {
  return (
    <>
      <PageHeader title="Products" />
      <Card>
        <EmptyState icon="🚧" title="Products is being built" />
      </Card>
    </>
  );
}
