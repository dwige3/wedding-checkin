package com.wedding.checkin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.wedding.checkin.model.Guest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

@Service
public class VerticalInviteGeneratorService {

    private static final float MM = 72f / 25.4f;
    private static final float PAGE_W = 140 * MM;
    private static final float PAGE_H = 200 * MM;
    private static final Color GREEN = new Color(7, 48, 35);
    private static final Color GOLD = new Color(224, 174, 83);
    private static final Color CREAM = new Color(249, 245, 232);
    private static final Color MASTER_GREEN = new Color(0, 27, 14);
    // Bordo/rametti del pannello sinistro nella maquette sono bianco-argento
    // (effetto vetro), non oro: l'oro resta solo sul tagliando destro.
    private static final Color PLAQUE_EDGE = new Color(232, 236, 233);
    private static final Map<Integer, String> TABLE_NAMES = Map.ofEntries(
            Map.entry(1, "Amore"), Map.entry(2, "Pazienza"), Map.entry(3, "Fiducia"),
            Map.entry(4, "Rispetto"), Map.entry(5, "Gioia"), Map.entry(6, "Serenità"),
            Map.entry(7, "Armonia"), Map.entry(8, "Complicità"), Map.entry(9, "Gratitudine"),
            Map.entry(10, "Speranza"), Map.entry(11, "Tenerezza"), Map.entry(12, "Felicità"),
            Map.entry(13, "Sorriso"), Map.entry(14, "Abbraccio"), Map.entry(15, "Dolcezza"));

    // Dimensioni fisiche del pannello dove viene disegnata la composizione
    // floreale, usate per calcolare la risoluzione (DPI) davvero necessaria.
    private static final float MAIN_PANEL_W_MM = 88f;
    private static final float MAIN_PANEL_H_MM = 180f;
    private static final int IMAGE_DPI = 196;

    // Immagine e font caricati/ridimensionati una sola volta per istanza del
    // servizio (bean singleton di Spring), non ad ogni generate(): con 400
    // inviti evita di ridecodificare da disco e di incorporare un PNG a piena
    // risoluzione (~620 DPI) in ognuno dei 400 PDF, appesantendo lo zip finale.
    private final BufferedImage cornerFlowers = loadCornerFlowers();
    private final BufferedImage masterImage = loadMasterImage();
    private final byte[] scriptFontBytes = loadScriptFontBytes();

