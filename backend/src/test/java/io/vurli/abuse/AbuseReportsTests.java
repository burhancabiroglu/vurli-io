package io.vurli.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AbuseReportsTests {

	@ParameterizedTest
	@ValueSource(strings = { "https://vurli.io/kafe-menu", "http://vurli.io/kafe-menu/", "vurli.io/kafe-menu",
			"kafe-menu", "  https://vurli.io/kafe-menu?utm=x  ", "https://vurli.io/kafe-menu#top" })
	void findsTheCodeInWhatWasPasted(String pasted) {
		assertThat(AbuseReports.codeOf(pasted)).isEqualTo("kafe-menu");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "ab", "https://vurli.io/", "https://vurli.io/a/b", "kafe menu", "<script>", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"https://vurli.io/%3Cx%3E" })
	void refusesWhatIsNotACode(String pasted) {
		assertThatExceptionOfType(InvalidReportException.class).isThrownBy(() -> AbuseReports.codeOf(pasted))
			.satisfies((ex) -> assertThat(ex.field()).isEqualTo(InvalidReportException.Field.LINK));
	}

}
