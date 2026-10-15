import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { Dictionary } from "@/lib/i18n";
import { type LinkStatistics, type Share, percent, withOther } from "@/lib/links";

import { ClicksChart } from "./clicks-chart";

type Props = { stats: LinkStatistics; locale: string; t: Dictionary };

function Breakdown({
  title,
  shares,
  total,
  name,
  otherLabel,
}: {
  title: string;
  shares: Share[];
  total: number;
  name: (key: string | null) => string;
  otherLabel: string;
}) {
  const { other } = withOther(shares, total);
  const rows = shares.map((share) => ({ label: name(share.key), clicks: share.clicks }));
  if (other > 0) {
    rows.push({ label: otherLabel, clicks: other });
  }
  return (
    <section className="flex min-w-0 flex-col gap-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <ul className="flex flex-col gap-1 text-sm">
        {rows.map((row) => (
          <li key={row.label} className="flex justify-between gap-3">
            <span className="truncate">{row.label}</span>
            <span className="shrink-0 text-muted-foreground tabular-nums">
              {row.clicks} · %{percent(row.clicks, total)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Statistics of one link. Referrers and countries are shown as text only, never as links. */
export function LinkStats({ stats, locale, t }: Props) {
  const regions = new Intl.DisplayNames([locale], { type: "region" });
  const devices = t.stats.device as Record<string, string>;
  const country = (code: string | null) => (code ? (regions.of(code) ?? code) : t.stats.unknownCountry);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.stats.title}</CardTitle>
        <CardDescription>{t.stats.note}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <dl className="grid grid-cols-2 gap-4">
          <div>
            <dt className="text-sm text-muted-foreground">{t.stats.total}</dt>
            <dd className="text-2xl font-semibold tabular-nums" data-testid="total-clicks">
              {stats.totalClicks}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">{t.stats.last30}</dt>
            <dd className="text-2xl font-semibold tabular-nums">{stats.windowClicks}</dd>
          </div>
        </dl>

        <ClicksChart days={stats.days} locale={locale} label={t.stats.chartLabel} />

        {stats.windowClicks === 0 ? (
          <p className="text-sm text-muted-foreground">{t.stats.empty}</p>
        ) : (
          <div className="grid gap-6 sm:grid-cols-3">
            <Breakdown
              title={t.stats.referrers}
              shares={stats.referrers}
              total={stats.windowClicks}
              name={(key) => key ?? t.stats.direct}
              otherLabel={t.stats.other}
            />
            <Breakdown
              title={t.stats.countries}
              shares={stats.countries}
              total={stats.windowClicks}
              name={country}
              otherLabel={t.stats.other}
            />
            <Breakdown
              title={t.stats.devices}
              shares={stats.devices}
              total={stats.windowClicks}
              name={(key) => (key ? (devices[key] ?? key) : t.stats.device.OTHER)}
              otherLabel={t.stats.other}
            />
          </div>
        )}

        {/* DB-IP's CC BY 4.0 licence requires this attribution wherever its data is shown (K44). */}
        <p className="text-xs text-muted-foreground">
          {t.stats.attribution}{" "}
          <a href="https://db-ip.com" target="_blank" rel="noopener noreferrer" className="underline">
            IP Geolocation by DB-IP
          </a>
        </p>
      </CardContent>
    </Card>
  );
}
