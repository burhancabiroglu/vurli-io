package io.vurli.account.internal;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Account emails in the user's language. Plain text only: no tracking pixels, no remote
 * content, nothing for a mail client to render unsafely.
 * <p>
 * Tokens travel in the URL fragment ({@code #token=}). Browsers never send fragments to
 * servers, so tokens stay out of access logs and {@code Referer} headers.
 */
public class AccountMailer {

	private final JavaMailSender mailSender;

	private final MessageSource messages;

	private final AccountProperties properties;

	public AccountMailer(JavaMailSender mailSender, MessageSource messages, AccountProperties properties) {
		this.mailSender = mailSender;
		this.messages = messages;
		this.properties = properties;
	}

	public void sendVerification(UserAccount user, String token) {
		send(user, "mail.verify", link("/verify-email#token=" + token));
	}

	public void sendAlreadyRegistered(UserAccount user) {
		send(user, "mail.already-registered", link("/login"), link("/forgot-password"));
	}

	public void sendPasswordReset(UserAccount user, String token) {
		send(user, "mail.reset", link("/reset-password#token=" + token));
	}

	private void send(UserAccount user, String key, Object... arguments) {
		Locale locale = Locale.forLanguageTag(user.locale());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(this.properties.mail().from());
		message.setTo(user.email());
		message.setSubject(this.messages.getMessage(key + ".subject", null, locale));
		message.setText(this.messages.getMessage(key + ".body", arguments, locale));
		this.mailSender.send(message);
	}

	private String link(String path) {
		return this.properties.appBaseUrl() + path;
	}

}