    private static BufferedImage loadCornerFlowers() {
        try (InputStream source = VerticalInviteGeneratorService.class
                .getResourceAsStream("/invite/vertical-corner-flowers-2x.png")) {
            if (source == null) {
                return null;
            }
            return scaleToDpi(ImageIO.read(source), MAIN_PANEL_W_MM, MAIN_PANEL_H_MM, IMAGE_DPI);
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage loadMasterImage() {
        try (InputStream source = VerticalInviteGeneratorService.class
                .getResourceAsStream("/invite/vertical-master.png")) {
            if (source == null) {
                return null;
            }
            return scaleToDpi(ImageIO.read(source), 140f, 200f, IMAGE_DPI);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] loadScriptFontBytes() {
        try (InputStream source = VerticalInviteGeneratorService.class
                .getResourceAsStream("/invite/fonts/GreatVibes-Regular.ttf")) {
            return source == null ? null : source.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Riduce l'immagine alla risoluzione richiesta per la stampa, senza
     * mai ingrandirla oltre l'originale. */
    private static BufferedImage scaleToDpi(BufferedImage source, float widthMm, float heightMm, int dpi) {
        int targetW = Math.round(widthMm / 25.4f * dpi);
        int targetH = Math.round(heightMm / 25.4f * dpi);
        if (source.getWidth() <= targetW && source.getHeight() <= targetH) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, targetW, targetH, null);
        g2.dispose();
        return scaled;
    }

    public byte[] generate(Guest guest) {
        if (guest == null || guest.getId() == null || guest.getId().isBlank()
                || guest.getNome() == null || guest.getNome().isBlank()) {
            throw new IllegalArgumentException("L'invitato deve avere id e nome");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            document.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(document, page)) {
                if (masterImage == null) {
                    throw new IllegalStateException("Template verticale master non trovato");
                }
                PDImageXObject master = LosslessFactory.createFromImage(document, masterImage);
                c.drawImage(master, 0, 0, PAGE_W, PAGE_H);
                drawDynamicMasterFields(document, c, guest);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile generare il template verticale", e);
        }
    }

    private void drawDynamicMasterFields(PDDocument document, PDPageContentStream c, Guest guest)
            throws IOException {
        // Le aree sono campionate dal verde uniforme del master. Tutto il
        // resto dell'immagine rimane esattamente quello fornito dall'utente.
        cover(c, 97 * MM, 111.2f * MM, 36 * MM, 32.3f * MM); // QR, titolo, nome e tratteggio
        cover(c, 127.2f * MM, 152.5f * MM, 8.3f * MM, 7 * MM); // cuori, senza toccare la n
        cover(c, 113.5f * MM, 84 * MM, 11 * MM, 7 * MM); // numero tavolo
        cover(c, 104 * MM, 59.5f * MM, 24 * MM, 7 * MM); // nome tavolo
        cover(c, 26 * MM, 52.8f * MM, 42 * MM, 5.8f * MM); // vecchia dicitura, senza toccare la riga sopra

        centered(c, "Total Bonanjo Douala", PDType1Font.TIMES_ROMAN,
                8, 47 * MM, 54.9f * MM, CREAM);

        float qrSize = 16 * MM;
        float qrPad = 1.4f * MM;
        float qrBoxSize = qrSize + 2 * qrPad;
        float qrBoxX = 115 * MM - qrBoxSize / 2;
        float qrBoxY = 124.5f * MM;
        fill(c, Color.WHITE);
        stroke(c, GOLD);
        c.setLineWidth(.8f);
        roundedRect(c, qrBoxX, qrBoxY, qrBoxSize, qrBoxSize,
                1.6f * MM, true, true);
        PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage(guest.getId()));
        c.drawImage(qr, qrBoxX + qrPad, qrBoxY + qrPad, qrSize, qrSize);

        float ticketCx = 115 * MM;
        boolean couple = isCouple(guest.getNome());
        String sharedSurnameLabel = sharedSurnameLabel(guest.getNome());
        boolean inlineGuest = sharedSurnameLabel != null || !couple;
        if (!inlineGuest) {
            centered(c, invitationTitle(guest.getNome()), PDType1Font.TIMES_BOLD,
                    8, ticketCx, 121.2f * MM, CREAM);
        }

        String[] nameLines = inlineGuest
                ? new String[0]
                : splitGuestName(displayName(guest.getNome()));
        if (inlineGuest) {
            String title = sharedSurnameLabel != null
                    ? (sharedSurnameLabel.startsWith("Monsieur et Madame ")
                        ? "Monsieur et Madame" : "M et Mme")
                    : invitationTitle(guest.getNome());
            String guestName = sharedSurnameLabel != null
                    ? sharedSurnameLabel.substring(title.length()).trim()
                    : displayName(guest.getNome());
            float titleSize = switch (title) {
                case "M et Mme" -> 9.5f;
                case "Monsieur et Madame" -> 5.8f;
                default -> 10f;
            };
            float titleX = 97.5f * MM;
            float lineEnd = 132.5f * MM;
            float titleWidth = PDType1Font.TIMES_BOLD.getStringWidth(title) / 1000 * titleSize;
            float lineStart = titleX + titleWidth + 1.2f * MM;
            float nameWidth = lineEnd - lineStart;
            String upperGuestName = guestName.toUpperCase(Locale.ROOT);
            boolean requiresTwoLines = PDType1Font.TIMES_BOLD.getStringWidth(upperGuestName)
                    / 1000 * 8.5f > nameWidth;
            if (requiresTwoLines) {
                centered(c, title, PDType1Font.TIMES_BOLD,
                        Math.min(titleSize, 8.5f), ticketCx, 121 * MM, CREAM);
                centeredFit(c, upperGuestName, PDType1Font.TIMES_BOLD,
                        11.5f, 7, 34 * MM, ticketCx, 116.4f * MM, CREAM);
                dotted(c, 98 * MM, 132 * MM, 115.4f * MM);
            } else {
                text(c, title, PDType1Font.TIMES_BOLD, titleSize,
                        titleX, 116.7f * MM, CREAM);
                dotted(c, lineStart, lineEnd, 115.9f * MM);
                centeredFit(c, upperGuestName, PDType1Font.TIMES_BOLD,
                        12.5f, 8.5f, nameWidth,
                        lineStart + nameWidth / 2, 116.7f * MM, CREAM);
            }
        } else if (nameLines.length == 1) {
            centeredFit(c, nameLines[0].toUpperCase(Locale.ROOT), PDType1Font.TIMES_BOLD,
                    13, 6.5f, 28 * MM, ticketCx,
                    116.5f * MM, CREAM);
        } else {
            centeredFit(c, nameLines[0].toUpperCase(Locale.ROOT), PDType1Font.TIMES_BOLD,
                    10.5f, 7, 27 * MM, ticketCx, 117.5f * MM, CREAM);
            centeredFit(c, nameLines[1].toUpperCase(Locale.ROOT), PDType1Font.TIMES_BOLD,
                    10.5f, 7, 27 * MM, ticketCx, 113.5f * MM, CREAM);
        }

        if (!inlineGuest) {
            float guestUnderlineY = nameLines.length == 1 ? 113.8f * MM : 111.7f * MM;
            dotted(c, 101 * MM, 129 * MM, guestUnderlineY);
        }

        if (couple) {
            heart(c, 128.5f * MM, 155.5f * MM, 2.5f * MM, CREAM);
            heart(c, 133 * MM, 155.5f * MM, 2.5f * MM, CREAM);
        } else {
            heart(c, 130.7f * MM, 155.5f * MM, 2.5f * MM, CREAM);
        }

        centeredFit(c, valueOrDash(guest.getTavolo()), PDType1Font.TIMES_ROMAN,
                14, 9, 10 * MM, 119 * MM, 85.5f * MM, CREAM);
        centeredFit(c, resolveTableName(guest), PDType1Font.TIMES_ROMAN,
                11, 7, 23 * MM, ticketCx, 61.5f * MM, CREAM);
    }

    private void cover(PDPageContentStream c, float x, float y, float w, float h)
            throws IOException {
        fill(c, MASTER_GREEN);
        c.addRect(x, y, w, h);
        c.fill();
    }

    private BufferedImage qrImage(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 500, 500,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            return MatrixToImageWriter.toBufferedImage(matrix,
                    new MatrixToImageConfig(MASTER_GREEN.getRGB(), Color.WHITE.getRGB()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossibile creare il QR per l'id " + payload, e);
        }
    }

    private String[] splitGuestName(String name) {
        String[] couple = name.split("(?i)\\s+(?:et|&)\\s+", 2);
        if (couple.length == 2) {
            return couple;
        }
        if (name.length() <= 22 || !name.contains(" ")) {
            return new String[]{name};
        }
        int middle = name.length() / 2;
        int split = name.indexOf(' ', middle);
        if (split < 0) split = name.lastIndexOf(' ', middle);
        return split > 0
                ? new String[]{name.substring(0, split), name.substring(split + 1)}
                : new String[]{name};
    }

    private String sharedSurnameLabel(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.matches("(?i)^m\\.?\\s+et\\s+mme\\.?\\s+.+")) {
            String surname = trimmed.replaceFirst(
                    "(?i)^m\\.?\\s+et\\s+mme\\.?\\s+", "").trim();
            return "M et Mme " + surname;
        }
        if (trimmed.matches("(?i)^monsieur\\s+et\\s+madame\\s+.+")) {
            String surname = trimmed.replaceFirst(
                    "(?i)^monsieur\\s+et\\s+madame\\s+", "").trim();
            return "Monsieur et Madame " + surname;
        }
        return null;
    }

    private void drawMainPanel(PDDocument document, PDPageContentStream c, PDFont scriptFont)
            throws IOException {
        float x = 2.5f * MM, y = 8 * MM, w = 88 * MM, h = 180 * MM;
        fill(c, GREEN);
        roundedRect(c, x, y, w, h, 3 * MM, true, false);

        // Doppio bordo bianco/argento effetto lastra/acrilico, come nella
        // maquette: il pannello sinistro resta bianco-avorio, l'oro e'
        // riservato al tagliando destro.
        stroke(c, PLAQUE_EDGE);
        c.setLineWidth(1.3f);
        roundedRect(c, x, y, w, h, 3 * MM, false, true);
        c.setLineWidth(.55f);
        roundedRect(c, x + 2 * MM, y + 2 * MM, w - 4 * MM, h - 4 * MM,
                2 * MM, false, true);
        stroke(c, new Color(150, 160, 156));
        c.setLineWidth(.35f);
        roundedRect(c, x + 3.2f * MM, y + 3.2f * MM, w - 6.4f * MM,
                h - 6.4f * MM, 1.5f * MM, false, true);
        cornerDetail(c, x, y, w, h);

        if (cornerFlowers != null) {
            PDImageXObject image = LosslessFactory.createFromImage(document, cornerFlowers);
            c.drawImage(image, x + 1 * MM, y + 1 * MM, w - 2 * MM, h - 2 * MM);
        }

        // Quattro borchie metalliche.
        metalStud(c, x + 6 * MM, y + h - 7 * MM, 2.7f * MM);
        metalStud(c, x + w - 6 * MM, y + h - 7 * MM, 2.7f * MM);
        metalStud(c, x + 6 * MM, y + 7 * MM, 2.7f * MM);
        metalStud(c, x + w - 6 * MM, y + 7 * MM, 2.7f * MM);

        c.saveGraphicsState();
        c.transform(Matrix.getTranslateInstance(0, -8 * MM));
        float cx = x + w * .58f;
        float lowerCx = x + w * .45f;
        float tw = 59 * MM;
        centered(c, "Avec la bénédiction des grandes familles :", PDType1Font.TIMES_ROMAN,
                7, cx, 180 * MM, CREAM);
        ornamentalFlourish(c, cx, 174.5f * MM, 24 * MM, PLAQUE_EDGE);
        heart(c, cx, 174.5f * MM, 3.8f * MM, CREAM);
        centeredFit(c, "Tchiengue et Tchomtchi", PDType1Font.TIMES_BOLD,
                17, 11, tw, cx, 166.5f * MM, CREAM);
        centered(c, "les enfants", PDType1Font.TIMES_ROMAN, 8, cx, 160 * MM, CREAM);
        centeredFit(c, "Xaviera Tchomtchi", scriptFont,
                25, 16, tw, cx, 150 * MM, CREAM);
        centered(c, "&", PDType1Font.TIMES_BOLD, 23, cx, 140.5f * MM, CREAM);
        leafBranch(c, cx - 15 * MM, 142 * MM, 12 * MM, false, PLAQUE_EDGE);
        leafBranch(c, cx + 15 * MM, 142 * MM, 12 * MM, true, PLAQUE_EDGE);
        centeredFit(c, "Gill Tchiengue", scriptFont,
                25, 16, tw, cx, 131.5f * MM, CREAM);
        heart(c, cx, 123 * MM, 3 * MM, CREAM);
        centered(c, "Ont la joie de vous convier", PDType1Font.TIMES_ROMAN,
                8, cx, 117 * MM, CREAM);
        centered(c, "à leur union nuptial", PDType1Font.TIMES_ROMAN,
                8, cx, 112.5f * MM, CREAM);
        flourish(c, cx, 107 * MM, 23 * MM, PLAQUE_EDGE);
        centeredFit(c, "Samedi 24 octobre 2026", PDType1Font.TIMES_BOLD,
                16, 11, tw, cx, 99.5f * MM, CREAM);
        flourish(c, cx, 94 * MM, 23 * MM, PLAQUE_EDGE);
        centered(c, "À 15 h précises", PDType1Font.TIMES_ROMAN,
                9, cx, 86.5f * MM, CREAM);
        flourish(c, cx, 81 * MM, 20 * MM, PLAQUE_EDGE);
        centered(c, "Au club PAD", PDType1Font.TIMES_BOLD,
                13, cx, 73.5f * MM, CREAM);
        centered(c, "Total Bonanjo Douala", PDType1Font.TIMES_ROMAN,
                8, cx, 68.5f * MM, CREAM);
        flourish(c, cx, 63 * MM, 20 * MM, PLAQUE_EDGE);
        centered(c, "suivi de la soirée au même endroit", PDType1Font.TIMES_ROMAN,
                7, lowerCx, 57.5f * MM, CREAM);
        centered(c, "à partir de 19h.", PDType1Font.TIMES_ROMAN,
                7, lowerCx, 53.5f * MM, CREAM);
        flourish(c, lowerCx, 49 * MM, 18 * MM, PLAQUE_EDGE);
        centered(c, "Nous vous invitons à vous présenter", PDType1Font.TIMES_ROMAN,
                6.8f, lowerCx, 43.5f * MM, CREAM);
        centered(c, "dans votre tenue de soirée", PDType1Font.TIMES_ROMAN,
                6.8f, lowerCx, 39.5f * MM, CREAM);
        centered(c, "afin de profiter de cette belle fête", PDType1Font.TIMES_ROMAN,
                6.8f, lowerCx, 35.5f * MM, CREAM);
        centered(c, "avec nous.", PDType1Font.TIMES_ROMAN,
                6.8f, lowerCx, 31.5f * MM, CREAM);
        c.restoreGraphicsState();
    }

    private void drawTicket(PDPageContentStream c, Guest guest) throws IOException {
        float x = 92.5f * MM, y = 8 * MM, w = 45 * MM, h = 180 * MM;
        float cx = x + w / 2;
        fill(c, GREEN);
        stroke(c, GOLD);
        c.setLineWidth(1);
        ticketPath(c, x, y, w, h, 3 * MM, 6 * MM);
        c.fillAndStroke();

        c.saveGraphicsState();
        c.transform(Matrix.getTranslateInstance(0, -14 * MM));
        heart(c, cx, 184 * MM, 3.2f * MM, CREAM);
        ornamentalFlourish(c, cx, 178.5f * MM, 18 * MM, GOLD);
        centered(c, "Invitation", PDType1Font.TIMES_BOLD,
                18, cx, 168.5f * MM, CREAM);
        if (isCouple(guest.getNome())) {
            heart(c, x + w - 8 * MM, 171 * MM, 2.5f * MM, CREAM);
            heart(c, x + w - 4.5f * MM, 171 * MM, 2.5f * MM, CREAM);
        } else {
            heart(c, x + w - 6 * MM, 171 * MM, 2.5f * MM, CREAM);
        }
        flourish(c, cx, 163 * MM, 20 * MM, GOLD);
        centered(c, invitationTitle(guest.getNome()), PDType1Font.TIMES_ROMAN,
                9, cx, 154.5f * MM, CREAM);
        centeredFit(c, displayName(guest.getNome()), PDType1Font.TIMES_BOLD,
                13, 7, 36 * MM, cx, 147.5f * MM, CREAM);
        dotted(c, x + 4 * MM, x + w - 4 * MM, 142 * MM);
        flourish(c, cx, 135 * MM, 19 * MM, GOLD);

        centered(c, "Nous avons le plaisir", PDType1Font.TIMES_ROMAN,
                8, cx, 127 * MM, CREAM);
        centered(c, "de vous convier à la table", PDType1Font.TIMES_ROMAN,
                8, cx, 122 * MM, CREAM);
        centered(c, "N°  " + valueOrDash(guest.getTavolo()), PDType1Font.TIMES_BOLD,
                14, cx, 111.5f * MM, CREAM);
        leafBranch(c, cx - 13 * MM, 112 * MM, 9 * MM, false, GOLD);
        leafBranch(c, cx + 13 * MM, 112 * MM, 9 * MM, true, GOLD);
        dotted(c, cx - 4 * MM, cx + 4 * MM, 107 * MM);
        flourish(c, cx, 100 * MM, 18 * MM, GOLD);
        centered(c, "Nommée :", PDType1Font.TIMES_ROMAN, 8, cx, 91 * MM, CREAM);
        centeredFit(c, resolveTableName(guest), PDType1Font.TIMES_BOLD,
                12, 7, 34 * MM, cx, 85 * MM, CREAM);
        dotted(c, x + 5 * MM, x + w - 5 * MM, 80 * MM);

        centered(c, "À l'occasion de notre mariage.", PDType1Font.TIMES_ROMAN,
                6.5f, cx, 69 * MM, CREAM);
        ornamentalFlourish(c, cx, 60 * MM, 20 * MM, GOLD);
        heart(c, cx, 56 * MM, 3.5f * MM, CREAM);
        flourish(c, cx, 49 * MM, 22 * MM, GOLD);
        centered(c, "Merci de confirmer", PDType1Font.TIMES_ROMAN,
                8, cx, 38 * MM, CREAM);
        centered(c, "votre présence.", PDType1Font.TIMES_ROMAN,
                8, cx, 32.5f * MM, CREAM);
        c.restoreGraphicsState();
    }

    /** Toglie l'onorifico (già mostrato come titolo sopra) dal nome
     * visualizzato, cosi' "Mme Anna Bianchi" non appare due volte. */
    private String displayName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase();
        String sharedSurname = trimmed.replaceFirst(
                "(?i)^(?:m\\.?\\s+et\\s+mme\\.?|monsieur\\s+et\\s+madame)\\s+", "");
        if (!sharedSurname.equals(trimmed)) {
            return sharedSurname.trim();
        }
        for (String prefix : new String[]{"mme ", "madame ", "m. ", "monsieur "}) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private String resolveTableName(Guest guest) {
        if (guest.getNomeTavolo() != null && !guest.getNomeTavolo().isBlank()) {
            return guest.getNomeTavolo().trim();
        }
        try {
            return TABLE_NAMES.getOrDefault(Integer.parseInt(guest.getTavolo().trim()), "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isCouple(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        return normalized.contains(" et ") || normalized.contains(" & ");
    }

    private String invitationTitle(String name) {
        if (isCouple(name)) {
            return "M. et Mme";
        }
        String normalized = name == null ? "" : name.trim().toLowerCase();
        if (normalized.startsWith("mme ") || normalized.startsWith("madame ")) {
            return "Mme";
        }
        if (normalized.startsWith("m. ") || normalized.startsWith("monsieur ")) {
            return "M.";
        }
        return "Invité(e)";
    }

    private void metalStud(PDPageContentStream c, float cx, float cy, float r) throws IOException {
        fill(c, new Color(75, 82, 80));
        circle(c, cx, cy, r, true);
        fill(c, new Color(226, 231, 229));
        circle(c, cx - r * .18f, cy + r * .18f, r * .72f, true);
        stroke(c, new Color(95, 102, 100));
        c.setLineWidth(.4f);
        circle(c, cx, cy, r, false);
    }

    /** Sagoma "badge": rettangolo arrotondato con un'unica tacca semicircolare
     * in alto al centro, bordi lisci — come nella maquette (nessun dente o
     * perforazione sul lato sinistro). */
    private void ticketPath(PDPageContentStream c, float x, float y, float w, float h,
                            float radius, float notch) throws IOException {
        float top = y + h, right = x + w, cx = x + w / 2;
        c.moveTo(x + radius, y);
        c.lineTo(right - radius, y);
        c.curveTo(right, y, right, y, right, y + radius);
        c.lineTo(right, top - radius);
        c.curveTo(right, top, right, top, right - radius, top);
        c.lineTo(cx + notch, top);
        c.curveTo(cx + notch, top - notch, cx - notch, top - notch, cx - notch, top);
        c.lineTo(x + radius, top);
        c.curveTo(x, top, x, top, x, top - radius);
        float currentY = top - radius;
        float tooth = 3.2f * MM;
        while (currentY - tooth > y + radius) {
            c.lineTo(x, currentY - tooth * .38f);
            c.curveTo(x + .9f * MM, currentY - tooth * .48f,
                    x + .9f * MM, currentY - tooth * .72f,
                    x, currentY - tooth * .82f);
            currentY -= tooth;
            c.lineTo(x, currentY);
        }
        c.lineTo(x, y + radius);
        c.curveTo(x, y, x, y, x + radius, y);
        c.closePath();
    }

    private void cornerDetail(PDPageContentStream c, float x, float y, float w, float h)
            throws IOException {
        stroke(c, PLAQUE_EDGE);
        c.setLineWidth(.55f);
        float r = 10 * MM;

        c.moveTo(x + w - r, y + h - 1.5f * MM);
        c.curveTo(x + w - r, y + h - 7 * MM,
                x + w - 5.5f * MM, y + h - r,
                x + w - 1.5f * MM, y + h - r);
        c.moveTo(x + 1.5f * MM, y + r);
        c.curveTo(x + 5.5f * MM, y + r,
                x + r, y + 7 * MM,
                x + r, y + 1.5f * MM);

        float d = 4.2f * MM;
        c.moveTo(x + 1.2f * MM, y + h - d);
        c.lineTo(x + d, y + h - 1.2f * MM);
        c.moveTo(x + w - d, y + 1.2f * MM);
        c.lineTo(x + w - 1.2f * MM, y + d);
        c.stroke();
    }

    private void centered(PDPageContentStream c, String text, PDFont font,
                          float size, float cx, float y, Color color) throws IOException {
        fill(c, color);
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(cx - font.getStringWidth(text) / 1000 * size / 2, y);
        c.showText(text);
        c.endText();
    }

    private void text(PDPageContentStream c, String text, PDFont font,
                      float size, float x, float y, Color color) throws IOException {
        fill(c, color);
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        c.showText(text);
        c.endText();
    }

    private void centeredFit(PDPageContentStream c, String text, PDFont font,
                             float max, float min, float width, float cx, float y, Color color)
            throws IOException {
        float size = max;
        while (size > min && font.getStringWidth(text) / 1000 * size > width) size -= .5f;
        centered(c, text, font, size, cx, y, color);
    }

    private void flourish(PDPageContentStream c, float cx, float y, float width, Color color)
            throws IOException {
        stroke(c, color);
        c.setLineWidth(.5f);
        c.moveTo(cx - width / 2, y);
        c.lineTo(cx - 1.5f * MM, y);
        c.moveTo(cx + 1.5f * MM, y);
        c.lineTo(cx + width / 2, y);
        c.stroke();
        fill(c, color);
        float d = 1 * MM;
        c.moveTo(cx, y + d);
        c.lineTo(cx + d, y);
        c.lineTo(cx, y - d);
        c.lineTo(cx - d, y);
        c.closePath();
        c.fill();
    }

    private void ornamentalFlourish(PDPageContentStream c, float cx, float y, float width, Color color)
            throws IOException {
        stroke(c, color);
        c.setLineWidth(.55f);
        float half = width / 2;
        c.moveTo(cx - half, y);
        c.curveTo(cx - half * .72f, y + 2.2f * MM,
                cx - half * .45f, y - 2.2f * MM, cx - 1.8f * MM, y);
        c.moveTo(cx + half, y);
        c.curveTo(cx + half * .72f, y + 2.2f * MM,
                cx + half * .45f, y - 2.2f * MM, cx + 1.8f * MM, y);
        c.stroke();
        fill(c, color);
        float d = 1.1f * MM;
        c.moveTo(cx, y + d);
        c.lineTo(cx + d, y);
        c.lineTo(cx, y - d);
        c.lineTo(cx - d, y);
        c.closePath();
        c.fill();
    }

    private void leafBranch(PDPageContentStream c, float cx, float y, float length,
                            boolean pointsRight, Color color) throws IOException {
        float direction = pointsRight ? 1 : -1;
        stroke(c, color);
        fill(c, color);
        c.setLineWidth(.55f);
        float start = cx - direction * length / 2;
        float end = cx + direction * length / 2;
        c.moveTo(start, y);
        c.curveTo(cx - direction * length * .15f, y + .5f * MM,
                cx + direction * length * .15f, y - .5f * MM, end, y);
        c.stroke();
        for (int i = 1; i <= 4; i++) {
            float px = start + direction * length * i / 5;
            float side = i % 2 == 0 ? -1 : 1;
            float tipX = px + direction * 2.3f * MM;
            float tipY = y + side * 2.1f * MM;
            c.moveTo(px, y);
            c.curveTo(px + direction * .8f * MM, y + side * 1.8f * MM,
                    tipX - direction * .5f * MM, tipY, tipX, tipY);
            c.curveTo(tipX - direction * 1.2f * MM, tipY - side * .8f * MM,
                    px + direction * .4f * MM, y + side * .3f * MM, px, y);
            c.fill();
        }
    }

    private void dotted(PDPageContentStream c, float x1, float x2, float y) throws IOException {
        stroke(c, GOLD);
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

    private void roundedRect(PDPageContentStream c, float x, float y, float w, float h,
                             float r, boolean fill, boolean stroke) throws IOException {
        float k = .55228475f * r;
        c.moveTo(x + r, y);
        c.lineTo(x + w - r, y);
        c.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        c.lineTo(x + w, y + h - r);
        c.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        c.lineTo(x + r, y + h);
        c.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        c.lineTo(x, y + r);
        c.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        c.closePath();
        if (fill && stroke) c.fillAndStroke(); else if (fill) c.fill(); else c.stroke();
    }

    private void circle(PDPageContentStream c, float cx, float cy, float r, boolean fill)
            throws IOException {
        float k = .55228475f * r;
        c.moveTo(cx + r, cy);
        c.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        c.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        c.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        c.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
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
        return value == null || value.isBlank() ? "-" : value;
    }
}
