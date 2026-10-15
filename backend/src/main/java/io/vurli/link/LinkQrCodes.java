package io.vurli.link;

import java.util.UUID;

import io.vurli.qr.QrCodeRenderer;

import org.springframework.stereotype.Service;

/**
 * QR codes encode the short link, never the target, so a printed code keeps working
 * when the owner changes where the link points.
 */
@Service
public class LinkQrCodes {

	private final LinkManagement links;

	public LinkQrCodes(LinkManagement links) {
		this.links = links;
	}

	/**
	 * @throws LinkNotFoundException if the link is not in this workspace or was deleted
	 */
	public QrCode render(UUID workspaceId, UUID linkId, QrFormat format) {
		LinkView link = this.links.get(workspaceId, linkId);
		byte[] content = switch (format) {
			case PNG -> QrCodeRenderer.png(link.shortUrl());
			case SVG -> QrCodeRenderer.svg(link.shortUrl());
		};
		return new QrCode(link.code(), format, content);
	}

}
