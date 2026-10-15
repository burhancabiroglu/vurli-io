import { logoutAction } from "@/app/actions/auth";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { format, getDictionary } from "@/lib/i18n";
import { requireUser } from "@/lib/session";

import { ResendVerification } from "./resend-verification";

export default async function DashboardPage() {
  const user = await requireUser();
  const { t } = await getDictionary();
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
      <header className="flex items-center justify-between gap-4">
        <span className="text-xl font-semibold tracking-tight">{t.common.brand}</span>
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <span>{format(t.dashboard.signedInAs, { email: user.email })}</span>
          <form action={logoutAction}>
            <Button type="submit" variant="outline" size="sm">
              {t.dashboard.logout}
            </Button>
          </form>
        </div>
      </header>

      {!user.emailVerified && (
        <Alert>
          <AlertTitle>{t.dashboard.unverifiedTitle}</AlertTitle>
          <AlertDescription className="flex flex-col items-start gap-3">
            {format(t.dashboard.unverifiedBody, { email: user.email })}
            <ResendVerification
              labels={{ resend: t.dashboard.resend, resent: t.dashboard.resent }}
              errors={t.errors}
              unexpected={t.common.unexpected}
            />
          </AlertDescription>
        </Alert>
      )}

      <section className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">{t.dashboard.title}</h1>
        <p className="text-muted-foreground">{t.dashboard.placeholder}</p>
      </section>
    </main>
  );
}
