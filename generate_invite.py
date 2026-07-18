#!/usr/bin/env python3
"""
Generatore di inviti di matrimonio con QR code personalizzato per invitato.
Progettato per essere riutilizzato su una lista di 400 invitati.
"""

import qrcode
from reportlab.lib.pagesizes import portrait
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import os

# ---------- CONFIGURAZIONE MATRIMONIO (fissa per tutti gli inviti) ----------
SPOSI = "Tamara & Nicolas"
DATA_EVENTO = "20 Agosto 2026"
LUOGO = "Milano"

# ---------- DIMENSIONI BIGLIETTO (formato invito classico 100x150mm) ----------
PAGE_W, PAGE_H = 100 * mm, 150 * mm

# ---------- COLORI (tema elegante oro/bianco) ----------
COLOR_GOLD = HexColor("#B08D57")
COLOR_DARK = HexColor("#2E2A26")
COLOR_CREAM = HexColor("#FDFBF7")


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
    img = qr.make_image(fill_color="#2E2A26", back_color="#FDFBF7")
    img.save(out_path)
    return out_path


def generate_invite(guest_id: str, guest_name: str, table: str, output_pdf: str):
    """Genera un singolo biglietto di invito PDF per un invitato.

    guest_id: codice univoco dell'invitato (es. '0001'), codificato nel QR.
    guest_name / table: stampati in chiaro sul biglietto per leggibilita' umana,
    ma NON inseriti nel QR.
    """
    # Crea la cartella di destinazione se non esiste ancora (es. al primo avvio
    # su un percorso nuovo come C:/Document/sposa).
    out_dir = os.path.dirname(output_pdf)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    qr_path = output_pdf.replace(".pdf", "_qr.png")
    generate_qr(guest_id, qr_path)

    c = canvas.Canvas(output_pdf, pagesize=(PAGE_W, PAGE_H))

    # Sfondo crema
    c.setFillColor(COLOR_CREAM)
    c.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)

    # Cornice decorativa dorata (doppio bordo)
    margin = 6 * mm
    c.setStrokeColor(COLOR_GOLD)
    c.setLineWidth(1.2)
    c.rect(margin, margin, PAGE_W - 2 * margin, PAGE_H - 2 * margin, fill=0, stroke=1)
    c.setLineWidth(0.4)
    c.rect(margin + 2 * mm, margin + 2 * mm, PAGE_W - 2 * margin - 4 * mm,
           PAGE_H - 2 * margin - 4 * mm, fill=0, stroke=1)

    # Titolo "MATRIMONIO"
    c.setFillColor(COLOR_GOLD)
    c.setFont("Helvetica", 9)
    top_y = PAGE_H - 22 * mm
    c.drawCentredString(PAGE_W / 2, top_y, "I N V I T O   D I   N O Z Z E")

    # Nomi sposi
    c.setFillColor(COLOR_DARK)
    c.setFont("Helvetica-Bold", 22)
    c.drawCentredString(PAGE_W / 2, top_y - 14 * mm, SPOSI)

    # Linea decorativa sotto i nomi
    c.setStrokeColor(COLOR_GOLD)
    c.setLineWidth(0.6)
    line_y = top_y - 19 * mm
    c.line(PAGE_W / 2 - 20 * mm, line_y, PAGE_W / 2 + 20 * mm, line_y)

    # Data e luogo
    c.setFont("Helvetica", 12)
    c.setFillColor(COLOR_DARK)
    c.drawCentredString(PAGE_W / 2, line_y - 10 * mm, DATA_EVENTO)
    c.setFont("Helvetica", 10)
    c.drawCentredString(PAGE_W / 2, line_y - 16 * mm, LUOGO)

    # QR code al centro
    qr_size = 42 * mm
    qr_x = (PAGE_W - qr_size) / 2
    qr_y = 42 * mm
    c.drawImage(qr_path, qr_x, qr_y, width=qr_size, height=qr_size)

    # Nome invitato e tavolo sotto il QR
    c.setFont("Helvetica-Bold", 13)
    c.setFillColor(COLOR_DARK)
    c.drawCentredString(PAGE_W / 2, qr_y - 9 * mm, guest_name)
    c.setFont("Helvetica", 10)
    c.setFillColor(COLOR_GOLD)
    c.drawCentredString(PAGE_W / 2, qr_y - 15 * mm, f"Tavolo {table}")

    # Frase finale
    c.setFont("Helvetica-Oblique", 8)
    c.setFillColor(COLOR_DARK)
    c.drawCentredString(PAGE_W / 2, margin + 8 * mm,
                         "Vi aspettiamo per festeggiare insieme a noi")

    c.showPage()
    c.save()

    os.remove(qr_path)
    return output_pdf


