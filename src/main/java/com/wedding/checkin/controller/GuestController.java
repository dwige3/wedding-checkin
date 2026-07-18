package com.wedding.checkin.controller;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API per l'app di check-in ingressi.
 * La verifica "puo' entrare / gia' entrato" avviene qui, lato server,
 * cosi' che il controllo sia coerente anche con piu' dispositivi di scansione
 * collegati contemporaneamente (es. piu' ingressi).
 */
@RestController
@RequestMapping("/api")
public class GuestController {

    private static final DateTimeFormatter ORARIO =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Rome"));

    private final GuestRepository repo;
    private final Object lock = new Object();
    private final String staffPin;

    public GuestController(GuestRepository repo, @Value("${staff.pin}") String staffPin) {
        this.repo = repo;
        this.staffPin = staffPin;
    }

    /**
     * Verifica il PIN dello staff per le operazioni sensibili (import, reset, override).
     * Senza questo controllo, chiunque connesso alla stessa WiFi potrebbe azzerare
     * tutti i check-in o forzare ingressi semplicemente conoscendo l'URL dell'app.
     */
    private void checkStaffPin(String pin) {
        if (pin == null || !pin.equals(staffPin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN staff mancante o errato");
        }
    }

    /**
     * Importa/aggiorna la lista invitati da un CSV testuale (id,nome,tavolo per riga).
     * Non tocca lo stato di check-in di invitati gia' presenti.
     */
    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> importCsv(@RequestBody String csv,
                                          @RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkStaffPin(pin);
        int imported = 0;
        if (csv != null) {
            for (String rawLine : csv.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", 3);
                if (parts.length < 3) continue;
                String id = parts[0].trim();
                if (id.equalsIgnoreCase("id")) continue; // salta l'header
                String nome = parts[1].trim();
                String tavolo = parts[2].trim();

                Guest guest = repo.findById(id).orElseGet(() -> new Guest(id, nome, tavolo));
                guest.setNome(nome);
                guest.setTavolo(tavolo);
                repo.save(guest);
                imported++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        return result;
    }

    /**
     * Scansione di un QR all'ingresso. Segna il check-in solo se non era gia' stato fatto.
     */
    @PostMapping("/scan/{id}")
    public Map<String, Object> scan(@PathVariable String id) {
        synchronized (lock) {
            Optional<Guest> found = repo.findById(id);
            Map<String, Object> result = new LinkedHashMap<>();
            if (found.isEmpty()) {
                result.put("status", "unknown");
                return result;
            }
            Guest guest = found.get();
            if (!guest.isCheckedIn()) {
                guest.setCheckedIn(true);
                guest.setCheckedInAt(Instant.now());
                repo.save(guest);
                result.put("status", "ok");
            } else {
                result.put("status", "duplicate");
                result.put("orario", ORARIO.format(guest.getCheckedInAt()));
            }
            result.put("nome", guest.getNome());
            result.put("tavolo", guest.getTavolo());
            return result;
        }
    }

    /**
     * Forza un nuovo check-in per un invitato gia' segnato come entrato
     * (da usare solo dallo staff in caso di errore).
     */
    @PostMapping("/override/{id}")
    public Map<String, Object> override(@PathVariable String id,
                                         @RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkStaffPin(pin);
        synchronized (lock) {
            Optional<Guest> found = repo.findById(id);
            Map<String, Object> result = new LinkedHashMap<>();
            if (found.isEmpty()) {
                result.put("status", "unknown");
                return result;
            }
            Guest guest = found.get();
            guest.setCheckedIn(true);
            guest.setCheckedInAt(Instant.now());
            repo.save(guest);
            result.put("status", "ok");
            result.put("nome", guest.getNome());
            result.put("tavolo", guest.getTavolo());
            return result;
        }
    }

    /** Azzera tutti i check-in (es. per una prova generale prima dell'evento). */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkStaffPin(pin);
        synchronized (lock) {
            List<Guest> all = repo.findAll();
            for (Guest g : all) {
                g.setCheckedIn(false);
                g.setCheckedInAt(null);
            }
            repo.saveAll(all);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reset", all.size());
            return result;
        }
    }

    /** Statistiche rapide per la dashboard. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total = repo.count();
        long checkedIn = repo.findAll().stream().filter(Guest::isCheckedIn).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("checkedIn", checkedIn);
        return result;
    }
}
