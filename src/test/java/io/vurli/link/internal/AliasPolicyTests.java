package io.vurli.link.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.vurli.link.InvalidAliasException;
import io.vurli.link.InvalidAliasException.Reason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AliasPolicyTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			kafe-menu      | kafe-menu
			Kafe-Menu      | kafe-menu
			  yaz2026      | yaz2026
			abc            | abc
			a-b            | a-b
			""")
	void acceptsAndLowercases(String input, String expected) {
		assertThat(AliasPolicy.normalize(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ab", "-menu", "menu-", "kafe menu", "kafe_menu", "kafe/menu", "kafe.menu",
			"menü", "a%2Fb" })
	void rejectsInvalidFormat(String input) {
		assertReason(input, Reason.FORMAT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "api", "Admin", "login", "actuator", "abuse" })
	void rejectsReservedNames(String input) {
		assertReason(input, Reason.RESERVED);
	}

	@ParameterizedTest
	@ValueSource(ints = { 3, 50 })
	void acceptsLengthLimits(int length) {
		assertThat(AliasPolicy.normalize("a".repeat(length))).hasSize(length);
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 51 })
	void rejectsOutsideLengthLimits(int length) {
		assertReason("a".repeat(length), Reason.FORMAT);
	}

	private void assertReason(String input, Reason reason) {
		assertThatExceptionOfType(InvalidAliasException.class).isThrownBy(() -> AliasPolicy.normalize(input))
			.satisfies(ex -> assertThat(ex.reason()).isEqualTo(reason));
	}

}
