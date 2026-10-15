package io.vurli.account;

/**
 * A second factor waiting for its first code.
 * @param secret the Base32 secret, for typing into an authenticator app by hand
 * @param uri the {@code otpauth://} URI, shown as a QR code
 */
public record TotpEnrollment(String secret, String uri) {
}
