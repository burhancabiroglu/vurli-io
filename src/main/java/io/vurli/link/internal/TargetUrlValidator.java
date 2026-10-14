package io.vurli.link.internal;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import io.vurli.link.InvalidTargetUrlException;
import io.vurli.link.InvalidTargetUrlException.Reason;

/**
 * Validates a user supplied redirect target and returns its normalized ASCII form.
 * <p>
 * The normalized form is what gets stored and later written to the {@code Location}
 * header, so it must never contain characters a browser could interpret differently
 * from us (whitespace, control characters, backslashes, credentials).
 */
public class TargetUrlValidator {

	static final int MAX_LENGTH = 2048;

	/** Last label of a public host name: letters only, or an IDN (punycode) label. */
	private static final Pattern TOP_LEVEL_LABEL = Pattern.compile("[a-z]{2,63}|xn--[a-z0-9-]{1,59}");

	private final String shortDomain;

	public TargetUrlValidator(String shortDomain) {
		this.shortDomain = shortDomain.toLowerCase(Locale.ROOT);
	}

	public String validate(String input) {
		if (input == null || input.isBlank()) {
			throw invalid(Reason.BLANK);
		}
		String candidate = input.strip();
		if (candidate.length() > MAX_LENGTH) {
			throw invalid(Reason.TOO_LONG);
		}
		for (int i = 0; i < candidate.length(); i++) {
			char c = candidate.charAt(i);
			// Browsers treat '\' like '/', which lets "https://evil.com\@good.com" mislead users.
			if (c <= 0x20 || c == 0x7f || c == '\\') {
				throw invalid(Reason.ILLEGAL_CHARACTER);
			}
		}

		URI uri = parse(candidate);
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw invalid(Reason.UNSUPPORTED_SCHEME);
		}
		String authority = uri.getRawAuthority();
		if (uri.isOpaque() || authority == null || authority.isEmpty()) {
			throw invalid(Reason.MALFORMED);
		}
		if (authority.contains("@")) {
			throw invalid(Reason.CREDENTIALS);
		}
		if (authority.startsWith("[")) {
			throw invalid(Reason.IP_ADDRESS);
		}

		String host = authority;
		int port = -1;
		int colon = authority.lastIndexOf(':');
		if (colon >= 0) {
			host = authority.substring(0, colon);
			port = parsePort(authority.substring(colon + 1));
		}
		String asciiHost = toAsciiHost(host);
		if (!isDefaultPort(scheme, port)) {
			throw invalid(Reason.NON_DEFAULT_PORT);
		}
		if (asciiHost.equals(this.shortDomain) || asciiHost.endsWith("." + this.shortDomain)) {
			throw invalid(Reason.SELF_REFERENCE);
		}

		StringBuilder normalized = new StringBuilder(scheme).append("://").append(asciiHost);
		normalized.append(uri.getRawPath() != null ? uri.getRawPath() : "");
		if (uri.getRawQuery() != null) {
			normalized.append('?').append(uri.getRawQuery());
		}
		if (uri.getRawFragment() != null) {
			normalized.append('#').append(uri.getRawFragment());
		}
		// Percent-encodes remaining non-ASCII characters; existing escapes are kept as they are.
		String result = parse(normalized.toString()).toASCIIString();
		if (result.length() > MAX_LENGTH) {
			throw invalid(Reason.TOO_LONG);
		}
		return result;
	}

	private static URI parse(String value) {
		try {
			return new URI(value);
		}
		catch (URISyntaxException ex) {
			throw invalid(Reason.MALFORMED);
		}
	}

	private static int parsePort(String value) {
		if (value.isEmpty() || value.length() > 5 || !value.chars().allMatch(Character::isDigit)) {
			throw invalid(Reason.MALFORMED);
		}
		int port = Integer.parseInt(value);
		if (port > 65535) {
			throw invalid(Reason.MALFORMED);
		}
		return port;
	}

	private static String toAsciiHost(String host) {
		String ascii;
		try {
			ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException ex) {
			throw invalid(Reason.INVALID_HOST);
		}
		if (ascii.endsWith(".")) {
			ascii = ascii.substring(0, ascii.length() - 1);
		}
		int lastDot = ascii.lastIndexOf('.');
		if (lastDot <= 0) {
			throw invalid(Reason.INVALID_HOST);
		}
		String topLevel = ascii.substring(lastDot + 1);
		if (topLevel.chars().allMatch(Character::isDigit) || topLevel.startsWith("0x")) {
			// Dotted, decimal or hex IPv4 forms such as 127.0.0.1 or 0x7f.0x1.
			throw invalid(Reason.IP_ADDRESS);
		}
		if (!TOP_LEVEL_LABEL.matcher(topLevel).matches()) {
			throw invalid(Reason.INVALID_HOST);
		}
		return ascii;
	}

	private static boolean isDefaultPort(String scheme, int port) {
		return port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
	}

	private static InvalidTargetUrlException invalid(Reason reason) {
		return new InvalidTargetUrlException(reason);
	}

}
