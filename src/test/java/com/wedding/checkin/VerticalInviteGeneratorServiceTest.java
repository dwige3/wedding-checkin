package com.wedding.checkin;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.service.VerticalInviteGeneratorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalInviteGeneratorServiceTest {

    private final VerticalInviteGeneratorService generator =
            new VerticalInviteGeneratorService();

    @Test
    void coppiaConCognomeCondivisoMostraTitoloECognomeSeparati() throws Exception {
        Guest guest = new Guest("0001", "M. et Mme. Matarella", "2", "Pazienza");

        byte[] pdf = generator.generate(guest);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("M. et Mme"));
            assertTrue(text.contains("Matarella"));
            assertFalse(text.contains("Mme. Matarella"));
        }
    }
}
