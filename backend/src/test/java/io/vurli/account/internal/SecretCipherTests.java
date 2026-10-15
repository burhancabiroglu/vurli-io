package io.vurli.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretCipherTests {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private final SecretCipher cipher = new SecretCipher(KEY);

	@Test
	void roundTripsWithAFreshNonceEachTime() {
		byte[] secret = "a totp secret!!!".getBytes();

		byte[] first = this.cipher.encrypt(secret);
		byte[] second = this.cipher.encrypt(secret);

		assertThat(first).isNotEqualTo(second);
		assertThat(this.cipher.decrypt(first)).isEqualTo(secret);
		assertThat(this.cipher.decrypt(second)).isEqualTo(secret);
	}

	@Test
	void detectsTamperingAndTheWrongKey() {
		byte[] stored = this.cipher.encrypt("secret".getBytes());
		byte[] tampered = stored.clone();
		tampered[tampered.length - 1] ^= 1;
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;

		assertThatIllegalStateException().isThrownBy(() -> this.cipher.decrypt(tampered));
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(otherKey)).decrypt(stored));
	}

	@Test
	void refusesKeysOfTheWrongSize() {
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher(""));
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher(null));
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher("not base64!"));
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])));
	}

}
