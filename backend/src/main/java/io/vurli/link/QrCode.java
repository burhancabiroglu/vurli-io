package io.vurli.link;

/**
 * A rendered QR code of a short link.
 * @param code the link's code, for naming the download
 */
public record QrCode(String code, QrFormat format, byte[] content) {

	public String fileName() {
		return "vurli-" + this.code + "." + this.format.extension();
	}

}
