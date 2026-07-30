package com.wedding.checkin;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.wedding.checkin.model.Guest;
import com.wedding.checkin.service.InviteGeneratorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteGeneratorServiceTest {

    private static final float MM = 72f / 25.4f;
    private final InviteGeneratorService generator = new InviteGeneratorService();

    @Test
    void generaPdfValidoConDimensioniETestiAttesi() throws Exception {
        Guest guest = new Guest("0042", "Anna Bianchi", "7", "Armonia");

        byte[] bytes = generator.generate(guest);

        assertTrue(bytes.length > 5_000);
        assertEquals("%PDF", new String(bytes, 0, 4));
        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(200 * MM, document.getPage(0).getMediaBox().getWidth(), .1f);
            assertEquals(100 * MM, document.getPage(0).getMediaBox().getHeight(), .1f);

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Tamara & Nicolas"));
            assertTrue(text.contains("Anna Bianchi"));
            assertTrue(text.contains("7"));
            assertTrue(text.contains("ARMONIA"));
        }
    }

    @Test
    void qrNelPdfContieneSoloIdInvitato() throws Exception {
        Guest guest = new Guest("0099", "Nome Non Codificato", "3", "Fiducia");
        byte[] bytes = generator.generate(guest);

        try (PDDocument document = PDDocument.load(bytes)) {
            BufferedImage page = new PDFRenderer(document)
                    .renderImageWithDPI(0, 200, ImageType.RGB);
            var bitmap = new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(page)));
            assertEquals("0099", new MultiFormatReader().decode(bitmap).getText());
        }
    }

    @Test
    void adattaNomiLunghiEApplicaFallbackNomeTavolo() throws Exception {
        Guest guest = new Guest("0100",
                "Alessandro Giovanni Maria Rossi e Beatrice Bianchi", "2", "");

        byte[] bytes = generator.generate(guest);

        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains(guest.getNome()));
            assertTrue(text.contains("2"));
            assertTrue(text.contains("PAZIENZA"));
        }
    }

    @Test
    void nomeTavoloEsplicitoHaPrioritaSulFallback() throws Exception {
        Guest guest = new Guest("0101", "Luca Verdi", "2", "Tavolo personalizzato");

        byte[] bytes = generator.generate(guest);

        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("TAVOLO PERSONALIZZATO"));
            assertTrue(!text.contains("PAZIENZA"));
        }
    }

    @Test
    void numeroNonMappatoNonInventaUnNomeTavolo() throws Exception {
        Guest guest = new Guest("0102", "Sara Neri", "99", "");

        byte[] bytes = generator.generate(guest);

        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("99"));
            assertTrue(!text.contains("N O M E   T A V O L O"));
        }
    }

    @Test
    void rifiutaInvitatoSenzaIdONome() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(new Guest("", "Mario Rossi", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(new Guest("0001", "", "1")));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }
}
