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

L'app parte sulla porta **8443** in **HTTPS** (certificato self-signed incluso) ed e' raggiungibile da qualsiasi dispositivo sulla stessa rete WiFi. L'HTTPS e' necessario: i browser dei telefoni bloccano l'accesso alla fotocamera su indirizzi non sicuri (`http://`), quindi lo scanner QR non funzionerebbe con un URL `http://`.

1. Trova l'indirizzo IP locale del laptop (es. `192.168.1.15`):
   - macOS/Linux: `ifconfig | grep inet`
   - Windows: `ipconfig`
2. Da ogni telefono/tablet, apri il browser su:
   ```
   https://192.168.1.15:8443
   ```
   (sostituendo con l'IP reale del laptop)
3. Il browser mostra un avviso perche' il certificato e' self-signed (non emesso da un'autorita' riconosciuta) — e' normale e atteso. Su Chrome/Safari: tocca "Avanzate" (o "Dettagli") poi "Procedi comunque"/"Visita questo sito web". Va fatto una sola volta per dispositivo.

## Utilizzo

1. **Importa invitati**: incolla il contenuto di `import_checkin.csv` (generato dallo script Python `generate_invite.py`) nella tab "Importa invitati" e premi "Importa lista".
2. **Scansiona ingresso**: nella tab "Scansiona ingresso", premi "Avvia fotocamera" e inquadra i QR dei biglietti.
   - ✅ Prima scansione → ingresso registrato, mostra nome, tavolo (e nome tavolo se presente).
   - ⚠️ Scansione ripetuta → avviso di doppio ingresso con orario del primo accesso. Lo staff puo' comunque forzare un nuovo ingresso in caso di errore.
3. Il contatore "Ingressi registrati: X / Y" si aggiorna automaticamente ogni 5 secondi.

## Formato del CSV di import

```
id,nome,tavolo,nomeTavolo
0001,Mario Rossi,8,Pazienza
0002,Anna Bianchi,3,Amore
```

Il quarto campo (`nomeTavolo`) e' opzionale: un CSV a 3 colonne (`id,nome,tavolo`, senza nome tavolo) resta valido e viene importato normalmente. Il CSV generato da `generate_invite.py` include sempre tutte e 4 le colonne.

## Dati e persistenza

I dati sono salvati in un database H2 su file nella cartella `./data/` (creata automaticamente al primo avvio), quindi sopravvivono al riavvio dell'applicazione. Per azzerare tutto prima dell'evento, usa il pulsante "Azzera tutti i check-in" oppure elimina la cartella `data/`.

## PIN staff

Le operazioni sensibili — **importare la lista**, **azzerare i check-in** e **forzare un ingresso duplicato** — richiedono un PIN staff, altrimenti chiunque sulla stessa WiFi (inclusi eventualmente gli invitati) potrebbe eseguirle. Il PIN si inserisce nel campo "PIN staff" nella tab "Importa invitati" e viene richiesto anche per l'override durante la scansione.

Il PIN di default è `2026`, impostato in `src/main/resources/application.properties` (chiave `staff.pin`). **Cambialo prima dell'evento** con un valore noto solo allo staff. La scansione normale dei biglietti (`/api/scan/{id}`) resta senza PIN, così ogni invitato può passare senza intoppi.

## Test

```bash
mvn test
```

## Generazione inviti in Java

La generazione degli inviti è integrata nell'app Spring Boot tramite PDFBox e
ZXing: Python non è necessario. Dopo aver importato la lista invitati, inserisci
il PIN staff nella tab **Importa invitati** e premi **Scarica inviti (.zip)**.

Sono disponibili anche gli endpoint:

- `GET /api/invites/{id}.pdf` per scaricare un singolo invito;
- `GET /api/invites/all.zip` per scaricare tutti gli inviti.

Entrambi richiedono il PIN nell'header `X-Staff-Pin`. Il QR contiene soltanto
l'ID univoco dell'invitato.

I test in `src/test/java/com/wedding/checkin/GuestControllerTest.java` coprono: prima scansione, scansione duplicata, override e reset con/senza PIN corretto, import con/senza PIN. Usano un database H2 in memoria (`src/test/resources/application.properties`), quindi non toccano mai i dati reali in `./data/`.

## Biglietti PDF (generate_invite.py)

Gli inviti sono generati in formato "ticket" orizzontale (200x100mm): pannello invito a sinistra, tagliando con QR a destra, separati da una linea di strappo perforata — tema verde scuro/oro con ornamenti grafici vettoriali (nessuna immagine esterna richiesta, quindi nessun font o asset da scaricare).

Dipendenze Python richieste:

```bash
pip install qrcode reportlab pandas openpyxl
```

Prima di lanciare lo script, apri `generate_invite.py` e imposta `OUTPUT_DIR` con il percorso reale sul tuo PC dove vuoi salvare PDF e CSV (la cartella viene creata automaticamente se non esiste).

## Prova end-to-end: genera un biglietto e scannerizzalo da telefono

1. **Genera un biglietto di prova.** Dal laptop:
   ```bash
   pip install qrcode reportlab pandas openpyxl
   python generate_invite.py
   ```
   Con lo script cosi' com'e' viene creato `invito_0001_mario_rossi.pdf` nella cartella impostata in `OUTPUT_DIR` (di default `C:/Document/sposa`), per l'invitato di prova `0001,Mario Rossi,8,Pazienza`.

2. **Avvia l'app** dal laptop:
   ```bash
   mvn spring-boot:run
   ```

3. **Importa l'invitato di prova.** Dal laptop (o da un telefono), apri `https://<ip-laptop>:8443`, accetta l'avviso del certificato, vai su "Importa invitati", inserisci il PIN staff (default `2026`, in `application.properties`) e incolla:
   ```
   id,nome,tavolo,nomeTavolo
   0001,Mario Rossi,8,Pazienza
   ```
   poi premi "Importa lista" — dovrebbe confermare "Importati 1 invitati."

4. **Mostra il QR al telefono.** Apri il PDF del biglietto su uno schermo (laptop, tablet) oppure stampalo. Il QR contiene solo il testo `0001`.

5. **Scansiona.** Sul telefono (connesso alla stessa WiFi), vai sulla tab "Scansiona ingresso", premi "Avvia fotocamera", concedi il permesso quando richiesto, e inquadra il QR. Dovresti vedere "✅ INGRESSO OK" con nome "Mario Rossi" e "Tavolo 8 · Pazienza".

6. **Verifica il doppio ingresso.** Inquadra di nuovo lo stesso QR: dovrebbe apparire "⚠️ GIÀ REGISTRATO" con l'orario del primo ingresso e l'opzione "Registra comunque l'ingresso" (richiede di nuovo il PIN).

7. **Prima dell'evento vero**, usa "Azzera tutti i check-in" (richiede PIN) per ripulire i dati di prova.

Se "Avvia fotocamera" da telefono non chiede il permesso o fallisce subito, la causa piu' probabile e' che l'URL non sia `https://` (vedi sezione HTTPS sopra) oppure che l'avviso del certificato non sia stato accettato.

## Note

- Il QR sui biglietti deve contenere **solo il codice univoco dell'invitato** (es. `0001`), generato dallo script Python — non il nome in chiaro.
- In caso di dubbi sulla build (es. mancanza di accesso a Maven Central dalla tua rete), verifica la connessione internet: Maven deve poter scaricare le dipendenze Spring Boot al primo avvio.
- Il certificato HTTPS incluso (`src/main/resources/keystore.p12`) e' self-signed e va bene per l'uso privato dell'evento: non e' pensato per essere esposto su internet.
