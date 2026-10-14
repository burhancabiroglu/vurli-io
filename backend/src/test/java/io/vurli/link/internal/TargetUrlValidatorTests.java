package io.vurli.link.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.vurli.link.InvalidTargetUrlException;
import io.vurli.link.InvalidTargetUrlException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetUrlValidatorTests {

	private final TargetUrlValidator validator = new TargetUrlValidator("vurli.io");

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			https://example.com                              | https://example.com
			https://example.com/menu?table=4#top             | https://example.com/menu?table=4#top
			HTTPS://Example.COM/Path                         | https://example.com/Path
			  https://example.com/trimmed                    | https://example.com/trimmed
			http://example.com:80/a                          | http://example.com/a
			https://example.com:443/a                        | https://example.com/a
			https://example.com./a                           | https://example.com/a
			https://sub.example.co.uk/a%20b                  | https://sub.example.co.uk/a%20b
			https://example.com/a%2Fb                        | https://example.com/a%2Fb
			https://müşteri.com.tr/menü                      | https://xn--mteri-kva70e.com.tr/men%C3%BC
			https://example.com/?redirect=https://other.com  | https://example.com/?redirect=https://other.com
			https://example.com/?q=@evil.com                 | https://example.com/?q=@evil.com
			https://notvurli.io/a                            | https://notvurli.io/a
			""")
	void acceptsAndNormalizes(String input, String expected) {
		assertThat(this.validator.validate(input)).isEqualTo(expected);
	}

	@Test
	void keepsUrlAtMaximumLength() {
		String url = "https://example.com/" + "a".repeat(TargetUrlValidator.MAX_LENGTH - 20);

		assertThat(this.validator.validate(url)).hasSize(TargetUrlValidator.MAX_LENGTH);
	}

	@Test
	void rejectsUrlOverMaximumLength() {
		String url = "https://example.com/" + "a".repeat(TargetUrlValidator.MAX_LENGTH - 19);

		assertReason(url, Reason.TOO_LONG);
	}

	@Test
	void rejectsUrlThatGrowsOverMaximumLengthWhenEncoded() {
		String url = "https://example.com/" + "ü".repeat(TargetUrlValidator.MAX_LENGTH / 2);

		assertReason(url, Reason.TOO_LONG);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void rejectsBlank(String input) {
		assertReason(input, Reason.BLANK);
	}

	@Test
	void rejectsNull() {
		assertReason(null, Reason.BLANK);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://example.com/a b", "https://example.com/a\tb", "https://example.com/a\nb",
			"https://example.com/a\r\nLocation: https://evil.com", "https://evil.com\\@example.com",
			"https://example.com/\u007f" })
	void rejectsCharactersBrowsersInterpretDifferently(String input) {
		assertReason(input, Reason.ILLEGAL_CHARACTER);
	}

	@ParameterizedTest
	@ValueSource(strings = { "javascript:alert(1)", "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", "ftp://example.com",
			"file:///etc/passwd", "//example.com/a", "example.com", "mailto:someone@example.com" })
	void rejectsUnsupportedSchemes(String input) {
		assertReason(input, Reason.UNSUPPORTED_SCHEME);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://example.com@evil.com", "https://user:pass@example.com",
			"https://@example.com" })
	void rejectsCredentials(String input) {
		assertReason(input, Reason.CREDENTIALS);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://127.0.0.1/", "http://10.0.0.1:80/", "https://0x7f.0x1/", "https://[::1]/",
			"https://[2001:db8::1]/a" })
	void rejectsIpAddresses(String input) {
		assertReason(input, Reason.IP_ADDRESS);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://localhost/", "https://2130706433/", "https://exa_mple.com/",
			"https://%65vil.com/", "https://example.c0m/", "https://-example.com/" })
	void rejectsInvalidHosts(String input) {
		assertReason(input, Reason.INVALID_HOST);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://example.com:8443/", "http://example.com:443/", "https://example.com:80/" })
	void rejectsNonDefaultPorts(String input) {
		assertReason(input, Reason.NON_DEFAULT_PORT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://", "http:/example.com", "https://example.com:/", "https://example.com:99999/",
			"https://example.com/%zz" })
	void rejectsMalformed(String input) {
		assertReason(input, Reason.MALFORMED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://vurli.io/abc", "https://VURLI.IO/abc", "https://app.vurli.io/",
			"https://vurli.io./abc" })
	void rejectsLinksToItself(String input) {
		assertReason(input, Reason.SELF_REFERENCE);
	}

	private void assertReason(String input, Reason reason) {
		assertThatExceptionOfType(InvalidTargetUrlException.class).isThrownBy(() -> this.validator.validate(input))
			.satisfies(ex -> assertThat(ex.reason()).isEqualTo(reason));
	}

}
