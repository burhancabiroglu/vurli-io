import { cn } from "cn";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";

import { AppHeader } from "@/components/app-header";
import { FormAlert } from "@/components/form-alert";
import { LinkStateBadge } from "@/components/link-state-badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { format, getDictionary } from "@/lib/i18n";
import { type Link as VurliLink, isLinkId, linkState } from "@/lib/links";
import { requireUser } from "@/lib/session";

import { CopyButton, DeleteForm, ExpiryForm, StatusForm, TargetForm } from "./link-forms";

export default async function LinkPage({ params, searchParams }: PageProps<"/links/[id]">) {
  const user = await requireUser();
  const { id } = await params;
  if (!isLinkId(id)) {
    notFound();
  }
  const result = await api<VurliLink>(`/api/v1/links/${id}`);
  if (!result.ok) {
    if (result.status === 401) {
      redirect("/login");
    }
    if (result.status === 404) {
      notFound();
    }
    throw new Error(`Loading link failed with status ${result.status}`);
  }

  const link = result.data;
  const { t } = await getDictionary();
  const { created } = await searchParams;
  const state = linkState(link, new Date());
  const blocked = link.status === "BLOCKED";
  const target = { id: link.id, version: link.version };
  const messages = { errors: t.errors, unexpected: t.common.unexpected, saved: t.link.saved };
  const display = link.shortUrl.replace(/^https?:\/\//, "");
  const qr = `/links/${link.id}/qr`;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
      <AppHeader user={user} />
      <Link href="/" className="text-sm text-muted-foreground hover:underline">
        ← {t.link.back}
      </Link>

      {created === "1" && <FormAlert tone="info" message={t.link.createdNotice} />}

      <section className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight break-all">{display}</h1>
          <LinkStateBadge state={state} label={t.links.state[state]} />
        </div>
        <div className="flex flex-wrap gap-2">
          <CopyButton text={link.shortUrl} labels={{ copy: t.link.copy, copied: t.link.copied }} />
          <a
            href={link.shortUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            {t.link.open}
          </a>
        </div>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>{t.link.qrTitle}</CardTitle>
          <CardDescription>{t.link.qrHint}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-start gap-4 sm:flex-row">
          {/* eslint-disable-next-line @next/next/no-img-element -- generated per link, nothing to optimize */}
          <img
            src={`${qr}?format=svg`}
            alt={format(t.link.qrAlt, { url: display })}
            width={160}
            height={160}
            className="rounded-md border"
          />
          <div className="flex flex-wrap gap-2">
            <a href={`${qr}?format=png`} download className={cn(buttonVariants({ size: "sm" }))}>
              {t.link.downloadPng}
            </a>
            <a href={`${qr}?format=svg`} download className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
              {t.link.downloadSvg}
            </a>
          </div>
        </CardContent>
      </Card>

      {blocked && <FormAlert message={t.link.blockedHint} />}

      <Card>
        <CardHeader>
          <CardTitle>{t.link.targetTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          <TargetForm
            link={target}
            targetUrl={link.targetUrl}
            label={t.link.targetTitle}
            submit={t.link.save}
            disabled={blocked}
            messages={messages}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t.link.expiryTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          <ExpiryForm
            link={target}
            expiresAt={link.expiresAt}
            label={t.link.expiryTitle}
            hint={t.link.expiryHint}
            submit={t.link.save}
            disabled={blocked}
            messages={messages}
          />
        </CardContent>
      </Card>

      {!blocked && (
        <Card>
          <CardHeader>
            <CardTitle>{t.link.statusTitle}</CardTitle>
            <CardDescription>{t.link.disabledHint}</CardDescription>
          </CardHeader>
          <CardContent>
            <StatusForm
              link={target}
              enable={link.status === "DISABLED"}
              label={link.status === "DISABLED" ? t.link.enable : t.link.disable}
              messages={messages}
            />
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t.link.deleteTitle}</CardTitle>
          <CardDescription>{t.link.deleteHint}</CardDescription>
        </CardHeader>
        <CardContent>
          <DeleteForm
            link={target}
            labels={{ delete: t.link.delete, confirmDelete: t.link.confirmDelete, cancel: t.link.cancel }}
            messages={messages}
          />
        </CardContent>
      </Card>
    </main>
  );
}
