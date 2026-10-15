package io.vurli.analytics.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Only the host of a referrer is kept: paths and query strings can carry personal data or
 * tokens.
 */
public final class ReferrerHosts {

	private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*");

	private ReferrerHosts() {
	}

	/**
	 * @return the lower-case host without {@code www.}, or {@code null} for no referrer, a
	 * non-web referrer such as {@code android-app://} or anything malformed
	 */
	public static String hostOf(String referer) {
		if (referer == null || referer.isBlank() || referer.length() > 2048) {
			return null;
		}
		try {
			URI uri = new URI(referer.trim());
			String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
			if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
				return null;
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			if (host.startsWith("www.")) {
				host = host.substring(4);
			}
			return (host.length() <= 253 && HOST.matcher(host).matches()) ? host : null;
		}
		catch (URISyntaxException ex) {
			return null;
		}
	}

}
