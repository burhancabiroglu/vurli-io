import "server-only";

import { headers } from "next/headers";

import en from "./dictionaries/en.json";
import tr from "./dictionaries/tr.json";
import { type Locale, negotiateLocale } from "./locales";

export type Dictionary = typeof en;

const dictionaries: Record<Locale, Dictionary> = { en, tr };

/** The visitor's language, from the browser's Accept-Language header. */
export async function getLocale(): Promise<Locale> {
  return negotiateLocale((await headers()).get("accept-language"));
}

export async function getDictionary(): Promise<{ locale: Locale; t: Dictionary }> {
  const locale = await getLocale();
  return { locale, t: dictionaries[locale] };
}

/** Fills {name} placeholders. */
export function format(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => values[key] ?? match);
}
