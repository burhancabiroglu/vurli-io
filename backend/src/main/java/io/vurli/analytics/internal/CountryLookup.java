package io.vurli.analytics.internal;

import java.net.InetAddress;

/** The country an address belongs to, as an ISO 3166 code, or {@code null}. */
@FunctionalInterface
public interface CountryLookup {

	CountryLookup NONE = (address) -> null;

	String country(InetAddress address);

}
