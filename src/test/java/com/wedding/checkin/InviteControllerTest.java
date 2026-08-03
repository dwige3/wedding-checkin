package com.wedding.checkin;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InviteControllerTest {

    private static final String PIN = "1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        repo.save(new Guest("0001", "Mario Rossi", "2", "Pazienza"));
        repo.save(new Guest("0002", "Anna Bianchi", "3", "Fiducia"));
    }

    @Test
    void downloadSingoloRichiedePin() throws Exception {
        mockMvc.perform(get("/api/invites/0001.pdf"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/invites/0001.pdf").header("X-Staff-Pin", "errato"))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadSingoloRestituiscePdfENomeFile() throws Exception {
        mockMvc.perform(get("/api/invites/0001.pdf").header("X-Staff-Pin", PIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("mario_rossi.pdf")))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 5_000);
                    assertEquals("%PDF", new String(body, 0, 4));
                });
    }

    @Test
    void templateVerticaleRestituiscePdfNelFormatoCorretto() throws Exception {
        byte[] body = mockMvc.perform(get("/api/invites/0001.pdf")
                        .queryParam("template", "vertical")
                        .header("X-Staff-Pin", PIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = PDDocument.load(body)) {
            var box = document.getPage(0).getMediaBox();
            assertEquals(140, box.getWidth() * 25.4 / 72, 0.1);
            assertEquals(200, box.getHeight() * 25.4 / 72, 0.1);
        }
    }

    @Test
    void templateSconosciutoRestituisce400() throws Exception {
        mockMvc.perform(get("/api/invites/0001.pdf")
                        .queryParam("template", "sconosciuto")
                        .header("X-Staff-Pin", PIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadSingoloRestituisce404PerIdSconosciuto() throws Exception {
        mockMvc.perform(get("/api/invites/9999.pdf").header("X-Staff-Pin", PIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void zipContieneUnPdfPerOgniInvitato() throws Exception {
        byte[] body = mockMvc.perform(get("/api/invites/all.zip")
                        .header("X-Staff-Pin", PIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("inviti.zip")))
                .andReturn().getResponse().getContentAsByteArray();

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
                byte[] pdf = zip.readAllBytes();
                assertEquals("%PDF", new String(pdf, 0, 4));
            }
        }
        assertEquals(Set.of(
                "mario_rossi.pdf",
                "anna_bianchi.pdf"), entries);
    }

    @Test
    void zipVuotoRestaUnArchivioValido() throws Exception {
        repo.deleteAll();

        byte[] body = mockMvc.perform(get("/api/invites/all.zip")
                        .header("X-Staff-Pin", PIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            assertEquals(null, zip.getNextEntry());
        }
    }
}
