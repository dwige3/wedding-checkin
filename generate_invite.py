#!/usr/bin/env python3
"""
Generatore di inviti di matrimonio in stile "ticket": biglietto principale a
sinistra + tagliando con QR a destra, separati da una linea di strappo
perforata, come i biglietti d'ingresso classici.
Progettato per essere riutilizzato su una lista di 400 invitati.
"""

import qrcode
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, white
import os

# ---------- CONFIGURAZIONE MATRIMONIO (fissa per tutti gli inviti) ----------
SPOSI = "Tamara & Nicolas"
DATA_EVENTO = "20 Agosto 2026"
LUOGO = "Milano"
FRASE_FINALE = "Vi aspettiamo per festeggiare insieme a noi"

# ---------- DIMENSIONI TICKET (formato orizzontale, ~200x100mm) ----------
PAGE_W, PAGE_H = 200 * mm, 100 * mm
MARGIN = 5 * mm
DIVIDER_RATIO = 0.66  # quota di larghezza occupata dal pannello invito (sinistra)

# ---------- COLORI (tema elegante verde scuro/oro, come da esempio) ----------
COLOR_BG = HexColor("#12301F")
COLOR_GOLD = HexColor("#C9A961")
COLOR_GOLD_SOFT = HexColor("#9C8352")
COLOR_CREAM = HexColor("#F5EFE0")
COLOR_WHITE = white
COLOR_LEAF_DARK = HexColor("#183C2A")
COLOR_LEAF = HexColor("#416B47")
COLOR_LEAF_LIGHT = HexColor("#78946D")
COLOR_ROSE_SHADOW = HexColor("#D8CDB3")

# ---------- NOMI DEI TAVOLI (associati in ordine al numero del tavolo) ----------
# Basta scrivere il numero del tavolo per ogni invitato: il nome del tavolo
# viene aggiunto automaticamente da questa mappa (vedi table_name_for),
# senza doverlo ripetere a mano per ognuno dei 400 invitati.
TABLE_NAMES = {
    1: "Amore",
    2: "Pazienza",
    3: "Fiducia",
    4: "Rispetto",
    5: "Gioia",
    6: "Serenità",
    7: "Armonia",
    8: "Complicità",
    9: "Gratitudine",
    10: "Speranza",
    11: "Tenerezza",
    12: "Felicità",
    13: "Sorriso",
    14: "Abbraccio",
    15: "Dolcezza",
}


def table_name_for(table_number) -> str:
    """Restituisce il nome del tavolo associato al numero, se presente in
    TABLE_NAMES. Ritorna stringa vuota se il numero non e' mappato (es.
    tavoli aggiunti dopo, o numero non valido) cosi' il biglietto resta
    comunque generabile."""
    try:
        return TABLE_NAMES.get(int(str(table_number).strip()), "")
    except (TypeError, ValueError):
        return ""


# ---------------------------------------------------------------------------
# Utility di disegno
# ---------------------------------------------------------------------------

def tracked(text, letter_gap=" ", word_gap="   "):
    """Trasforma 'Invito di nozze' in 'I N V I T O   D I   N O Z Z E'
    per un effetto tipografico con lettere spaziate (small caps tracking)."""
    words = text.upper().split(" ")
    return word_gap.join(letter_gap.join(w) for w in words)


def fit_font_size(c, text, font, max_size, min_size, max_width):
    """Riduce progressivamente la dimensione del font finche' il testo
    non entra nella larghezza disponibile (evita che nomi lunghi escano
    dal biglietto)."""
    size = max_size
    while size > min_size and c.stringWidth(text, font, size) > max_width:
        size -= 0.5
    return size


def draw_heart(c, cx, cy, size, color):
    """Piccolo cuore vettoriale (due cerchi + triangolo), usato come
    ornamento invece di immagini fotografiche di fiori."""
    c.saveState()
    c.setFillColor(color)
    r = size / 4
    c.circle(cx - r, cy, r, stroke=0, fill=1)
    c.circle(cx + r, cy, r, stroke=0, fill=1)
    p = c.beginPath()
    p.moveTo(cx - size / 2, cy)
    p.lineTo(cx, cy - size / 2)
    p.lineTo(cx + size / 2, cy)
    p.close()
    c.drawPath(p, stroke=0, fill=1)
    c.restoreState()


