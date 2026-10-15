import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getDictionary } from "@/lib/i18n";

import { VerifyEmail } from "./verify-email";

export default async function VerifyEmailPage() {
  const { t } = await getDictionary();
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{t.verify.title}</h1>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <VerifyEmail
          labels={{
            working: t.verify.working,
            done: t.verify.done,
            invalid: t.verify.invalid,
            continue: t.verify.continue,
          }}
        />
      </CardContent>
    </Card>
  );
}
