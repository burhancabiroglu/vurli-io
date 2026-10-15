import Link from "next/link";

import { FormAlert } from "@/components/form-alert";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { getDictionary } from "@/lib/i18n";

import { LoginForm } from "./login-form";

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { t } = await getDictionary();
  const { reset } = await searchParams;
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{t.login.title}</h1>
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {reset === "1" && <FormAlert tone="info" message={t.login.resetDone} />}
        <LoginForm
          labels={{ email: t.common.email, password: t.common.password, submit: t.login.submit }}
          errors={t.errors}
          unexpected={t.common.unexpected}
        />
        <Link href="/forgot-password" className="text-sm text-muted-foreground underline-offset-4 hover:underline">
          {t.login.forgot}
        </Link>
      </CardContent>
      <CardFooter className="text-sm text-muted-foreground">
        {t.login.noAccount}&nbsp;
        <Link href="/register" className="text-foreground underline-offset-4 hover:underline">
          {t.login.register}
        </Link>
      </CardFooter>
    </Card>
  );
}
