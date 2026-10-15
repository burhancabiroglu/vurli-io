package io.vurli.abuse;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import io.vurli.abuse.internal.ClientAddressProperties;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

/**
 * Where a request came from, as a key for rate limits. Never stored (K23).
 */
@Component
public class ClientAddresses {

	static final String FORWARDED_FOR = "X-Forwarded-For";

	/**
	 * Dotted-quad IPv4 or an IPv6 literal. Java also accepts legacy IPv4 forms such as
	 * {@code 1.2.3}; those never come from a real proxy and are rejected here.
	 */
	private static final Pattern LITERAL = Pattern.compile("(\\d{1,3}(\\.\\d{1,3}){3})|([0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*)");

	private final boolean trustForwardedFor;

	public ClientAddresses(ClientAddressProperties properties) {
		this.trustForwardedFor = properties.trustForwardedFor();
	}

	/**
	 * The client's address, or for IPv6 its /64 network: one home or server usually gets
	 * a whole /64, so per-address IPv6 limits would be trivial to sidestep.
	 * <p>
	 * When trusted, the last {@code X-Forwarded-For} entry is used: our own proxy appends
	 * it, so earlier entries may be forged. Otherwise, or if that entry is not an address,
	 * the connection's address.
	 */
	public String rateLimitKey(HttpServletRequest request) {
		Optional<InetAddress> forwarded = Optional.empty();
		if (this.trustForwardedFor) {
			String header = request.getHeader(FORWARDED_FOR);
			if (header != null) {
				forwarded = parse(header.substring(header.lastIndexOf(',') + 1).trim());
			}
		}
		return forwarded.or(() -> parse(request.getRemoteAddr()))
			.map(ClientAddresses::key)
			.orElse("unknown");
	}

	private static String key(InetAddress address) {
		if (address instanceof Inet6Address) {
			byte[] network = new byte[8];
			System.arraycopy(address.getAddress(), 0, network, 0, 8);
			return HexFormat.of().formatHex(network) + "/64";
		}
		return address.getHostAddress();
	}

	/** A literal IP address only; no name is ever looked up. */
	private static Optional<InetAddress> parse(String value) {
		if (value == null || value.length() > 45 || !LITERAL.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(InetAddress.ofLiteral(value));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