def draw_flourish(c, cx, cy, width, color):
    """Linea decorativa con piccolo rombo centrale, usata sotto i nomi
    degli sposi e come separatore."""
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(0.5)
    half = width / 2
    gap = 1.6 * mm
    c.line(cx - half, cy, cx - gap, cy)
    c.line(cx + gap, cy, cx + half, cy)
    d = 1.1 * mm
    p = c.beginPath()
    p.moveTo(cx, cy + d)
    p.lineTo(cx + d, cy)
    p.lineTo(cx, cy - d)
    p.lineTo(cx - d, cy)
    p.close()
    c.setFillColor(color)
    c.drawPath(p, stroke=0, fill=1)
    c.restoreState()


def draw_corner_vine(c, x, y, rotation, scale, color):
    """Piccolo tralcio dorato stilizzato (vettoriale) per decorare gli
    angoli del biglietto, al posto delle rose fotografiche dell'esempio."""
    c.saveState()
    c.translate(x, y)
    c.rotate(rotation)
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(0.7)
    s = scale
    p = c.beginPath()
    p.moveTo(0, 0)
    p.curveTo(s * 0.25, s * 0.05, s * 0.55, s * 0.22, s * 0.85, s * 0.65)
    c.drawPath(p, stroke=1, fill=0)
    leaf_points = [(s * 0.22, s * 0.03), (s * 0.46, s * 0.13), (s * 0.68, s * 0.36)]
    for (lx, ly) in leaf_points:
        c.saveState()
        c.translate(lx, ly)
        c.rotate(40)
        c.ellipse(-0.9 * mm, -0.4 * mm, 0.9 * mm, 0.4 * mm, stroke=0, fill=1)
        c.restoreState()
    c.restoreState()


def draw_leaf(c, x, y, length, width, rotation, color):
    """Foglia lanceolata con nervatura, ruotabile e interamente vettoriale."""
    c.saveState()
    c.translate(x, y)
    c.rotate(rotation)
    c.setFillColor(color)
    p = c.beginPath()
    p.moveTo(0, 0)
    p.curveTo(length * .28, width, length * .78, width * .72, length, 0)
    p.curveTo(length * .72, -width * .68, length * .25, -width, 0, 0)
    p.close()
    c.drawPath(p, stroke=0, fill=1)
    c.setStrokeColor(COLOR_LEAF_LIGHT)
    c.setLineWidth(.25)
    c.line(length * .08, 0, length * .84, 0)
    c.restoreState()


def draw_rose(c, x, y, radius):
    """Rosa avorio stilizzata a più petali, ispirata al riferimento."""
    c.saveState()
    c.setFillColor(COLOR_ROSE_SHADOW)
    c.circle(x, y, radius, stroke=0, fill=1)
    petals = [
        (0, .25, .74), (-.34, .10, .57), (.34, .10, .57),
        (-.23, -.28, .53), (.23, -.28, .53), (0, -.05, .43),
    ]
    for px, py, scale in petals:
        c.setFillColor(COLOR_CREAM)
        c.ellipse(x + (px - scale) * radius, y + (py - scale * .52) * radius,
                  x + (px + scale) * radius, y + (py + scale * .52) * radius,
                  stroke=0, fill=1)
    c.setStrokeColor(COLOR_GOLD_SOFT)
    c.setLineWidth(.35)
    c.circle(x, y, radius * .22, stroke=1, fill=0)
    c.restoreState()


def draw_baby_breath(c, x, y, scale=1.0):
    """Rametto di piccoli fiori avorio usato tra rose e foglie."""
    c.saveState()
    c.setStrokeColor(COLOR_LEAF_LIGHT)
    c.setLineWidth(.35)
    c.line(x, y, x + 8 * mm * scale, y + 12 * mm * scale)
    for dx, dy in ((2, 4), (5, 6), (3, 9), (7, 10), (8, 13)):
        fx, fy = x + dx * mm * scale, y + dy * mm * scale
        c.line(x + 4 * mm * scale, y + 6 * mm * scale, fx, fy)
        c.setFillColor(COLOR_CREAM)
        c.circle(fx, fy, .9 * mm * scale, stroke=0, fill=1)
        c.setFillColor(COLOR_GOLD)
        c.circle(fx, fy, .22 * mm * scale, stroke=0, fill=1)
    c.restoreState()


