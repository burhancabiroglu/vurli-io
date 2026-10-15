import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getDictionary } from "@/lib/i18n";

import { ResetPasswordForm } from "./reset-password-form";

export default async function ResetPasswordPage() {
  const { t } = await getDictionary();
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{t.reset.title}</h1>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ResetPasswordForm
          labels={{
            password: t.common.newPassword,
            passwordHint: t.common.passwordHint,
            submit: t.reset.submit,
            noToken: t.reset.noToken,
          }}
          errors={t.errors}
          unexpected={t.common.unexpected}
        />
      </CardContent>
    </Card>
  );
}
