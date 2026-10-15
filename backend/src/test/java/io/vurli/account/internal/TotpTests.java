package io.vurli.account.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TotpTests {

	/** The SHA-1 seed of RFC 6238 appendix B. */
	private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	@ParameterizedTest
	@CsvSource({ "59, 94287082", "1111111109, 07081804", "1111111111, 14050471", "1234567890, 89005924",
			"2000000000, 69279037", "20000000000, 65353130" })
	void matchesTheRfc6238TestVectors(long epochSeconds, String expected) {
		assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(epochSeconds)), 8)).isEqualTo(expected);
	}

	@Test
	void sixDigitCodesAreTheLastSixOfTheRfcValue() {
		// RFC 4226 appendix D: HOTP values for counters 0 and 1.
		assertThat(Totp.code(RFC_KEY, 0, 6)).isEqualTo("755224");
		assertThat(Totp.code(RFC_KEY, 1, 6)).isEqualTo("287082");
	}

	@Test
	void acceptsOneStepOfClockDriftEitherWay() {
		Instant now = Instant.ofEpochSecond(1_111_111_111);
		long step = Totp.step(now);

		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step, 6), now, -1)).hasValue(step);
		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step - 1, 6), now, -1)).hasValue(step - 1);
		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step + 1, 6), now, -1)).hasValue(step + 1);
		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step - 2, 6), now, -1)).isEmpty();
		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step + 2, 6), now, -1)).isEmpty();
	}

	@Test
	void aUsedCodeCannotBeUsedAgain() {
		Instant now = Instant.ofEpochSecond(1_111_111_111);
		long step = Totp.step(now);
		String code = Totp.code(RFC_KEY, step, 6);

		assertThat(Totp.verify(RFC_KEY, code, now, step)).isEmpty();
		// Nor an older one that is still inside the window.
		assertThat(Totp.verify(RFC_KEY, Totp.code(RFC_KEY, step - 1, 6), now, step)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "12345", "1234567", "12345a", " 123456", "１２３４５６" })
	void rejectsCodesThatAreNotSixDigits(String code) {
		assertThat(Totp.verify(RFC_KEY, code, Instant.now(), -1)).isEmpty();
	}

	@Test
	void base32MatchesTheRfc4648Vectors() {
		assertThat(Base32Codec.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
		assertThat(Base32Codec.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
		assertThat(Base32Codec.encode(RFC_KEY)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
	}

}
