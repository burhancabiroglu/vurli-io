package io.vurli.account.internal;

/** RFC 4648 Base32 without padding, the format authenticator apps expect for secrets. */
public final class Base32Codec {

	private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private Base32Codec() {
	}

	public static String encode(byte[] data) {
		StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				out.append(ALPHABET[(buffer >> (bits - 5)) & 0x1f]);
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(ALPHABET[(buffer << (5 - bits)) & 0x1f]);
		}
		return out.toString();
	}

}
