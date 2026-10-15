package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import io.vurli.abuse.internal.ClientAddressProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressesTests {

	private final ClientAddresses trusting = new ClientAddresses(new ClientAddressProperties(true));

	@Test
	void usesTheConnectionWithoutForwardedFor() {
		assertThat(this.trusting.rateLimitKey(request("192.0.2.7", null))).isEqualTo("192.0.2.7");
	}

	@Test
	void usesTheLastForwardedEntryBecauseEarlierOnesCanBeForged() {
		assertThat(this.trusting.rateLimitKey(request("10.0.0.2", "203.0.113.9"))).isEqualTo("203.0.113.9");
		assertThat(this.trusting.rateLimitKey(request("10.0.0.2", "1.1.1.1, 8.8.8.8 ,203.0.113.9")))
			.isEqualTo("203.0.113.9");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "unknown", "evil.com", "localhost", "203.0.113.9:443", "1.2.3", "999.1.1.1",
			"fe80::1%eth0", "1.1.1.1, ", "1111111111111111111111111111111111111111111111111" })
	void ignoresForwardedValuesThatAreNotAddresses(String header) {
		assertThat(this.trusting.rateLimitKey(request("192.0.2.7", header))).isEqualTo("192.0.2.7");
	}

	@Test
	void ignoresForwardedForUnlessTrusted() {
		ClientAddresses direct = new ClientAddresses(new ClientAddressProperties(false));

		assertThat(direct.rateLimitKey(request("192.0.2.7", "203.0.113.9"))).isEqualTo("192.0.2.7");
	}

	@Test
	void groupsIpv6ByNetworkSoOneHostCannotRotateAddresses() {
		String first = this.trusting.rateLimitKey(request("10.0.0.2", "2001:db8:1:2::1"));
		String second = this.trusting.rateLimitKey(request("10.0.0.2", "2001:db8:1:2:ffff:ffff:ffff:ffff"));
		String otherNetwork = this.trusting.rateLimitKey(request("10.0.0.2", "2001:db8:1:3::1"));

		assertThat(first).isEqualTo(second).isEqualTo("20010db800010002/64");
		assertThat(otherNetwork).isNotEqualTo(first);
	}

	@Test
	void treatsIpv4MappedIpv6AsIpv4() {
		assertThat(this.trusting.rateLimitKey(request("10.0.0.2", "::ffff:203.0.113.9"))).isEqualTo("203.0.113.9");
	}

	private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddress);
		if (forwardedFor != null) {
			request.addHeader(ClientAddresses.FORWARDED_FOR, forwardedFor);
		}
		return request;
	}

}
