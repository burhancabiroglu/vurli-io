package io.vurli.link.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RandomCodeGeneratorTests {

	private final RandomCodeGenerator generator = new RandomCodeGenerator();

	@Test
	void generatesSevenBase62Characters() {
		for (int i = 0; i < 1_000; i++) {
			assertThat(this.generator.next()).hasSize(7).matches("[0-9A-Za-z]{7}");
		}
	}

	@Test
	void usesTheWholeAlphabet() {
		Set<Character> seen = new HashSet<>();
		for (int i = 0; i < 2_000; i++) {
			this.generator.next().chars().forEach(c -> seen.add((char) c));
		}

		assertThat(seen).hasSize(RandomCodeGenerator.ALPHABET.length());
	}

}
