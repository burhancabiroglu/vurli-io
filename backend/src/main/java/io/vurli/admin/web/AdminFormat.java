package io.vurli.admin.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/** Formatting for the admin templates ({@code ${@adminFormat.time(...)}}); times in UTC. */
@Component("adminFormat")
class AdminFormat {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
		.withZone(ZoneOffset.UTC);

	public String time(Instant instant) {
		return (instant != null) ? TIME.format(instant) : "";
	}

}
