package com.wedding.checkin;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.wedding.checkin.model.Guest;
import com.wedding.checkin.service.VerticalInviteGeneratorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalInviteGeneratorServiceTest {

    private final VerticalInviteGeneratorService generator =
            new VerticalInviteGeneratorService();

    @Test
    void coppiaConCognomeCondivisoMostraTitoloECognome() throws Exception {
        Guest guest = new Guest("0001", "M. et Mme. Matarella", "2", "Pazienza");

        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("M et Mme"));
            assertTrue(text.contains("MATARELLA"));
        }
    }

    @Test
    void supportaLaFormaEstesaMonsieurEtMadame() throws Exception {
        Guest guest = new Guest("0002", "Monsieur et Madame Matarella", "2", "Pazienza");

        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Monsieur"));
            assertTrue(text.contains("Madame"));
            assertTrue(text.contains("MATARELLA"));
        }
    }

    @Test
    void singleMostraTitoloENomeSulloStessoCampo() throws Exception {
        Guest guest = new Guest("0003", "Mme Anna Bianchi", "7", "Armonia");

        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Mme"));
            assertTrue(text.contains("ANNA BIANCHI"));
            assertTrue(text.contains("Total Bonanjo Douala"));
        }
    }

    @Test
    void qrVerticaleContieneSoloIdInvitato() throws Exception {
        Guest guest = new Guest("0099", "M. Mario Rossi", "2", "Pazienza");
        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            BufferedImage page = new PDFRenderer(document)
                    .renderImageWithDPI(0, 250, ImageType.RGB);
            var bitmap = new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(page)));
            assertEquals("0099", new MultiFormatReader().decode(bitmap).getText());
        }
    }

    @Test
    void tavoloResiliezaUsaGillNgounou() throws Exception {
        Guest guest = new Guest("0100", "Mme Anna Bianchi", "20", "Resilieza");

        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.replaceAll("\\s+", " ").contains("Gill Ngounou"));
            assertTrue(text.contains("Resilieza"));
        }
    }
}
