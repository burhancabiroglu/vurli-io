package io.vurli.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders and then scans each code with ZXing's reader, so a broken quiet zone, a
 * misplaced module or a wrong scale fails here rather than on a printed menu.
 */
class QrCodeRendererTests {

	private static final Pattern RUN = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-\\3z");

	@ParameterizedTest
	@ValueSource(strings = { "https://vurli.io/abc1234", "https://vurli.io/kafe-menu-ve-tatlilar-listesi-2026-sonbahar-x",
			"http://localhost:8080/a" })
	void pngScansBackToTheShortUrl(String url) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(QrCodeRenderer.png(url)));

		assertThat(image.getWidth()).isEqualTo(image.getHeight()).isBetween(900, QrCodeRenderer.PNG_TARGET_SIZE);
		assertThat(scan(image)).isEqualTo(url);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://vurli.io/abc1234", "https://vurli.io/kafe-menu-ve-tatlilar-listesi-2026-sonbahar-x" })
	void svgScansBackToTheShortUrl(String url) throws Exception {
		String svg = new String(QrCodeRenderer.svg(url), StandardCharsets.UTF_8);

		assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"").doesNotContain(url);
		assertThat(scan(rasterize(svg))).isEqualTo(url);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://vurli.io/abc1234" })
	void quietZoneIsBlank(String url) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(QrCodeRenderer.png(url)));
		int modules = QrCodeRenderer.encode(url).getWidth() + 2 * QrCodeRenderer.QUIET_ZONE;
		int border = image.getWidth() / modules * QrCodeRenderer.QUIET_ZONE;

		for (int i = 0; i < image.getWidth(); i++) {
			for (int j = 0; j < border; j++) {
				assertThat(image.getRGB(i, j) & 0xFFFFFF).isEqualTo(0xFFFFFF);
				assertThat(image.getRGB(j, i) & 0xFFFFFF).isEqualTo(0xFFFFFF);
			}
		}
	}

	/** Draws the SVG's runs at 8 px per module, the way a browser would. */
	private static BufferedImage rasterize(String svg) {
		Matcher viewBox = Pattern.compile("viewBox=\"0 0 (\\d+) \\1\"").matcher(svg);
		assertThat(viewBox.find()).isTrue();
		int modules = Integer.parseInt(viewBox.group(1));
		int scale = 8;
		BufferedImage image = new BufferedImage(modules * scale, modules * scale, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				image.setRGB(x, y, 0xFFFFFF);
			}
		}
		String path = svg.substring(svg.indexOf(" d=\"") + 4, svg.lastIndexOf("\"/></svg>"));
		Matcher run = RUN.matcher(path);
		int consumed = 0;
		while (run.find()) {
			assertThat(run.start()).isEqualTo(consumed);
			consumed = run.end();
			int x = Integer.parseInt(run.group(1));
			int y = Integer.parseInt(run.group(2));
			int length = Integer.parseInt(run.group(3));
			for (int px = x * scale; px < (x + length) * scale; px++) {
				for (int py = y * scale; py < (y + 1) * scale; py++) {
					image.setRGB(px, py, 0);
				}
			}
		}
		assertThat(consumed).as("path holds only module runs").isEqualTo(path.length());
		return image;
	}

	private static String scan(BufferedImage image) throws Exception {
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		LuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
		return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
	}

}
