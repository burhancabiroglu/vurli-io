package io.vurli.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReferrerHostsTests {

	@ParameterizedTest
	@CsvSource({ "https://www.instagram.com/,instagram.com", "https://l.instagram.com/?u=https%3A%2F%2Fvurli.io,l.instagram.com",
			"http://Example.COM/path?token=secret#x,example.com", "https://t.co/abc,t.co",
			"https://xn--kafe-ura.com.tr/menu,xn--kafe-ura.com.tr" })
	void keepsOnlyTheHost(String referer, String host) {
		assertThat(ReferrerHosts.hostOf(referer)).isEqualTo(host);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "android-app://com.google.android.gm/", "javascript:alert(1)", "not a url",
			"https:///nohost", "ftp://example.com/file", "https://exa mple.com/", "https://ex_ample.com/" })
	void dropsWhatIsNotAWebReferrer(String referer) {
		assertThat(ReferrerHosts.hostOf(referer)).isNull();
	}

}
