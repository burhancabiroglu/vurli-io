import { cn } from "cn";
import Link from "next/link";

import { FormAlert } from "@/components/form-alert";
import { AppHeader } from "@/components/app-header";
import { LinkStateBadge } from "@/components/link-state-badge";
import { LocalTime } from "@/components/local-time";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { format, getDictionary } from "@/lib/i18n";
import { type LinkPage, linkState, parsePage } from "@/lib/links";
import { requireUser } from "@/lib/session";

import { CreateLinkForm } from "./create-link-form";
import { ResendVerification } from "./resend-verification";

const PAGE_SIZE = 20;

export default async function DashboardPage({ searchParams }: PageProps<"/">) {
  const user = await requireUser();
  const { locale, t } = await getDictionary();
  const query = await searchParams;
  const page = parsePage(query.page);

  const result = await api<LinkPage>(`/api/v1/links?page=${page}&size=${PAGE_SIZE}`);
  if (!result.ok) {
    throw new Error(`Listing links failed with status ${result.status}`);
  }
  const links = result.data;
  const now = new Date();

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
      <AppHeader user={user} />

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

      {query.deleted === "1" && <FormAlert tone="info" message={t.links.deleted} />}

      <Card>
        <CardHeader>
          <CardTitle>{t.links.createTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          <CreateLinkForm
            labels={{
              targetUrl: t.links.targetUrl,
              targetPlaceholder: t.links.targetPlaceholder,
              alias: t.links.alias,
              aliasHint: t.links.aliasHint,
              expiresAt: t.links.expiresAt,
              create: t.links.create,
            }}
            errors={t.errors}
            unexpected={t.common.unexpected}
            disabled={!user.emailVerified}
          />
        </CardContent>
      </Card>

      <section className="flex flex-col gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{t.dashboard.title}</h1>
        {links.items.length === 0 ? (
          <p className="text-muted-foreground">{t.links.empty}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {links.items.map((link) => {
              const state = linkState(link, now);
              return (
                <li key={link.id} className="flex flex-col gap-2 rounded-lg border p-3 sm:flex-row sm:items-center">
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <Link href={`/links/${link.id}`} className="truncate font-medium hover:underline">
                      {link.shortUrl.replace(/^https?:\/\//, "")}
                    </Link>
                    <p className="truncate text-sm text-muted-foreground">{link.targetUrl}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3 text-xs text-muted-foreground">
                    <LinkStateBadge state={state} label={t.links.state[state]} />
                    <LocalTime iso={link.createdAt} locale={locale} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        {(page > 0 || links.hasNext) && (
          <nav className="flex justify-between">
            {page > 0 ? (
              <Link href={`/?page=${page - 1}`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
                {t.links.previous}
              </Link>
            ) : (
              <span />
            )}
            {links.hasNext && (
              <Link href={`/?page=${page + 1}`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
                {t.links.next}
              </Link>
            )}
          </nav>
        )}
      </section>
    </main>
  );
}
