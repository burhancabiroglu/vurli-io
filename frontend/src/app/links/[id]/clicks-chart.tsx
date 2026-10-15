"use client";

import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";

import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { useIsClient } from "@/hooks/use-is-client";

type Props = {
  days: { day: string; clicks: number }[];
  locale: string;
  label: string;
};

/**
 * Daily clicks of the last 30 days. Drawn only in the browser: Recharts sizes itself
 * with inline style attributes, which the production CSP rejects in server-rendered HTML
 * (styles set from JavaScript are allowed).
 */
export function ClicksChart({ days, locale, label }: Props) {
  const isClient = useIsClient();
  // Days are UTC dates; formatting them in UTC keeps "15 Oct" from becoming the 14th.
  const dayFormat = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", timeZone: "UTC" });
  const formatDay = (day: string) => dayFormat.format(new Date(`${day}T00:00:00Z`));
  const config = { clicks: { label } } satisfies ChartConfig;

  if (!isClient) {
    return <div className="h-48 w-full" aria-hidden="true" />;
  }
  return (
    <ChartContainer config={config} className="aspect-auto h-48 w-full [--color-clicks:var(--primary)]">
      <BarChart data={days} margin={{ left: 0, right: 0 }} accessibilityLayer>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="day" tickLine={false} axisLine={false} minTickGap={24} tickFormatter={formatDay} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={32} />
        <ChartTooltip
          cursor={false}
          content={<ChartTooltipContent labelFormatter={(value) => formatDay(String(value))} />}
        />
        <Bar dataKey="clicks" fill="var(--color-clicks)" radius={3} />
      </BarChart>
    </ChartContainer>
  );
}
