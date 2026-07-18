package com.wedding.checkin;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test della logica di check-in esposta da GuestController: prima scansione,
 * scansione duplicata, override e reset, con particolare attenzione al
 * controllo del PIN staff sulle operazioni sensibili (import/reset/override).
 * Usa un DB H2 in memoria (vedi src/test/resources/application.properties),
 * quindi non tocca mai il file data/weddingdb.mv.db dell'evento reale.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuestControllerTest {

    private static final String PIN_CORRETTO = "1234"; // combacia con src/test/resources/application.properties
    private static final String PIN_SBAGLIATO = "0000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        repo.save(new Guest("0001", "Mario Rossi", "8"));
    }

    @Test
    void scanDiUnCodiceSconosciutoRestituisceUnknown() throws Exception {
        mockMvc.perform(post("/api/scan/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void primaScansioneRegistraLIngresso() throws Exception {
        mockMvc.perform(post("/api/scan/0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.nome").value("Mario Rossi"))
                .andExpect(jsonPath("$.tavolo").value("8"));

        assertTrue(repo.findById("0001").orElseThrow().isCheckedIn());
    }

    @Test
    void secondaScansioneSegnalaDuplicato() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/scan/0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"))
                .andExpect(jsonPath("$.orario").exists());
    }

    @Test
    void overrideSenzaPinVieneRifiutato() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/override/0001"))
                .andExpect(status().isForbidden());
    }

    @Test
    void overrideConPinSbagliatoVieneRifiutato() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/override/0001").header("X-Staff-Pin", PIN_SBAGLIATO))
                .andExpect(status().isForbidden());
    }

    @Test
    void overrideConPinCorrettoForzaNuovoIngresso() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/override/0001").header("X-Staff-Pin", PIN_CORRETTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void resetSenzaPinVieneRifiutato() throws Exception {
        mockMvc.perform(post("/api/reset"))
                .andExpect(status().isForbidden());

        // il check-in esistente non deve essere toccato dal tentativo respinto
        mockMvc.perform(post("/api/scan/0001"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void resetConPinAzzeraTuttiICheckIn() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/reset").header("X-Staff-Pin", PIN_CORRETTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset").value(1));

        mockMvc.perform(post("/api/scan/0001"))
                .andExpect(jsonPath("$.status").value("ok")); // di nuovo libero dopo il reset
    }

    @Test
    void importSenzaPinVieneRifiutato() throws Exception {
        mockMvc.perform(post("/api/import")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("id,nome,tavolo\n0002,Anna Bianchi,3"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/scan/0002"))
                .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void importConPinAggiungeNuoviInvitatiSenzaToccareICheckInEsistenti() throws Exception {
        mockMvc.perform(post("/api/scan/0001")).andExpect(status().isOk());

        mockMvc.perform(post("/api/import")
                        .header("X-Staff-Pin", PIN_CORRETTO)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("id,nome,tavolo\n0001,Mario Rossi,8\n0002,Anna Bianchi,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        mockMvc.perform(post("/api/scan/0002"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.nome").value("Anna Bianchi"));

        // l'invitato 0001 gia' entrato deve restare tale dopo il re-import
        mockMvc.perform(post("/api/scan/0001"))
                .andExpect(jsonPath("$.status").value("duplicate"));
    }
}
