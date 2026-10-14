package io.vurli.link.internal;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.vurli.link.InvalidAliasException;
import io.vurli.link.InvalidAliasException.Reason;

/**
 * Rules for user chosen aliases such as {@code vurli.io/kafe-menu}.
 * <p>
 * Aliases are stored lowercase so that look-alike variants ("Kafe-Menu" and "kafe-menu")
 * can never point to different targets.
 */
public final class AliasPolicy {

	/** 3-50 characters, lowercase letters, digits and hyphens, no hyphen at either end. */
	private static final Pattern ALIAS = Pattern.compile("[a-z0-9][a-z0-9-]{1,48}[a-z0-9]");

	/** Paths the application uses itself or that could impersonate the service. */
	private static final Set<String> RESERVED = Set.of("about", "abuse", "account", "actuator", "admin", "api",
			"app", "assets", "auth", "billing", "blog", "contact", "css", "dashboard", "docs", "error", "favicon",
			"health", "help", "images", "img", "js", "legal", "login", "logout", "mail", "privacy", "register",
			"report", "reset", "security", "settings", "signin", "signup", "static", "status", "support", "terms",
			"verify", "vurli", "www");

	private AliasPolicy() {
	}

	public static String normalize(String alias) {
		String normalized = alias.strip().toLowerCase(Locale.ROOT);
		if (!ALIAS.matcher(normalized).matches()) {
			throw new InvalidAliasException(Reason.FORMAT);
		}
		if (isReserved(normalized)) {
			throw new InvalidAliasException(Reason.RESERVED);
		}
		return normalized;
	}

	public static boolean isReserved(String code) {
		return RESERVED.contains(code.toLowerCase(Locale.ROOT));
	}

}
