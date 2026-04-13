package com.selah;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.awt.image.ColorConvertOp;
import java.awt.color.ColorSpace;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OCRProcessor {

    private static final String WORKING_DIRECTORY = App.WORKING_DIRECTORY;

    private static final Set<String> ENGLISH_SUBSTRINGS = new HashSet<>();

    static {
        // Populate the dictionary with common three-letter English substrings
        String[] substrings = {"the", "and", "ing", "ion", "ent", "her", "for", "tha", "nth", "was", "you", "ith", "ver", "all", "thi", "ter", "hat", "ere", "his", "res"};
        for (String substring : substrings) {
            ENGLISH_SUBSTRINGS.add(substring);
        }
    }

    private static int countEnglishSubstrings(String text) {
        int count = 0;
        text = text.toLowerCase();
        for (String substring : ENGLISH_SUBSTRINGS) {
            int index = text.indexOf(substring);
            while (index != -1) {
                count++;
                index = text.indexOf(substring, index + 1);
            }
        }
        return count;
    }

    private static String cleanText(String text) {
        // Remove non-alphanumeric characters except spaces and newlines
        String cleanedText = text.replaceAll("[^a-zA-Z0-9 \n]", "");

        // Remove extra spaces but keep newlines
        cleanedText = cleanedText.replaceAll("[ \t]+", " ").trim();

        return cleanedText;
    }

    public static String processImageFromUrl(String imageUrl) throws BadImageException {
        try {
            // Ensure the URL uses format=png if a format parameter exists
            if (imageUrl.contains("format=")) {
                imageUrl = imageUrl.replaceAll("format=[^&]*", "format=png");
                System.out.println("Updated URL to use format=png: " + imageUrl);
            }

            Path tempImagePath = Path.of(WORKING_DIRECTORY, "SELAH_OCR_temp_image.png");

            // Download the image
            try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
                Files.copy(in, tempImagePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Image downloaded to: " + tempImagePath);
                if (!Files.exists(tempImagePath) || Files.size(tempImagePath) == 0) {
                    //System.err.println("Downloaded file is empty or does not exist.");
                    throw new BadImageException("Failed to download image from URL: " + imageUrl);
                }
            }

            // Validate the downloaded image
            BufferedImage image = ImageIO.read(tempImagePath.toFile());
            if (image == null) {
                //System.err.println("The downloaded file is not a valid image or cannot be read.");
                return new BadImageException("The downloaded file is not a valid image or cannot be read: " + tempImagePath).toString();
            }

            // Convert all images to PNG for Tesseract compatibility
            Path convertedImagePath = Path.of(WORKING_DIRECTORY, "SELAH_OCR_converted_image.png");
            ImageIO.write(image, "png", convertedImagePath.toFile());
            System.out.println("Image converted to PNG and saved to: " + convertedImagePath);

            // Perform OCR on the colorized version
            System.out.println("Performing OCR on colorized image...");
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(System.getProperty("os.name").toLowerCase().contains("win")
                    ? "C:/Program Files/Tesseract-OCR/tessdata"
                    : "/usr/share/tesseract-ocr/4.00/tessdata");
            String extractedTextColor = tesseract.doOCR(convertedImagePath.toFile());

            // Convert the image to grayscale
            System.out.println("Converting image to grayscale...");
            BufferedImage grayscaleImage = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(image, null);
            Path grayscaleImagePath = Path.of(WORKING_DIRECTORY, "SELAH_OCR_grayscale_image.png");
            ImageIO.write(grayscaleImage, "png", grayscaleImagePath.toFile());
            System.out.println("Grayscale image saved to: " + grayscaleImagePath);

            // Perform OCR on the grayscale version
            System.out.println("Performing OCR on grayscale image...");
            String extractedTextBW = tesseract.doOCR(grayscaleImagePath.toFile());

            // Clean the extracted text
            String cleanedTextColor = cleanText(extractedTextColor);
            String cleanedTextBW = cleanText(extractedTextBW);

            // Count English substrings in both versions
            int colorCount = countEnglishSubstrings(cleanedTextColor);
            int bwCount = countEnglishSubstrings(cleanedTextBW);

            // Delete temporary image files after processing
            try {
                Files.deleteIfExists(Path.of(WORKING_DIRECTORY, "SELAH_OCR_temp_image.png"));
                Files.deleteIfExists(Path.of(WORKING_DIRECTORY, "SELAH_OCR_converted_image.png"));
                Files.deleteIfExists(Path.of(WORKING_DIRECTORY, "SELAH_OCR_grayscale_image.png"));
                System.out.println("Temporary image files deleted.");
            } catch (IOException e) {
                System.err.println("Failed to delete temporary image files.");
                e.printStackTrace();
            }

            // Output the version with more English substrings
            if (colorCount >= bwCount) {
                //System.out.println("Colorized version has more English substrings. Outputting cleaned text:");
                //System.out.println(cleanedTextColor);
                return cleanedTextColor;
            } else {
                //System.out.println("Grayscale version has more English substrings. Outputting cleaned text:");
                //System.out.println(cleanedTextBW);
                return cleanedTextBW;
            }

        } catch (TesseractException e) {
            System.err.println("Failed to process image for OCR.");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("An error occurred while processing the image.");
            e.printStackTrace();
            return null;
        }
    }
}