def generate_batch(guests, output_dir):
    """
    Genera gli inviti per una lista di invitati e un CSV di import per l'app di check-in Java.

    guests: lista di tuple (nome, tavolo)
    output_dir: cartella dove salvare i PDF e il file import_checkin.csv

    Il file import_checkin.csv prodotto ha il formato "id,nome,tavolo" con header,
    identico a quello atteso dall'endpoint POST /api/import dell'app Spring Boot
    (wedding-checkin): basta incollarne il contenuto nella tab "Importa invitati".
    """
    os.makedirs(output_dir, exist_ok=True)
    csv_path = os.path.join(output_dir, "import_checkin.csv")

    with open(csv_path, "w", encoding="utf-8") as csv_file:
        csv_file.write("id,nome,tavolo\n")
        for i, (nome, tavolo) in enumerate(guests, start=1):
            guest_id = f"{i:04d}"
            safe_name = nome.lower().replace(" ", "_")
            pdf_path = os.path.join(output_dir, f"invito_{safe_name}.pdf")
            generate_invite(guest_id, nome, tavolo, pdf_path)
            csv_file.write(f"{guest_id},{nome},{tavolo}\n")

    return csv_path


def generate_from_file(input_path, output_dir):
    """
    Legge la lista invitati reale da un file .xlsx o .csv con due colonne:
    nome, tavolo (la prima riga puo' essere un'intestazione qualsiasi, viene
    rilevata automaticamente dal nome delle colonne "nome"/"tavolo" se presenti,
    altrimenti si assume che la prima colonna sia il nome e la seconda il tavolo).

    Genera tutti i biglietti PDF + import_checkin.csv, pronti da:
    1) consegnare in stampa (i PDF)
    2) incollare in "Importa invitati" nell'app Java wedding-checkin (il CSV)
    """
    import pandas as pd

    if input_path.lower().endswith((".xlsx", ".xls")):
        df = pd.read_excel(input_path)
    else:
        df = pd.read_csv(input_path)

    cols_lower = [str(c).strip().lower() for c in df.columns]
    if "nome" in cols_lower and "tavolo" in cols_lower:
        nome_col = df.columns[cols_lower.index("nome")]
        tavolo_col = df.columns[cols_lower.index("tavolo")]
    else:
        nome_col, tavolo_col = df.columns[0], df.columns[1]

    guests = [
        (str(row[nome_col]).strip(), str(row[tavolo_col]).strip())
        for _, row in df.iterrows()
        if str(row[nome_col]).strip().lower() != "nan"
    ]

    csv_path = generate_batch(guests, output_dir)
    print(f"Generati {len(guests)} inviti in '{output_dir}'.")
    print(f"CSV di import per l'app di check-in: {csv_path}")
    return csv_path


if __name__ == "__main__":
    # Cartella sul tuo PC dove salvare i PDF degli inviti e il CSV di import.
    # Cambiala pure se preferisci un altro percorso: verra' creata in automatico
    # se non esiste ancora.
    OUTPUT_DIR = "C:/Document/sposa"

    # Invito di prova
    generate_invite(
        guest_id="0001",
        guest_name="Mario Rossi",
        table="8",
        output_pdf=f"{OUTPUT_DIR}/invito_mario_rossi.pdf",
    )
    print("Invito di prova generato con successo.")

    # Quando avrai la lista reale dei 400 invitati, usa una di queste due opzioni:

    # Opzione A: partendo da un file Excel/CSV con colonne "nome" e "tavolo"
    # generate_from_file(f"{OUTPUT_DIR}/lista_invitati.xlsx", f"{OUTPUT_DIR}/inviti")

    # Opzione B: partendo da una lista scritta direttamente in Python
    # guests = [("Mario Rossi", "8"), ("Anna Bianchi", "3"), ...]
    # generate_batch(guests, f"{OUTPUT_DIR}/inviti")
