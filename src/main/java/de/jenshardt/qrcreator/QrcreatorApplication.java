package de.jenshardt.qrcreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

@SpringBootApplication
public class QrcreatorApplication implements ApplicationRunner {

	private final static Logger LOG = LoggerFactory.getLogger(QrcreatorApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(QrcreatorApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		LOG.info("QR Creator Application started successfully.");
		try {
			creatQRCodesFromCSV();
			LOG.info("QR Creator Application finished successfully.");
		} catch (Exception e) {
			LOG.error(e.getMessage());
			e.printStackTrace();
		} finally {
			System.exit(0);
		}
	}

	private void creatQRCodesFromCSV() throws CsvValidationException {
		// Read csv from the folder "files/input" in a structure that
		// each line has the columns "Datum", "URL", "Beschreibung".

		String csvFilePath = "src/main/files/input/data_full.csv";
		String outputDir = "src/main/files/output";

		// Create output directory if it doesn't exist
		File outputFolder = new File(outputDir);
		if (!outputFolder.exists()) {
			outputFolder.mkdirs();
		}

		CSVParser parser = new CSVParserBuilder()
				.withSeparator(',')
				.build();
		try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFilePath))
				.withCSVParser(parser)
				.build()) {

			String[] line;
			// Iterate over each line in the CSV file
			while ((line = reader.readNext()) != null) {
				if (line.length < 3) {
					LOG.warn("Skipping invalid CSV line with insufficient columns");
					continue;
				}

				// CSV columns are: Datum, Beschreibung, URL
				String datum = line[0].trim();
				String beschreibung = line[1].trim();
				String url = line[2].trim();

				LOG.info("Found line {}, {} with an url", datum, beschreibung);

				try {
					// Decide whether to overlay an icon in the center of the QR code
					BufferedImage overlay = null;
					if (datum != null && !datum.isEmpty()) {
						char first = Character.toUpperCase(datum.charAt(0));
						try {
							if (first == 'W') {
								int idx = new java.util.Random().nextInt(6) + 1; // 1..6
								String iconPath = String.format("src/main/files/icons/Panda%d.png", idx);
								overlay = ImageIO.read(new File(iconPath));
							} else if (first == 'M') {
								String iconPath = "src/main/files/icons/Donkey.jpeg";
								overlay = ImageIO.read(new File(iconPath));
							}
						} catch (IOException e) {
							LOG.warn("Could not load overlay icon for datum {}: {}", datum, e.getMessage());
						}
					}

					// Create a QR code for each line containing the content from the "URL" column.
					BufferedImage qrCodeImage = generateQRCode(url, 300, 300, overlay);

					// Create an image consisting of three parts:
					// - The content of the "Datum" column on top as text,
					// - The QR Code below that with a maximal size of 300x300 pixels containing the content of the "URL" column and
					// - The content of the "Beschreibung" column below the QR code as text.
					BufferedImage compositeImage = createCompositeImage(datum, qrCodeImage, beschreibung);

					// The file name should be the content of the "Datum" column.
					String fileName = datum.replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
					String filePath = Paths.get(outputDir, fileName).toString();
					LOG.info("Saving file {} in folder {}", fileName, filePath);

					// The image should be in PNG format.
					// Save the images in the output folder.
					ImageIO.write(compositeImage, "PNG", new File(filePath));
					LOG.info("QR Code image created successfully: {}", filePath);

				} catch (WriterException | IOException e) {
					LOG.error("Error creating QR code for datum: {}", datum, e);
				}
			}

			LOG.info("QR code generation completed successfully");

		} catch (IOException e) {
			LOG.error("Error reading CSV file: {}", csvFilePath, e);
		}
	}

	private static BufferedImage generateQRCode(String content, int width, int height, BufferedImage overlay) throws WriterException, IOException {
		// Prepare QR encoder hints for high error correction
		java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
		hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		hints.put(EncodeHintType.MARGIN, 1);

		QRCode qr = Encoder.encode(content, ErrorCorrectionLevel.H, hints);
		ByteMatrix matrix = qr.getMatrix();
		if (matrix == null) throw new WriterException("QR matrix is null");
		int matrixWidth = matrix.getWidth();

		// compute module size so QR fits requested pixel dimensions
		int moduleSize = Math.max(1, Math.min(width / matrixWidth, height / matrixWidth));
		int qrPixelWidth = moduleSize * matrixWidth;
		int leftPadding = (width - qrPixelWidth) / 2;
		int topPadding = (height - qrPixelWidth) / 2;

		BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = qrImage.createGraphics();
		// white background
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		// draw modules
		g.setColor(Color.BLACK);
		for (int my = 0; my < matrixWidth; my++) {
			for (int mx = 0; mx < matrixWidth; mx++) {
				if (matrix.get(mx, my) == 1) {
					int x = leftPadding + mx * moduleSize;
					int y = topPadding + my * moduleSize;
					g.fillRect(x, y, moduleSize, moduleSize);
				}
			}
		}

		if (overlay == null) {
			g.dispose();
			return qrImage;
		}

		// determine overlay size in modules (keep smaller than ~18% of QR)
		double overlayFraction = 0.18;
		int overlayMaxPx = (int) (width * overlayFraction);
		int overlayModules = Math.max(1, overlayMaxPx / moduleSize);
		// ensure overlayModules is odd to center nicely
		if (overlayModules % 2 == 0) overlayModules--;
		if (overlayModules < 1) overlayModules = 1;

		int overlayPx = overlayModules * moduleSize;
		int startModuleX = (matrixWidth - overlayModules) / 2;
		int startModuleY = (matrixWidth - overlayModules) / 2;

		// clear whole modules under overlay (set to white)
		g.setColor(Color.WHITE);
		int clearX = leftPadding + startModuleX * moduleSize;
		int clearY = topPadding + startModuleY * moduleSize;
		g.fillRect(clearX, clearY, overlayPx, overlayPx);

		// draw overlay image scaled to overlayPx preserving colors
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		int overlayDrawW = overlayPx;
		int overlayDrawH = overlayPx;
		g.drawImage(overlay, clearX, clearY, overlayDrawW, overlayDrawH, null);

		g.dispose();
		return qrImage;
	}

	private static BufferedImage createCompositeImage(String datum, BufferedImage qrCodeImage, String beschreibung) {
		int qrSize = 600;
		int padding = 20;
		int spacing = 10;
		int fontSize = 14;
		// We'll measure text using FontMetrics to support wrapping
		int contentWidth = qrSize;
		int imageWidth = contentWidth + (2 * (padding + spacing));

		// Prepare temporary graphics to measure text
		BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D gtmp = tmp.createGraphics();
		Font font = new Font("Arial", Font.PLAIN, fontSize);
		gtmp.setFont(font);
		java.awt.FontMetrics fm = gtmp.getFontMetrics();
		int fmHeight = fm.getHeight();
		int fmAscent = fm.getAscent();

		// Wrap beschreibung into lines that fit into contentWidth
		java.util.List<String> wrappedLines = new java.util.ArrayList<>();
		if (beschreibung == null) beschreibung = "";
		String[] paragraphs = beschreibung.split("\n");
		// reserve space for the prefix "Beschreibung: " when wrapping
		int prefixWidth = fm.stringWidth("Beschreibung: ");
		int availableWidth = Math.max(10, contentWidth - prefixWidth);
		for (String para : paragraphs) {
			String[] words = para.split(" ");
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < words.length; i++) {
				String word = words[i];
				String test = line.length() == 0 ? word : line + " " + word;
				if (fm.stringWidth(test) <= availableWidth) {
					line.setLength(0);
					line.append(test);
				} else {
					if (line.length() > 0) {
						wrappedLines.add(line.toString());
						line.setLength(0);
						line.append(word);
					} else {
						// single long word, need to split by characters
						StringBuilder part = new StringBuilder();
						for (char c : word.toCharArray()) {
							part.append(c);
							if (fm.stringWidth(part.toString()) > availableWidth) {
								// remove last char and push
								part.setLength(part.length() - 1);
								if (part.length() > 0) wrappedLines.add(part.toString());
								part.setLength(0);
								part.append(c);
							}
						}
						if (part.length() > 0) wrappedLines.add(part.toString());
						line.setLength(0);
					}
				}
				if (i == words.length - 1 && line.length() > 0) {
					wrappedLines.add(line.toString());
				}
			}
		}
		gtmp.dispose();

		// Datum line height
		int datumHeight = fmHeight;

		// Calculate final image height dynamically
		int topYStart = spacing + padding;
		int datumYBaseline = topYStart + fmAscent;
		int qrY = datumYBaseline + padding;
		int beschreibungStartY = qrY + qrSize + padding;
		int beschreibungHeight = wrappedLines.size() * fmHeight;
		int imageHeight = beschreibungStartY + beschreibungHeight + spacing + padding;

		BufferedImage compositeImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = compositeImage.createGraphics();

		// Set white background
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, imageWidth, imageHeight);

		// Set text color and font
		graphics.setColor(Color.BLACK);
		graphics.setFont(font);

		// Draw Datum at the top (centered)
		String datumText = "Datum: " + datum;
		int datumWidth = graphics.getFontMetrics().stringWidth(datumText);
		int datumX = (imageWidth - datumWidth) / 2;
		graphics.drawString(datumText, datumX, datumYBaseline);

		// Draw QR code in the middle (centered)
		int qrX = (imageWidth - qrSize) / 2;
		graphics.drawImage(qrCodeImage, qrX, qrY, qrSize, qrSize, null);

		// Draw Beschreibung lines (centered)
		int currentBaseline = beschreibungStartY + fmAscent;
		for (int i = 0; i < wrappedLines.size(); i++) {
			String beschreibungLine = wrappedLines.get(i);
			String beschreibungText = (i == 0 ? "Beschreibung: " : "") + beschreibungLine;
			int beschreibungWidth = graphics.getFontMetrics().stringWidth(beschreibungText);
			int beschreibungX = (imageWidth - beschreibungWidth) / 2;
			graphics.drawString(beschreibungText, beschreibungX, currentBaseline);
			currentBaseline += fmHeight;
		}

		graphics.dispose();
		return compositeImage;
	}
}
