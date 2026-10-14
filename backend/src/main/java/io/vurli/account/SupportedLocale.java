package io.vurli.account;

import java.util.Locale;

/**
 * Turkish and English (K33). Any other language falls back to English.
 */
final class SupportedLocale {

	private SupportedLocale() {
	}

	static String of(Locale locale) {
		return (locale != null && "tr".equals(locale.getLanguage())) ? "tr" : "en";
	}

}
