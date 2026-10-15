package io.vurli.analytics.internal;

import java.util.Locale;
import java.util.regex.Pattern;

import io.vurli.analytics.DeviceType;

/**
 * Rough User-Agent classification. Statistics are approximate by design (product rule 6);
 * this errs towards not counting automated traffic.
 */
public final class UserAgents {

	/**
	 * Crawlers, link preview fetchers (WhatsApp, Slack, X, Facebook, Telegram, Discord,
	 * LinkedIn...), monitoring and HTTP libraries. {@code bot\b} matches Googlebot/2.1,
	 * Slackbot-LinkExpanding and Twitterbot without matching words that merely contain it.
	 */
	private static final Pattern AUTOMATED = Pattern.compile("bot\\b|crawl|spider|slurp|preview|"
			+ "facebookexternalhit|facebookcatalog|whatsapp|skypeuripreview|embedly|quora link|pinterest|vkshare|"
			+ "headless|playwright|puppeteer|selenium|phantomjs|lighthouse|pagespeed|monitor|uptime|pingdom|feedfetcher|mediapartners|"
			+ "curl/|wget/|python|httpclient|okhttp|go-http-client|java/|libwww|axios|node-fetch|scrapy|postman",
			Pattern.CASE_INSENSITIVE);

	private UserAgents() {
	}

	/** No User-Agent at all is treated as automated too: every browser sends one. */
	public static boolean isBot(String userAgent) {
		return userAgent == null || userAgent.isBlank() || AUTOMATED.matcher(userAgent).find();
	}

	/**
	 * iPadOS presents itself as a Mac and is counted as desktop; Android tablets omit
	 * "Mobile".
	 */
	public static DeviceType deviceType(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return DeviceType.OTHER;
		}
		String agent = userAgent.toLowerCase(Locale.ROOT);
		if (agent.contains("ipad") || agent.contains("tablet")
				|| (agent.contains("android") && !agent.contains("mobile"))) {
			return DeviceType.TABLET;
		}
		if (agent.contains("mobi") || agent.contains("iphone") || agent.contains("ipod") || agent.contains("android")) {
			return DeviceType.MOBILE;
		}
		if (agent.contains("windows") || agent.contains("macintosh") || agent.contains("x11") || agent.contains("cros")
				|| agent.contains("linux")) {
			return DeviceType.DESKTOP;
		}
		return DeviceType.OTHER;
	}

}
