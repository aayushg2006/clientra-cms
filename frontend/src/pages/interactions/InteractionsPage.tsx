import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function InteractionsPage() {
  return (
    <>
      <PageHeader title="Interactions" />
      <Card>
        <EmptyState icon="🚧" title="Interactions is being built" />
      </Card>
    </>
  );
}
