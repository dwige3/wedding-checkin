package com.wedding.checkin;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.service.VerticalInviteGeneratorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

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
        }
    }
}