def draw_floral_border(c, inner_left, inner_bottom, inner_top):
    """Composizione botanica verticale sul bordo sinistro del pannello."""
    c.saveState()
    # Steli e foglie, prima dei fiori per ottenere profondità.
    c.setStrokeColor(COLOR_LEAF_LIGHT)
    c.setLineWidth(1)
    stem_x = inner_left + 7 * mm
    c.bezier(stem_x, inner_bottom + 2 * mm, inner_left + 24 * mm,
             inner_bottom + 40 * mm, inner_left + 4 * mm, inner_top - 18 * mm,
             inner_left + 16 * mm, inner_top)
    leaves = [
        (5, 13, 12, 3.2, 25, COLOR_LEAF), (13, 20, 13, 3.4, 145, COLOR_LEAF_DARK),
        (7, 34, 14, 3.8, 20, COLOR_LEAF_LIGHT), (17, 44, 12, 3.4, 150, COLOR_LEAF),
        (6, 60, 14, 3.8, 15, COLOR_LEAF_DARK), (14, 71, 13, 3.5, 145, COLOR_LEAF_LIGHT),
        (6, 84, 12, 3.2, 20, COLOR_LEAF),
    ]
    for lx, ly, ll, lw, rot, col in leaves:
        draw_leaf(c, inner_left + lx * mm, inner_bottom + ly * mm,
                  ll * mm, lw * mm, rot, col)
    draw_baby_breath(c, inner_left + 5 * mm, inner_bottom + 34 * mm, .75)
    draw_baby_breath(c, inner_left + 4 * mm, inner_top - 24 * mm, .7)
    draw_rose(c, inner_left + 5 * mm, inner_top - 10 * mm, 7.5 * mm)
    draw_rose(c, inner_left + 8 * mm, inner_bottom + 10 * mm, 8 * mm)
    draw_rose(c, inner_left + 17 * mm, inner_bottom + 24 * mm, 6.5 * mm)
    c.restoreState()


def draw_perforation(c, x, y_bottom, y_top, color, notch_radius=2.2 * mm):
    """Linea di strappo tratteggiata tra invito e tagliando, con i due
    'morsi' semicircolari bianchi tipici dei biglietti staccabili."""
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(0.6)
    c.setDash(2, 2)
    c.line(x, y_bottom, x, y_top)
    c.setDash()
    c.restoreState()

    c.saveState()
    c.setFillColor(COLOR_WHITE)
    c.circle(x, y_top, notch_radius, stroke=0, fill=1)
    c.circle(x, y_bottom, notch_radius, stroke=0, fill=1)
    c.restoreState()


def generate_qr(guest_id: str, out_path: str):
    """Genera il QR code contenente SOLO il codice univoco dell'invitato.
    Il nome/tavolo non vengono messi nel QR: la verifica del doppio ingresso
    avviene tramite l'app di check-in collegata a un database condiviso,
    non tramite il contenuto del QR stesso (un QR fotocopiato conterrebbe
    sempre lo stesso dato leggibile da chiunque)."""
    payload = guest_id
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=10,
        border=2,
    )
    qr.add_data(payload)
    qr.make(fit=True)
    img = qr.make_image(fill_color="#12301F", back_color="#FFFFFF")
    img.save(out_path)
    return out_path


