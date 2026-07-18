# Wedding Check-in — Tamara & Nicolas

App Spring Boot per il controllo ingressi al matrimonio tramite scansione QR.
Il controllo "gia' entrato / non ancora entrato" e' gestito lato server (database H2 su file), quindi funziona correttamente anche con **piu' dispositivi di scansione contemporaneamente** (es. piu' varchi d'ingresso), a differenza di una soluzione solo client-side.

## Requisiti

- Java 17 o superiore
- Maven 3.8+
- Una rete WiFi condivisa tra il laptop che fa da server e i telefoni/tablet usati per scansionare

## Avvio

```bash
cd wedding-checkin
mvn spring-boot:run
```

L'app parte sulla porta **8080** ed e' raggiungibile da qualsiasi dispositivo sulla stessa rete WiFi.

1. Trova l'indirizzo IP locale del laptop (es. `192.168.1.15`):
   - macOS/Linux: `ifconfig | grep inet`
   - Windows: `ipconfig`
2. Da ogni telefono/tablet, apri il browser su:
   ```
   http://192.168.1.15:8080
   ```
   (sostituendo con l'IP reale del laptop)

## Utilizzo

1. **Importa invitati**: incolla il contenuto di `import_checkin.csv` (generato dallo script Python `generate_invite.py`) nella tab "Importa invitati" e premi "Importa lista".
2. **Scansiona ingresso**: nella tab "Scansiona ingresso", premi "Avvia fotocamera" e inquadra i QR dei biglietti.
   - ✅ Prima scansione → ingresso registrato, mostra nome e tavolo.
   - ⚠️ Scansione ripetuta → avviso di doppio ingresso con orario del primo accesso. Lo staff puo' comunque forzare un nuovo ingresso in caso di errore.
3. Il contatore "Ingressi registrati: X / Y" si aggiorna automaticamente ogni 5 secondi.

## Dati e persistenza

I dati sono salvati in un database H2 su file nella cartella `./data/` (creata automaticamente al primo avvio), quindi sopravvivono al riavvio dell'applicazione. Per azzerare tutto prima dell'evento, usa il pulsante "Azzera tutti i check-in" oppure elimina la cartella `data/`.

## PIN staff

Le operazioni sensibili — **importare la lista**, **azzerare i check-in** e **forzare un ingresso duplicato** — richiedono un PIN staff, altrimenti chiunque sulla stessa WiFi (inclusi eventualmente gli invitati) potrebbe eseguirle. Il PIN si inserisce nel campo "PIN staff" nella tab "Importa invitati" e viene richiesto anche per l'override durante la scansione.

Il PIN di default è `2026`, impostato in `src/main/resources/application.properties` (chiave `staff.pin`). **Cambialo prima dell'evento** con un valore noto solo allo staff. La scansione normale dei biglietti (`/api/scan/{id}`) resta senza PIN, così ogni invitato può passare senza intoppi.

## Test

```bash
mvn test
```

I test in `src/test/java/com/wedding/checkin/GuestControllerTest.java` coprono: prima scansione, scansione duplicata, override e reset con/senza PIN corretto, import con/senza PIN. Usano un database H2 in memoria (`src/test/resources/application.properties`), quindi non toccano mai i dati reali in `./data/`.

## Note

- Il QR sui biglietti deve contenere **solo il codice univoco dell'invitato** (es. `0001`), generato dallo script Python — non il nome in chiaro.
- In caso di dubbi sulla build (es. mancanza di accesso a Maven Central dalla tua rete), verifica la connessione internet: Maven deve poter scaricare le dipendenze Spring Boot al primo avvio.
