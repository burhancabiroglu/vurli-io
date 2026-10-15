export const locales = ["tr", "en"] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "en";

/**
 * Picks the best supported language from an Accept-Language header, honouring
 * q-values. Anything unsupported falls back to English (K33).
 */
export function negotiateLocale(acceptLanguage: string | null | undefined): Locale {
  if (!acceptLanguage) {
    return defaultLocale;
  }
  const ranked = acceptLanguage
    .split(",")
    .map((part, index) => {
      const [tag, ...params] = part.trim().split(";");
      const q = params
        .map((param) => param.trim())
        .find((param) => param.startsWith("q="));
      const weight = q ? Number.parseFloat(q.slice(2)) : 1;
      return {
        language: tag.trim().toLowerCase().split("-")[0],
        weight: Number.isFinite(weight) ? weight : 0,
        index,
      };
    })
    .filter((entry) => entry.language && entry.weight > 0)
    .sort((a, b) => b.weight - a.weight || a.index - b.index);
  const match = ranked.find((entry) => isLocale(entry.language));
  return match ? (match.language as Locale) : defaultLocale;
}

export function isLocale(value: string | undefined): value is Locale {
  return locales.includes(value as Locale);
}