def generate_invite(guest_id: str, guest_name: str, table: str, output_pdf: str, table_name: str = None):
    """Genera un singolo biglietto/ticket PDF per un invitato.

    guest_id: codice univoco dell'invitato (es. '0001'), codificato nel QR.
    guest_name / table / table_name: stampati in chiaro sul biglietto per
    leggibilita' umana, ma NON inseriti nel QR.
    table_name: se non specificato (None), viene derivato automaticamente
    dal numero tavolo tramite TABLE_NAMES/table_name_for(); passa una
    stringa esplicita (anche vuota "") per sovrascrivere questo comportamento.
    """
    if table_name is None:
        table_name = table_name_for(table)

    out_dir = os.path.dirname(output_pdf)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    qr_path = output_pdf.replace(".pdf", "_qr.png")
    generate_qr(guest_id, qr_path)

    c = canvas.Canvas(output_pdf, pagesize=(PAGE_W, PAGE_H))

    # ---- Sfondo verde scuro su tutta la pagina ----
    c.setFillColor(COLOR_BG)
    c.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)

    # ---- Cornice dorata esterna ----
    inner_left, inner_bottom = MARGIN, MARGIN
    inner_right, inner_top = PAGE_W - MARGIN, PAGE_H - MARGIN
    c.setStrokeColor(COLOR_GOLD)
    c.setLineWidth(1.1)
    c.roundRect(inner_left, inner_bottom, inner_right - inner_left, inner_top - inner_bottom,
                4 * mm, stroke=1, fill=0)

    # ---- Composizione floreale avorio e verde sul bordo sinistro ----
    draw_floral_border(c, inner_left, inner_bottom, inner_top)

    # ---- Linea di strappo tra invito e tagliando ----
    divider_x = inner_left + (inner_right - inner_left) * DIVIDER_RATIO
    draw_perforation(c, divider_x, inner_bottom, inner_top, COLOR_GOLD)

    # =========================== PANNELLO INVITO (sinistra) ===========================
    left_pad = 6 * mm
    left_x0 = inner_left + 24 * mm
    left_x1 = divider_x - left_pad
    left_cx = (left_x0 + left_x1) / 2
    left_w = left_x1 - left_x0

    y = inner_top - 7 * mm
    draw_heart(c, left_cx, y, 4 * mm, COLOR_CREAM)
    draw_flourish(c, left_cx, y - 3.5 * mm, 25 * mm, COLOR_GOLD)

    y -= 10 * mm
    c.setFillColor(COLOR_GOLD)
    c.setFont("Times-Italic", 8)
    c.drawCentredString(left_cx, y, "Con grande gioia vi invitiamo alle nozze di")

    y -= 11 * mm
    sposi_size = fit_font_size(c, SPOSI, "Times-Italic", 26, 16, left_w)
    c.setFillColor(COLOR_GOLD)
    c.setFont("Times-Italic", sposi_size)
    c.drawCentredString(left_cx, y, SPOSI)

    y -= 6 * mm
    draw_flourish(c, left_cx, y, 30 * mm, COLOR_GOLD)

    y -= 8 * mm
    c.setFillColor(COLOR_CREAM)
    c.setFont("Times-Roman", 14)
    c.drawCentredString(left_cx, y, DATA_EVENTO)

    y -= 6.5 * mm
    c.setFillColor(COLOR_GOLD)
    c.setFont("Helvetica-Bold", 9)
    c.drawCentredString(left_cx, y, tracked(LUOGO, letter_gap=""))

    y -= 8 * mm
    c.setStrokeColor(COLOR_GOLD_SOFT)
    c.setLineWidth(0.4)
    c.setDash(1, 2)
    c.line(left_cx - left_w / 2, y, left_cx + left_w / 2, y)
    c.setDash()

    frase_y = inner_bottom + 9 * mm
    c.setFillColor(COLOR_CREAM)
    c.setFont("Times-Italic", 9)
    c.drawCentredString(left_cx, frase_y, FRASE_FINALE)

    draw_heart(c, left_cx, inner_bottom + 4 * mm, 3.4 * mm, COLOR_CREAM)
    draw_flourish(c, left_cx, inner_bottom + 2.2 * mm, 23 * mm, COLOR_GOLD)

    # =========================== TAGLIANDO QR (destra) ===========================
    right_pad = 5 * mm
    right_x0 = divider_x + right_pad
    right_x1 = inner_right - right_pad
    right_cx = (right_x0 + right_x1) / 2
    right_w = right_x1 - right_x0

    y = inner_top - 8 * mm
    c.setFillColor(COLOR_GOLD)
    c.setFont("Times-Italic", 9)
    c.drawCentredString(right_cx, y, "Invito personale")

    y -= 5 * mm
    c.setFillColor(COLOR_CREAM)
    c.setFont("Helvetica", 6.5)
    c.drawCentredString(right_cx, y, "Mostra questo codice all'ingresso")

    # QR su riquadro bianco arrotondato
    qr_size = 32 * mm
    qr_box_pad = 2.5 * mm
    qr_box_size = qr_size + 2 * qr_box_pad
    qr_box_x = right_cx - qr_box_size / 2
    qr_box_y = y - 6 * mm - qr_box_size
    c.setFillColor(COLOR_WHITE)
    c.setStrokeColor(COLOR_GOLD)
    c.setLineWidth(1)
    c.roundRect(qr_box_x, qr_box_y, qr_box_size, qr_box_size, 2 * mm, stroke=1, fill=1)
    c.drawImage(qr_path, qr_box_x + qr_box_pad, qr_box_y + qr_box_pad,
                width=qr_size, height=qr_size, mask='auto')

    y = qr_box_y - 3 * mm
    c.setFillColor(COLOR_GOLD)
    c.setFont("Helvetica", 6.5)
    c.drawCentredString(right_cx, y, tracked("Invitato", letter_gap=""))

    y -= 3.8 * mm
    name_size = fit_font_size(c, guest_name, "Times-Bold", 11, 7, right_w)
    c.setFillColor(COLOR_CREAM)
    c.setFont("Times-Bold", name_size)
    c.drawCentredString(right_cx, y, guest_name)

    y -= 3 * mm
    c.setStrokeColor(COLOR_GOLD_SOFT)
    c.setLineWidth(0.4)
    c.setDash(1, 2)
    c.line(right_cx - right_w / 2, y, right_cx + right_w / 2, y)
    c.setDash()

    y -= 3 * mm
    c.setFillColor(COLOR_GOLD)
    c.setFont("Helvetica", 6.5)
    c.drawCentredString(right_cx, y, tracked("Tavolo", letter_gap=""))
    y -= 3.8 * mm
    c.setFillColor(COLOR_CREAM)
    c.setFont("Times-Bold", 13)
    c.drawCentredString(right_cx, y, str(table))

    if table_name:
        y -= 3.8 * mm
        c.setFillColor(COLOR_GOLD)
        c.setFont("Helvetica", 6)
        c.drawCentredString(right_cx, y, tracked("Nome tavolo", letter_gap=""))
        y -= 3.5 * mm
        tn_size = fit_font_size(c, table_name.upper(), "Times-Bold", 9, 6, right_w)
        c.setFillColor(COLOR_CREAM)
        c.setFont("Times-Bold", tn_size)
        c.drawCentredString(right_cx, y, table_name.upper())

    c.showPage()
    c.save()

    os.remove(qr_path)
    return output_pdf


