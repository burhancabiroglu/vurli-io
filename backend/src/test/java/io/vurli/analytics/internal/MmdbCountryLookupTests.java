package io.vurli.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.net.InetAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Against the real DB-IP file, which is not in the repository: run
 * {@code scripts/download-geoip.sh} first. Without the file these tests are skipped, so
 * the build never depends on db-ip.com being reachable.
 * <p>
 * The addresses belong to national academic networks (ULAKBIM in Turkey, WIDE and Osaka
 * University in Japan), whose country does not move between monthly releases. Anycast
 * addresses such as Google's resolvers do: DB-IP placed 2001:4860:4860::8888 in Canada.
 */
class MmdbCountryLookupTests {

	static final File DATABASE = new File("data/geoip/dbip-country-lite.mmdb");

	private MmdbCountryLookup lookup;

	@BeforeEach
	void open() throws Exception {
		assumeTrue(DATABASE.isFile(), "DB-IP database not downloaded");
		this.lookup = new MmdbCountryLookup(DATABASE);
	}

	@AfterEach
	void close() throws Exception {
		if (this.lookup != null) {
			this.lookup.close();
		}
	}

	@Test
	void findsCountriesForIpv4AndIpv6() throws Exception {
		assertThat(this.lookup.databaseType()).containsIgnoringCase("country");
		assertThat(this.lookup.country(InetAddress.ofLiteral("193.140.100.100"))).isEqualTo("TR");
		assertThat(this.lookup.country(InetAddress.ofLiteral("2001:a98::1"))).isEqualTo("TR");
		assertThat(this.lookup.country(InetAddress.ofLiteral("133.1.1.1"))).isEqualTo("JP");
		assertThat(this.lookup.country(InetAddress.ofLiteral("2001:200::1"))).isEqualTo("JP");
	}

	@Test
	void privateAndMissingAddressesHaveNoCountry() throws Exception {
		assertThat(this.lookup.country(InetAddress.ofLiteral("10.1.2.3"))).isNull();
		assertThat(this.lookup.country(InetAddress.ofLiteral("127.0.0.1"))).isNull();
		assertThat(this.lookup.country(null)).isNull();
	}

}
