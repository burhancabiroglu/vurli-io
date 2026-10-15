package io.vurli.link.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/**
 * Draws QR codes from ZXing's module matrix. Error correction level M survives about 15%
 * damage, enough for a printed table card. The quiet zone is the four modules the
 * standard requires; scanners fail without it.
 */
public final class QrCodeRenderer {

	static final int QUIET_ZONE = 4;

	/** Large enough to print sharply at a few centimetres. */
	static final int PNG_TARGET_SIZE = 1024;

	private QrCodeRenderer() {
	}

	public static byte[] png(String text) {
		ByteMatrix matrix = encode(text);
		int modules = matrix.getWidth() + 2 * QUIET_ZONE;
		int scale = Math.max(1, PNG_TARGET_SIZE / modules);
		BufferedImage image = new BufferedImage(modules * scale, modules * scale, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setColor(Color.BLACK);
			for (int y = 0; y < matrix.getHeight(); y++) {
				for (int x = 0; x < matrix.getWidth(); x++) {
					if (matrix.get(x, y) == 1) {
						graphics.fillRect((x + QUIET_ZONE) * scale, (y + QUIET_ZONE) * scale, scale, scale);
					}
				}
			}
		}
		finally {
			graphics.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	/**
	 * One path of horizontal runs in module units; it scales to any print size. Only
	 * numbers come from the input, so the SVG cannot carry markup or script.
	 */
	public static byte[] svg(String text) {
		ByteMatrix matrix = encode(text);
		int modules = matrix.getWidth() + 2 * QUIET_ZONE;
		StringBuilder path = new StringBuilder();
		for (int y = 0; y < matrix.getHeight(); y++) {
			int x = 0;
			while (x < matrix.getWidth()) {
				if (matrix.get(x, y) != 1) {
					x++;
					continue;
				}
				int start = x;
				while (x < matrix.getWidth() && matrix.get(x, y) == 1) {
					x++;
				}
				int run = x - start;
				path.append('M')
					.append(start + QUIET_ZONE)
					.append(' ')
					.append(y + QUIET_ZONE)
					.append('h')
					.append(run)
					.append("v1h-")
					.append(run)
					.append('z');
			}
		}
		String svg = """
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %1$d %1$d" width="%2$d" height="%2$d" \
				shape-rendering="crispEdges"><rect width="%1$d" height="%1$d" fill="#fff"/>\
				<path fill="#000" d="%3$s"/></svg>
				""".formatted(modules, modules * 8, path);
		return svg.getBytes(StandardCharsets.UTF_8);
	}

	static ByteMatrix encode(String text) {
		try {
			return Encoder
				.encode(text, ErrorCorrectionLevel.M, Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()))
				.getMatrix();
		}
		catch (WriterException ex) {
			throw new IllegalArgumentException("Cannot encode as QR code", ex);
		}
	}

}