def generate_batch(guests, output_dir):
    """
    Genera gli inviti per una lista di invitati e un CSV di import per l'app di check-in Java.

    guests: lista di tuple (nome, tavolo) oppure (nome, tavolo, nome_tavolo).
            Se nome_tavolo non e' indicato (tupla a 2 elementi, o terzo elemento
            vuoto), viene derivato automaticamente dal numero tavolo tramite
            TABLE_NAMES/table_name_for — non serve scriverlo a mano per ognuno
            dei 400 invitati.
    output_dir: cartella dove salvare i PDF e il file import_checkin.csv

    Il file import_checkin.csv prodotto ha il formato "id,nome,tavolo,nomeTavolo"
    con header, identico a quello atteso dall'endpoint POST /api/import dell'app
    Spring Boot (wedding-checkin): basta incollarne il contenuto nella tab
    "Importa invitati".
    """
    os.makedirs(output_dir, exist_ok=True)
    csv_path = os.path.join(output_dir, "import_checkin.csv")

    with open(csv_path, "w", encoding="utf-8") as csv_file:
        csv_file.write("id,nome,tavolo,nomeTavolo\n")
        for i, guest in enumerate(guests, start=1):
            if len(guest) >= 3 and guest[2]:
                nome, tavolo, nome_tavolo = guest[0], guest[1], guest[2]
            else:
                nome, tavolo = guest[0], guest[1]
                nome_tavolo = table_name_for(tavolo)

            guest_id = f"{i:04d}"
            safe_name = nome.lower().replace(" ", "_")
            pdf_path = os.path.join(output_dir, f"invito_{guest_id}_{safe_name}.pdf")
            generate_invite(guest_id, nome, tavolo, pdf_path, table_name=nome_tavolo)
            csv_file.write(f"{guest_id},{nome},{tavolo},{nome_tavolo}\n")

    return csv_path


