package io.vurli.link;

import java.util.Locale;

public enum QrFormat {

	PNG("image/png"), SVG("image/svg+xml");

	private final String mediaType;

	QrFormat(String mediaType) {
		this.mediaType = mediaType;
	}

	public String mediaType() {
		return this.mediaType;
	}

	public String extension() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * @throws IllegalArgumentException for anything but {@code png} or {@code svg}
	 */
	public static QrFormat fromExtension(String extension) {
		for (QrFormat format : values()) {
			if (format.extension().equalsIgnoreCase(extension)) {
				return format;
			}
		}
		throw new IllegalArgumentException("Unsupported QR format");
	}

}
