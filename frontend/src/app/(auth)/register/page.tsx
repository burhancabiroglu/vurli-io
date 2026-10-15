import Link from "next/link";

import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { getDictionary } from "@/lib/i18n";

import { RegisterForm } from "./register-form";

export default async function RegisterPage() {
  const { t } = await getDictionary();
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{t.register.title}</h1>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <RegisterForm
          labels={{
            email: t.common.email,
            password: t.common.password,
            passwordHint: t.common.passwordHint,
            submit: t.register.submit,
            sentTitle: t.register.sentTitle,
            sentBody: t.register.sentBody,
          }}
          errors={t.errors}
          unexpected={t.common.unexpected}
        />
      </CardContent>
      <CardFooter className="text-sm text-muted-foreground">
        {t.register.haveAccount}&nbsp;
        <Link href="/login" className="text-foreground underline-offset-4 hover:underline">
          {t.register.login}
        </Link>
      </CardFooter>
    </Card>
  );
}
