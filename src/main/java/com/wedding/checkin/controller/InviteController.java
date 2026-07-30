package com.wedding.checkin.controller;

import com.wedding.checkin.model.Guest;
import com.wedding.checkin.repository.GuestRepository;
import com.wedding.checkin.service.InviteGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final GuestRepository repo;
    private final InviteGeneratorService generator;
    private final String staffPin;

    public InviteController(GuestRepository repo, InviteGeneratorService generator,
                            @Value("${staff.pin}") String staffPin) {
        this.repo = repo;
        this.generator = generator;
        this.staffPin = staffPin;
    }

    @GetMapping(value = "/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> one(@PathVariable String id,
                                      @RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkPin(pin);
        Guest guest = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitato non trovato"));
        return download(generator.generate(guest), filename(guest), MediaType.APPLICATION_PDF);
    }

    @GetMapping(value = "/all.zip", produces = "application/zip")
    public ResponseEntity<byte[]> all(
            @RequestHeader(value = "X-Staff-Pin", required = false) String pin) {
        checkPin(pin);
        List<Guest> guests = repo.findAll();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Guest guest : guests) {
                zip.putNextEntry(new ZipEntry(filename(guest)));
                zip.write(generator.generate(guest));
                zip.closeEntry();
            }
            zip.finish();
            return download(bytes.toByteArray(), "inviti.zip",
                    MediaType.parseMediaType("application/zip"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossibile creare l'archivio degli inviti", e);
        }
    }

    private void checkPin(String pin) {
        if (pin == null || !pin.equals(staffPin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN staff mancante o errato");
        }
    }

    private ResponseEntity<byte[]> download(byte[] body, String name, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(name).build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private String filename(Guest guest) {
        String safeName = Normalizer.normalize(guest.getNome().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "invito_" + guest.getId() + "_" + safeName + ".pdf";
    }
}