def generate_from_file(input_path, output_dir):
    """
    Legge la lista invitati reale da un file .xlsx o .csv con le colonne
    nome, tavolo e (opzionale) nome tavolo.

    Il file puo' avere righe di titolo/istruzioni sopra l'intestazione vera
    (come nel template lista_invitati_template.xlsx, dove l'header e' alla
    riga 4): questa funzione cerca automaticamente, tra le prime 15 righe,
    quella che contiene la colonna "nome" e la usa come intestazione.
    Le colonne vengono riconosciute dal nome "nome"/"tavolo"/"nome tavolo"
    (o "nometavolo") se presenti, altrimenti si assume che le prime due
    colonne siano nome e tavolo. La riga di esempio del template (nome che
    contiene "ESEMPIO") viene scartata automaticamente. Se il nome tavolo
    non e' presente/valorizzato nel file, viene derivato dal numero tramite
    TABLE_NAMES.

    Genera tutti i biglietti PDF + import_checkin.csv, pronti da:
    1) consegnare in stampa (i PDF)
    2) incollare in "Importa invitati" nell'app Java wedding-checkin (il CSV)
    """
    import pandas as pd

    def norm(col):
        return str(col).strip().lower().replace(" ", "").replace("_", "")

    is_excel = input_path.lower().endswith((".xlsx", ".xls"))

    # Cerca la riga di intestazione vera (contiene "nome") tra le prime 15 righe,
    # per gestire file con titolo/istruzioni sopra la tabella.
    if is_excel:
        raw = pd.read_excel(input_path, header=None, nrows=15)
    else:
        raw = pd.read_csv(input_path, header=None, nrows=15)

    header_row_idx = 0
    for i in range(len(raw)):
        row_norm = [norm(v) for v in raw.iloc[i].tolist()]
        if "nome" in row_norm:
            header_row_idx = i
            break

    if is_excel:
        df = pd.read_excel(input_path, header=header_row_idx)
    else:
        df = pd.read_csv(input_path, header=header_row_idx)

    cols_norm = [norm(c) for c in df.columns]

    def find_col(*names):
        for n in names:
            if n in cols_norm:
                return df.columns[cols_norm.index(n)]
        return None

    nome_col = find_col("nome") or df.columns[0]
    tavolo_col = find_col("tavolo") or df.columns[1]
    nome_tavolo_col = find_col("nometavolo", "nomedeltavolo")

    guests = []
    for _, row in df.iterrows():
        nome = str(row[nome_col]).strip()
        if nome.lower() == "nan" or nome == "":
            continue
        if "esempio" in nome.lower():
            continue  # salta l'eventuale riga di esempio del template
        tavolo = str(row[tavolo_col]).strip()
        if tavolo.lower() == "nan":
            tavolo = ""
        nome_tavolo = ""
        if nome_tavolo_col is not None:
            nome_tavolo = str(row[nome_tavolo_col]).strip()
            if nome_tavolo.lower() == "nan":
                nome_tavolo = ""
        guests.append((nome, tavolo, nome_tavolo))

    csv_path = generate_batch(guests, output_dir)
    print(f"Generati {len(guests)} inviti in '{output_dir}'.")
    print(f"CSV di import per l'app di check-in: {csv_path}")
    return csv_path


if __name__ == "__main__":
    # Cartella dove salvare i PDF degli inviti e il CSV di import: una
    # sottocartella "inviti" accanto a questo script (dentro wedding-checkin),
    # cosi' funziona automaticamente ovunque tu tenga il progetto, senza
    # percorsi fissi da modificare. Viene creata da sola se non esiste.
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    OUTPUT_DIR = os.path.join(SCRIPT_DIR, "inviti")

    # Invito di prova — il nome tavolo NON e' indicato: viene derivato da solo
    # dal numero (tavolo 8 -> "Complicita'", vedi TABLE_NAMES in cima al file).
    generate_invite(
        guest_id="0001",
        guest_name="Mario Rossi",
        table="8",
        output_pdf=os.path.join(OUTPUT_DIR, "invito_0001_mario_rossi.pdf"),
    )
    print("Invito di prova generato con successo.")

    # Quando avrai la lista reale dei 400 invitati, usa una di queste due opzioni:

    # Opzione A: partendo dal file Excel compilato (es. lista_invitati_template.xlsx,
    # anche questo tenuto nella stessa cartella del progetto)
    # generate_from_file(os.path.join(SCRIPT_DIR, "lista_invitati_template.xlsx"), OUTPUT_DIR)

    # Opzione B: partendo da una lista scritta direttamente in Python
    # guests = [("Mario Rossi", "8"), ("Anna Bianchi", "3"), ...]
    # generate_batch(guests, OUTPUT_DIR)
