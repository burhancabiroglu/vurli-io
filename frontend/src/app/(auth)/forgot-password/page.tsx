import Link from "next/link";

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { getDictionary } from "@/lib/i18n";

import { ForgotPasswordForm } from "./forgot-password-form";

export default async function ForgotPasswordPage() {
  const { t } = await getDictionary();
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{t.forgot.title}</h1>
        </CardTitle>
        <CardDescription>{t.forgot.body}</CardDescription>
      </CardHeader>
      <CardContent>
        <ForgotPasswordForm
          labels={{ email: t.common.email, submit: t.forgot.submit, sent: t.forgot.sent }}
          errors={t.errors}
          unexpected={t.common.unexpected}
        />
      </CardContent>
      <CardFooter className="text-sm">
        <Link href="/login" className="text-muted-foreground underline-offset-4 hover:underline">
          {t.forgot.back}
        </Link>
      </CardFooter>
    </Card>
  );
}
