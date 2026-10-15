package io.vurli.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.vurli.analytics.DeviceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAgentsTests {

	@ParameterizedTest
	@ValueSource(strings = {
			"Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
			"Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
			"facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
			"WhatsApp/2.23.20.0 A",
			"Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
			"Twitterbot/1.0",
			"TelegramBot (like TwitterBot)",
			"Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)",
			"LinkedInBot/1.0 (compatible; Mozilla/5.0; Apache-HttpClient +http://www.linkedin.com)",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 (Applebot/0.1)",
			"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36",
			"curl/8.4.0", "Wget/1.21.4", "python-requests/2.31.0", "Go-http-client/2.0", "okhttp/4.12.0",
			"Java/21.0.1", "axios/1.6.2", "UptimeRobot/2.0", "Playwright/1.64.0 (arm64; macOS 15.0) node/22.20" })
	void automatedAgentsAreBots(String userAgent) {
		assertThat(UserAgents.isBot(userAgent)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void missingAgentIsTreatedAsABot(String userAgent) {
		assertThat(UserAgents.isBot(userAgent)).isTrue();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36|DESKTOP",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15|DESKTOP",
			"Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0|DESKTOP",
			"Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1|MOBILE",
			"Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36|MOBILE",
			"Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36|TABLET",
			"Mozilla/5.0 (iPad; CPU OS 16_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1|TABLET",
			"Mozilla/5.0 (Linux; Android 13; Instagram 300.0.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0 Mobile Safari/537.36|MOBILE",
			"SomethingUnknown/1.0|OTHER" })
	void browsersAreClassifiedByDevice(String userAgent, DeviceType expected) {
		assertThat(UserAgents.isBot(userAgent)).isFalse();
		assertThat(UserAgents.deviceType(userAgent)).isEqualTo(expected);
	}

}
