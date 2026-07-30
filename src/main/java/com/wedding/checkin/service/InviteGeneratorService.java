package com.wedding.checkin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.wedding.checkin.model.Guest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class InviteGeneratorService {

    private static final float MM = 72f / 25.4f;
    private static final float PAGE_W = 200 * MM;
    private static final float PAGE_H = 100 * MM;
    private static final float MARGIN = 5 * MM;
    private static final float DIVIDER_X = MARGIN + (PAGE_W - 2 * MARGIN) * .66f;

    private static final Color GREEN = new Color(18, 48, 31);
    private static final Color GREEN_DARK = new Color(11, 38, 29);
    private static final Color LEAF = new Color(65, 107, 71);
    private static final Color LEAF_LIGHT = new Color(120, 148, 109);
    private static final Color GOLD = new Color(201, 169, 97);
    private static final Color GOLD_SOFT = new Color(156, 131, 82);
    private static final Color CREAM = new Color(245, 239, 224);
    private static final Color ROSE_SHADOW = new Color(216, 205, 179);
    private static final Map<Integer, String> TABLE_NAMES = Map.ofEntries(
            Map.entry(1, "Amore"),
            Map.entry(2, "Pazienza"),
            Map.entry(3, "Fiducia"),
            Map.entry(4, "Rispetto"),
            Map.entry(5, "Gioia"),
            Map.entry(6, "Serenità"),
            Map.entry(7, "Armonia"),
            Map.entry(8, "Complicità"),
            Map.entry(9, "Gratitudine"),
            Map.entry(10, "Speranza"),
            Map.entry(11, "Tenerezza"),
            Map.entry(12, "Felicità"),
            Map.entry(13, "Sorriso"),
            Map.entry(14, "Abbraccio"),
            Map.entry(15, "Dolcezza")
    );

    public byte[] generate(Guest guest) {
        if (guest == null || blank(guest.getId()) || blank(guest.getNome())) {
            throw new IllegalArgumentException("L'invitato deve avere id e nome");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            document.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(document, page)) {
                drawBackground(c);
                drawFloralBorder(c);
                drawDivider(c);
                drawInvitationPanel(c);
                drawGuestPanel(document, c, guest);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile generare il PDF dell'invito", e);
        }
    }

    private void drawBackground(PDPageContentStream c) throws IOException {
        fill(c, GREEN_DARK);
        c.addRect(0, 0, PAGE_W, PAGE_H);
        c.fill();
        stroke(c, GOLD);
        c.setLineWidth(1.1f);
        c.addRect(MARGIN, MARGIN, PAGE_W - 2 * MARGIN, PAGE_H - 2 * MARGIN);
        c.stroke();
    }

    private void drawDivider(PDPageContentStream c) throws IOException {
        stroke(c, GOLD);
        c.setLineWidth(.6f);
        c.setLineDashPattern(new float[]{2, 2}, 0);
        c.moveTo(DIVIDER_X, MARGIN);
        c.lineTo(DIVIDER_X, PAGE_H - MARGIN);
        c.stroke();
        c.setLineDashPattern(new float[]{}, 0);
        fill(c, Color.WHITE);
        circle(c, DIVIDER_X, MARGIN, 2.2f * MM, true);
        circle(c, DIVIDER_X, PAGE_H - MARGIN, 2.2f * MM, true);
    }

    private void drawInvitationPanel(PDPageContentStream c) throws IOException {
        float left = MARGIN + 24 * MM;
        float right = DIVIDER_X - 6 * MM;
        float cx = (left + right) / 2;
        float width = right - left;

        heart(c, cx, PAGE_H - 12 * MM, 4 * MM, CREAM);
        flourish(c, cx, PAGE_H - 15.5f * MM, 25 * MM);
        centered(c, "Con grande gioia vi invitiamo alle nozze di",
                PDType1Font.TIMES_ITALIC, 8, cx, PAGE_H - 22 * MM, GOLD);
        centeredFit(c, "Tamara & Nicolas", PDType1Font.TIMES_ITALIC,
                26, 16, width, cx, PAGE_H - 33 * MM, GOLD);
        flourish(c, cx, PAGE_H - 39 * MM, 30 * MM);
        centered(c, "20 Agosto 2026", PDType1Font.TIMES_ROMAN,
                14, cx, PAGE_H - 48 * MM, CREAM);
        centered(c, "M I L A N O", PDType1Font.HELVETICA_BOLD,
                9, cx, PAGE_H - 54.5f * MM, GOLD);
        dottedLine(c, left, right, PAGE_H - 62.5f * MM);
        centered(c, "Vi aspettiamo per festeggiare insieme a noi",
                PDType1Font.TIMES_ITALIC, 9, cx, MARGIN + 9 * MM, CREAM);
        heart(c, cx, MARGIN + 4 * MM, 3.4f * MM, CREAM);
        flourish(c, cx, MARGIN + 2.2f * MM, 23 * MM);
    }

    private void drawGuestPanel(PDDocument document, PDPageContentStream c, Guest guest)
            throws IOException {
        float left = DIVIDER_X + 5 * MM;
        float right = PAGE_W - MARGIN - 5 * MM;
        float cx = (left + right) / 2;
        float width = right - left;
        float y = PAGE_H - MARGIN - 8 * MM;

        centered(c, "Invito personale", PDType1Font.TIMES_ITALIC, 9, cx, y, GOLD);
        y -= 5 * MM;
        centered(c, "Mostra questo codice all'ingresso",
                PDType1Font.HELVETICA, 6.5f, cx, y, CREAM);

        float qrSize = 32 * MM;
        float pad = 2.5f * MM;
        float boxSize = qrSize + 2 * pad;
        float boxX = cx - boxSize / 2;
        float boxY = y - 6 * MM - boxSize;
        fill(c, Color.WHITE);
        stroke(c, GOLD);
        c.setLineWidth(1);
        c.addRect(boxX, boxY, boxSize, boxSize);
        c.fillAndStroke();
        PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage(guest.getId()));
        c.drawImage(qr, boxX + pad, boxY + pad, qrSize, qrSize);

        y = boxY - 3 * MM;
        centered(c, "I N V I T A T O", PDType1Font.HELVETICA, 6.5f, cx, y, GOLD);
        y -= 3.8f * MM;
        centeredFit(c, guest.getNome(), PDType1Font.TIMES_BOLD,
                11, 7, width, cx, y, CREAM);
        y -= 3 * MM;
        dottedLine(c, left, right, y);
        y -= 3 * MM;
        centered(c, "T A V O L O", PDType1Font.HELVETICA, 6.5f, cx, y, GOLD);
        y -= 3.8f * MM;
        centered(c, valueOrDash(guest.getTavolo()), PDType1Font.TIMES_BOLD, 13, cx, y, CREAM);
        String tableName = resolveTableName(guest);
        if (!blank(tableName)) {
            y -= 3.8f * MM;
            centered(c, "N O M E   T A V O L O", PDType1Font.HELVETICA, 6, cx, y, GOLD);
            y -= 3.5f * MM;
            centeredFit(c, tableName.toUpperCase(), PDType1Font.TIMES_BOLD,
                    9, 6, width, cx, y, CREAM);
        }
    }

    /**
     * Un nome esplicito ha sempre priorità. Se manca, usa la stessa
     * associazione numero/nome del precedente generatore Python.
     */
    String resolveTableName(Guest guest) {
        if (!blank(guest.getNomeTavolo())) {
            return guest.getNomeTavolo().trim();
        }
        if (blank(guest.getTavolo())) {
            return "";
        }
        try {
            return TABLE_NAMES.getOrDefault(Integer.parseInt(guest.getTavolo().trim()), "");
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private BufferedImage qrImage(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 500, 500,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 500; y++) {
                for (int x = 0; x < 500; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? GREEN_DARK.getRGB() : Color.WHITE.getRGB());
                }
            }
            return image;
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossibile creare il QR per l'id " + payload, e);
        }
    }

    private void drawFloralBorder(PDPageContentStream c) throws IOException {
        float x = MARGIN + 7 * MM;
        stroke(c, LEAF_LIGHT);
        c.setLineWidth(1);
        c.moveTo(x, MARGIN + 2 * MM);
        c.curveTo(MARGIN + 24 * MM, MARGIN + 40 * MM,
                MARGIN + 4 * MM, PAGE_H - MARGIN - 18 * MM,
                MARGIN + 16 * MM, PAGE_H - MARGIN);
        c.stroke();
        leaf(c, MARGIN + 5 * MM, MARGIN + 13 * MM, 12 * MM, 3.2f * MM, 25, LEAF);
        leaf(c, MARGIN + 13 * MM, MARGIN + 20 * MM, 13 * MM, 3.4f * MM, 145, GREEN);
        leaf(c, MARGIN + 7 * MM, MARGIN + 34 * MM, 14 * MM, 3.8f * MM, 20, LEAF_LIGHT);
        leaf(c, MARGIN + 17 * MM, MARGIN + 44 * MM, 12 * MM, 3.4f * MM, 150, LEAF);
        leaf(c, MARGIN + 6 * MM, MARGIN + 60 * MM, 14 * MM, 3.8f * MM, 15, GREEN);
        leaf(c, MARGIN + 14 * MM, MARGIN + 71 * MM, 13 * MM, 3.5f * MM, 145, LEAF_LIGHT);
        rose(c, MARGIN + 5 * MM, PAGE_H - MARGIN - 10 * MM, 7.5f * MM);
        rose(c, MARGIN + 8 * MM, MARGIN + 10 * MM, 8 * MM);
        rose(c, MARGIN + 17 * MM, MARGIN + 24 * MM, 6.5f * MM);
        babyBreath(c, MARGIN + 5 * MM, MARGIN + 34 * MM);
        babyBreath(c, MARGIN + 4 * MM, PAGE_H - MARGIN - 24 * MM);
    }

    private void leaf(PDPageContentStream c, float x, float y, float length,
                      float width, float rotation, Color color) throws IOException {
        double angle = Math.toRadians(rotation);
        float ux = (float) Math.cos(angle), uy = (float) Math.sin(angle);
        float vx = -uy, vy = ux;
        fill(c, color);
        c.moveTo(x, y);
        c.curveTo(x + ux * length * .28f + vx * width, y + uy * length * .28f + vy * width,
                x + ux * length * .78f + vx * width * .72f, y + uy * length * .78f + vy * width * .72f,
                x + ux * length, y + uy * length);
        c.curveTo(x + ux * length * .72f - vx * width * .68f, y + uy * length * .72f - vy * width * .68f,
                x + ux * length * .25f - vx * width, y + uy * length * .25f - vy * width, x, y);
        c.closePath();
        c.fill();
    }

    private void rose(PDPageContentStream c, float x, float y, float radius) throws IOException {
        fill(c, ROSE_SHADOW);
        circle(c, x, y, radius, true);
        float[][] petals = {{0, .25f, .74f}, {-.34f, .10f, .57f}, {.34f, .10f, .57f},
                {-.23f, -.28f, .53f}, {.23f, -.28f, .53f}, {0, -.05f, .43f}};
        fill(c, CREAM);
        for (float[] p : petals) {
            ellipse(c, x + p[0] * radius, y + p[1] * radius,
                    p[2] * radius, p[2] * radius * .52f);
        }
        stroke(c, GOLD_SOFT);
        c.setLineWidth(.35f);
        circle(c, x, y, radius * .22f, false);
    }

    private void babyBreath(PDPageContentStream c, float x, float y) throws IOException {
        stroke(c, LEAF_LIGHT);
        c.setLineWidth(.35f);
        float endX = x + 8 * MM, endY = y + 12 * MM;
        c.moveTo(x, y);
        c.lineTo(endX, endY);
        c.stroke();
        float[][] dots = {{2, 4}, {5, 6}, {3, 9}, {7, 10}, {8, 13}};
        for (float[] dot : dots) {
            fill(c, CREAM);
            circle(c, x + dot[0] * MM, y + dot[1] * MM, .9f * MM, true);
            fill(c, GOLD);
            circle(c, x + dot[0] * MM, y + dot[1] * MM, .22f * MM, true);
        }
    }

    private void centered(PDPageContentStream c, String text, PDType1Font font,
                          float size, float cx, float y, Color color) throws IOException {
        fill(c, color);
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(cx - font.getStringWidth(text) / 1000 * size / 2, y);
        c.showText(text);
        c.endText();
    }

    private void centeredFit(PDPageContentStream c, String text, PDType1Font font,
                             float max, float min, float width, float cx, float y, Color color)
            throws IOException {
        float size = max;
        while (size > min && font.getStringWidth(text) / 1000 * size > width) size -= .5f;
        centered(c, text, font, size, cx, y, color);
    }

    private void flourish(PDPageContentStream c, float cx, float y, float width) throws IOException {
        stroke(c, GOLD);
        fill(c, GOLD);
        c.setLineWidth(.5f);
        c.moveTo(cx - width / 2, y);
        c.lineTo(cx - 1.6f * MM, y);
        c.moveTo(cx + 1.6f * MM, y);
        c.lineTo(cx + width / 2, y);
        c.stroke();
        float d = 1.1f * MM;
        c.moveTo(cx, y + d);
        c.lineTo(cx + d, y);
        c.lineTo(cx, y - d);
        c.lineTo(cx - d, y);
        c.closePath();
        c.fill();
    }

    private void dottedLine(PDPageContentStream c, float x1, float x2, float y) throws IOException {
        stroke(c, GOLD_SOFT);
        c.setLineWidth(.4f);
        c.setLineDashPattern(new float[]{1, 2}, 0);
        c.moveTo(x1, y);
        c.lineTo(x2, y);
        c.stroke();
        c.setLineDashPattern(new float[]{}, 0);
    }

    private void heart(PDPageContentStream c, float cx, float cy, float size, Color color)
            throws IOException {
        fill(c, color);
        float r = size / 4;
        circle(c, cx - r, cy, r, true);
        circle(c, cx + r, cy, r, true);
        c.moveTo(cx - size / 2, cy);
        c.lineTo(cx, cy - size / 2);
        c.lineTo(cx + size / 2, cy);
        c.closePath();
        c.fill();
    }

    private void ellipse(PDPageContentStream c, float cx, float cy, float rx, float ry)
            throws IOException {
        float k = .55228475f;
        c.moveTo(cx + rx, cy);
        c.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry);
        c.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy);
        c.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry);
        c.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy);
        c.closePath();
        c.fill();
    }

    private void circle(PDPageContentStream c, float cx, float cy, float radius, boolean fill)
            throws IOException {
        float k = .55228475f * radius;
        c.moveTo(cx + radius, cy);
        c.curveTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius);
        c.curveTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy);
        c.curveTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius);
        c.curveTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy);
        c.closePath();
        if (fill) c.fill(); else c.stroke();
    }

    private void fill(PDPageContentStream c, Color color) throws IOException {
        c.setNonStrokingColor(color);
    }

    private void stroke(PDPageContentStream c, Color color) throws IOException {
        c.setStrokingColor(color);
    }

    private String valueOrDash(String value) {
        return blank(value) ? "-" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
