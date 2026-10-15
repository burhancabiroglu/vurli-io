package io.vurli.account.internal;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts TOTP secrets at rest with AES-256-GCM (K47). Unlike passwords they cannot be
 * hashed: the server needs them to compute codes. A copy of the database alone is not
 * enough to generate codes; the key lives in configuration. GCM also detects tampering.
 * Stored layout: 12-byte nonce followed by ciphertext and tag.
 */
public final class SecretCipher {

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;

	private final SecureRandom random = new SecureRandom();

	/**
	 * @param base64Key 32 random bytes, Base64 encoded ({@code openssl rand -base64 32})
	 */
	public SecretCipher(String base64Key) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("vurli.admin.totp-key must be Base64", ex);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("vurli.admin.totp-key must be 32 bytes (openssl rand -base64 32)");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	public byte[] encrypt(byte[] plain) {
		byte[] nonce = new byte[NONCE_BYTES];
		this.random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] sealed = cipher.doFinal(plain);
			return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	/**
	 * @throws IllegalStateException if the data was changed or another key was used
	 */
	public byte[] decrypt(byte[] stored) {
		if (stored == null || stored.length <= NONCE_BYTES) {
			throw new IllegalStateException("Not an encrypted secret");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, this.key,
					new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(stored, 0, NONCE_BYTES)));
			return cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Secret cannot be decrypted with the configured key", ex);
		}
	}

}
