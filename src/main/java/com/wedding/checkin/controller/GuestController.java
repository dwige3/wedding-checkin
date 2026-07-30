package com.wedding.checkin.controller;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
     * Importa/aggiorna la lista invitati da un CSV testuale
     * (id,nome,tavolo,nomeTavolo per riga; il quarto campo e' opzionale
     * per compatibilita' con liste generate senza nome tavolo).
     * Non tocca lo stato di check-in di invitati gia' presenti.
     */
    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Transactional
    public Map<String, Object> importCsv(@RequestBody String csv,
                                          @RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkStaffPin(pin);
        synchronized (lock) {
            int imported = 0;
            Set<String> importedIds = new HashSet<>();
            if (csv != null) {
                for (String rawLine : csv.split("\\r?\\n")) {
                    String line = rawLine.trim();
                    if (line.isEmpty()) continue;
                    List<String> parts = parseCsvLine(line);
                    if (parts.size() < 3) continue;
                    String id = parts.get(0).trim();
                    if (id.equalsIgnoreCase("id")) continue; // salta l'header
                    String nome = parts.get(1).trim();
                    String tavolo = parts.get(2).trim();
                    String nomeTavolo = parts.size() >= 4 ? parts.get(3).trim() : "";
                    if (id.isEmpty() || nome.isEmpty()) continue;

                    Guest guest = repo.findById(id)
                            .orElseGet(() -> new Guest(id, nome, tavolo, nomeTavolo));
                    guest.setNome(nome);
                    guest.setTavolo(tavolo);
                    guest.setNomeTavolo(nomeTavolo);
                    repo.save(guest);
                    importedIds.add(id);
                    imported++;
                }
            }

            if (importedIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Il CSV non contiene invitati validi");
            }

            List<Guest> obsolete = repo.findAll().stream()
                    .filter(guest -> !importedIds.contains(guest.getId()))
                    .toList();
            repo.deleteAll(obsolete);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imported", imported);
            result.put("removed", obsolete.size());
            return result;
        }
    }

    /**
     * Parser CSV minimale compatibile con i file prodotti da Excel: supporta
     * campi tra virgolette, virgole nei campi e doppi apici escapati ("").
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    /**
     * Scansione di un QR all'ingresso. Segna il check-in solo se non era gia' stato fatto.
     */
    @PostMapping(value = "/scan", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> scanBody(@RequestBody String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty() || id.length() > 256) {
            return unknownResult();
        }
        return scanGuest(id);
    }

    /**
     * Rotta mantenuta per compatibilità con eventuali client precedenti.
     * Il frontend usa /api/scan con il codice nel corpo, così QR estranei
     * contenenti URL, slash o altri caratteri speciali non rompono la richiesta.
     */
    @PostMapping("/scan/{id}")
    public Map<String, Object> scan(@PathVariable String id) {
        return scanGuest(id);
    }

    private Map<String, Object> scanGuest(String id) {
        synchronized (lock) {
            Optional<Guest> found = repo.findById(id);
            if (found.isEmpty()) {
                return unknownResult();
            }
            Map<String, Object> result = new LinkedHashMap<>();
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
            result.put("nomeTavolo", guest.getNomeTavolo());
            return result;
        }
    }

    private Map<String, Object> unknownResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "unknown");
        return result;
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
            result.put("nomeTavolo", guest.getNomeTavolo());
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
