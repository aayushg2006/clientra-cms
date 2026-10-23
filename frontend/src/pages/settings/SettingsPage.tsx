import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/ui";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" />
      <Card>
        <EmptyState icon="🚧" title="Settings is being built" />
      </Card>
    </>
  );
}
