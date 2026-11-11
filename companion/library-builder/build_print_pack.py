#!/usr/bin/env python3
"""Oracle OS — Pacote de impressão / Print pack.

Builds print-ready A4 PDFs (black on white, large type) of every Oracle guide, in priority
order, with a cover, how-to-store page, index and a perpetual calendar; plus copies of the
vital outside manuals. Output: dist/impressao/ (Mac) — phone_install pushes it to the phones.

  python3 build_print_pack.py
"""
from __future__ import annotations

import calendar
import datetime as dt
import json
import re
import shutil
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (KeepTogether, ListFlowable, ListItem, PageBreak, Paragraph,
                                SimpleDocTemplate, Spacer, Table, TableStyle)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GUIDES = HERE / "guias"
OUT = ROOT / "dist" / "impressao"
DL = ROOT / "downloads"

FONT = HERE / "fonts"
pdfmetrics.registerFont(TTFont("DV", str(FONT / "DejaVuSans.ttf")))
pdfmetrics.registerFont(TTFont("DV-B", str(FONT / "DejaVuSans-Bold.ttf")))
pdfmetrics.registerFont(TTFont("DV-I", str(FONT / "DejaVuSans-Oblique.ttf")))
pdfmetrics.registerFont(TTFont("DV-BI", str(FONT / "DejaVuSans-BoldOblique.ttf")))
pdfmetrics.registerFontFamily("DV", normal="DV", bold="DV-B", italic="DV-I", boldItalic="DV-BI")

# (PT stem, EN stem) in the order a family should print them: what keeps people alive first.
ORDER = [
    ("primeiros-socorros", "first-aid-en"),
    ("agua-potavel", "water-en"),
    ("plano-50-anos", "plan-50-years-en"),
    ("fogo", "fire-en"),
    ("apagao-frio-abrigo", "blackout-cold-shelter-en"),
    ("aquecer-cozinhar-sem-luz", "heat-cook-no-power-en"),
    ("conservar-comida", "food-preservation-en"),
    ("higiene-banho", "hygiene-bathing-en"),
    ("guerra-ataque-nuclear", "war-nuclear-en"),
    ("resgate-busca", "rescue-search-en"),
    ("parteira-plantas-medicinais", "midwifery-medicinal-plants-en"),
    ("plantas-silvestres", "wild-plants-en"),
    ("pesca", "fishing-en"),
    ("caca-armadilhas", "hunting-trapping-en"),
    ("sementes-cereais", "seeds-grains-en"),
    ("animais", "animals-en"),
    ("construcao-natural", "natural-building-en"),
    ("energia-sem-rede", "off-grid-energy-en"),
    ("quimica-basica", "basic-chemistry-en"),
    ("ferraria-olaria-carpintaria", "smithing-pottery-carpentry-en"),
    ("texteis-couro", "textiles-leather-en"),
    ("cordas-nos", "ropes-knots-en"),
    ("navegacao", "navigation-en"),
    ("agua-rios-mar", "water-rivers-sea-en"),
    ("comunicacao-sem-energia", "no-power-comm-en"),
    ("radio-comunicacao", "radio-communication-en"),
    ("tempo-calendario", "weather-calendar-en"),
    ("comunidade-educacao", "community-education-en"),
    ("proteger-familia-recursos", "protect-family-supplies-en"),
    ("saude-mental-crise", "mental-health-crisis-en"),
    ("alongamentos-dores", "stretching-pain-en"),
    ("defesa-pessoal", "self-defence-en"),
    ("yoga", "yoga-en"),
    ("meditacao", "meditation-en"),
    ("chakras", "chakras-en"),
    ("diretorio-paises", None),
]

# Outside manuals worth having on paper (copied, numbered by priority; missing ones skipped).
MANUALS = [
    ("01-Where-There-Is-No-Doctor-Hesperian.pdf", ["library/saude/en_wtnd_2024_fm.pdf"] + [f"library/saude/en_wtnd_2024_{i:02d}.pdf" for i in range(1, 24)] + ["library/saude/en_wtnd_2024_gp.pdf"]),
    ("02-Survival-Manual-FM21-76-2006.pdf", ["library-retry/sobrevivencia/US-Army-Survival-Manual-2006.pdf"]),
    ("03-IFRC-First-Aid-Guidelines-2025.pdf", ["library/primeiros-socorros/IFRC-First-Aid-Guidelines-2025.pdf"]),
    ("04-Stop-the-Bleed.pdf", ["library-corpo/primeiros-socorros/Stop-the-Bleed.pdf"]),
    ("05-Manual-ABC-Primeiros-Socorros-PT.pdf", ["library-extra/saude/manual_ABC_dos_Primeiros_Socorros.pdf"]),
    ("06-MSF-Obstetric-Newborn-Care.pdf", ["library-med/saude/MSF-Obstetric-Newborn-Care.pdf"]),
    ("06b-Book-for-Midwives-Hesperian.pdf", ["library-reconstrucao/saude/en_midw_*.pdf"]),
    ("06c-FAO-Seeds-for-Communities.pdf", ["library-reconstrucao/agricultura/FAO-Seeds-a-i3987e.pdf"]),
    ("07-Household-Water-Treatment-IFRC.pdf", ["library/agua-saneamento/ifrc-hwts-manual-english.pdf"]),
    ("08-Slow-Sand-Filtration.pdf", ["library/agua-saneamento/PracticalAction-slow-sand-filtration.pdf"]),
    ("09-SODIS-Solar-Water-Disinfection.pdf", ["library/agua-saneamento/PracticalAction-sodis.pdf"]),
    ("10-Rainwater-Harvesting.pdf", ["library/agua-saneamento/PracticalAction-rainwater-harvesting.pdf"]),
    ("11-MSF-Essential-Drugs.pdf", ["library-med/saude/MSF-Essential-Drugs.pdf"]),
    ("12-MSF-Clinical-Guidelines.pdf", ["library-med/saude/MSF-Clinical-Guidelines.pdf"]),
    ("13-Primeiros-Cuidados-Psicologicos-OMS.pdf", ["library-med/saude-mental/OMS-Primeiros-Cuidados-Psicologicos.pdf"]),
    ("14-Psychological-First-Aid-WHO.pdf", ["library-med/saude-mental/WHO-Psychological-First-Aid.pdf"]),
    ("15-OS-Map-Reading-Made-Easy.pdf", ["library-med/navegacao/OS-Map-Reading-Made-Easy.pdf"]),
    ("16-Zeer-Pot-Cooler.pdf", ["library/agricultura/PracticalAction-zeer-pot.pdf"]),
]

TXT = {
    "pt": dict(
        title="ORACLE OS", sub="Pacote de impressão — guias de sobrevivência e reconstrução",
        lang="Português", made="Gerado a", index="Índice", page="pág.",
        store_title="Como usar e guardar estas folhas",
        store=[
            "Imprima primeiro as secções 1–3 (primeiros socorros, água, plano de 50 anos). Depois o resto, por ordem.",
            "Papel normal de 80 g dura décadas se ficar seco, escuro e longe de ratos. Use pastas de plástico com fecho ou sacos de congelação duplos.",
            "Faça pelo menos duas cópias e guarde-as em sítios diferentes (casa, carro, familiar, abrigo).",
            "Quando a tinta desbotar, recopie à mão os capítulos mais usados. O conhecimento só sobrevive se for copiado e ensinado.",
            "Na mochila de emergência leve só as secções 1, 2, 4, 5 e 22–23 dobradas num saco estanque.",
            "Os manuais completos (Onde Não Há Médico, FM 21-76, etc.) estão na pasta «manuais». Imprima-os em frente e verso.",
        ],
        cal_title="Calendário perpétuo (2000–2100)",
        cal_help="Procure o ano na tabela: o número indica qual dos 14 calendários usar. Os calendários 8–14 são anos bissextos.",
        cal_n="Calendário", months=["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"],
        days="S T Q Q S S D".split(), year="Ano", file="Oracle-Impressao-PT.pdf",
    ),
    "en": dict(
        title="ORACLE OS", sub="Print pack — survival and rebuilding guides",
        lang="English", made="Generated", index="Contents", page="p.",
        store_title="How to use and store these sheets",
        store=[
            "Print sections 1–3 first (first aid, water, the 50-year plan). Then the rest, in order.",
            "Ordinary 80 gsm paper lasts decades if kept dry, dark and away from rodents. Use zip-lock plastic folders or doubled freezer bags.",
            "Make at least two copies and keep them in different places (home, car, relative, shelter).",
            "When the ink fades, hand-copy the most used chapters. Knowledge only survives if it is copied and taught.",
            "In the grab bag carry only sections 1, 2, 4, 5 and 22–23, folded in a waterproof bag.",
            "The full manuals (Where There Is No Doctor, FM 21-76, etc.) are in the 'manuais' folder. Print them double-sided.",
        ],
        cal_title="Perpetual calendar (2000–2100)",
        cal_help="Find the year in the table: the number tells you which of the 14 calendars to use. Calendars 8–14 are leap years.",
        cal_n="Calendar", months=list(calendar.month_name)[1:],
        days="M T W T F S S".split(), year="Year", file="Oracle-Print-EN.pdf",
    ),
}

BASE = ParagraphStyle("b", fontName="DV", fontSize=11, leading=15.5, spaceAfter=4)
H1 = ParagraphStyle("h1", parent=BASE, fontName="DV-B", fontSize=18, leading=23, spaceAfter=10)
H2 = ParagraphStyle("h2", parent=BASE, fontName="DV-B", fontSize=13.5, leading=18, spaceBefore=9, spaceAfter=4)
H3 = ParagraphStyle("h3", parent=BASE, fontName="DV-B", fontSize=12, leading=16, spaceBefore=6, spaceAfter=3)
SMALL = ParagraphStyle("s", parent=BASE, fontSize=8.5, leading=11)
CELL = ParagraphStyle("c", parent=BASE, fontSize=8, leading=10, spaceAfter=0)
COVER = ParagraphStyle("cv", parent=H1, fontSize=40, leading=48, alignment=TA_CENTER)
COVER2 = ParagraphStyle("cv2", parent=BASE, fontSize=15, leading=20, alignment=TA_CENTER)


def inline(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<i>\1</i>", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    return text


def md_flowables(md: str, number: int) -> list:
    lines = md.splitlines()
    out: list = []
    para: list[str] = []
    items: list[tuple[int, str, str]] = []  # (indent, kind, text)
    table: list[str] = []

    def flush_para():
        if para:
            out.append(Paragraph(inline(" ".join(para)), BASE))
            para.clear()

    def flush_list():
        if not items:
            return
        def build(start: int, indent: int):
            flow, i = [], start
            while i < len(items) and items[i][0] >= indent:
                ind, kind, txt = items[i]
                if ind > indent:
                    sub, i = build(i, ind)
                    if flow:
                        flow[-1] = ListItem([flow[-1]._flowables[0], sub] if hasattr(flow[-1], "_flowables") else [sub])
                    continue
                flow.append(ListItem(Paragraph(inline(txt), BASE), leftIndent=14 if kind == "-" else 18,
                                     value=None))
                i += 1
            kind = items[start][1]
            lf = ListFlowable(flow, bulletType="1" if kind == "1" else "bullet", start="1" if kind == "1" else "•",
                              bulletFontName="DV", bulletFontSize=10, leftIndent=16 if kind == "-" else 20)
            return lf, i
        pos = 0
        while pos < len(items):
            lf, pos = build(pos, items[pos][0])
            out.append(lf)
        items.clear()

    def flush_table():
        if not table:
            return
        rows = [[c.strip() for c in r.strip().strip("|").split("|")] for r in table if not re.match(r"^\|?\s*:?-{3,}", r)]
        width = max(len(r) for r in rows)
        data = [[Paragraph(inline(c), CELL) for c in r + [""] * (width - len(r))] for r in rows]
        t = Table(data, repeatRows=1, colWidths=[(A4[0] - 36 * mm) / width] * width)
        t.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.4, colors.black),
                               ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#DDDDDD")),
                               ("VALIGN", (0, 0), (-1, -1), "TOP")]))
        out.append(t)
        out.append(Spacer(1, 6))
        table.clear()

    for raw in lines:
        line = raw.rstrip()
        if line.startswith("|"):
            flush_para(); flush_list(); table.append(line); continue
        flush_table()
        m = re.match(r"^(#{1,4})\s+(.*)", line)
        lm = re.match(r"^(\s*)([-*]|\d+[.)])\s+(.*)", line)
        if m:
            flush_para(); flush_list()
            level = len(m.group(1))
            text = m.group(2)
            if level == 1:
                out.append(Paragraph(f"{number}. {inline(text)}", H1))
            else:
                out.append(Paragraph(inline(text), H2 if level == 2 else H3))
        elif lm:
            flush_para()
            kind = "1" if lm.group(2)[0].isdigit() else "-"
            items.append((len(lm.group(1).replace("\t", "  ")), kind, lm.group(3)))
        elif not line.strip():
            flush_para(); flush_list()
        elif items and raw.startswith("  "):
            items[-1] = (items[-1][0], items[-1][1], items[-1][2] + " " + line.strip())
        else:
            flush_list(); para.append(line.strip())
    flush_para(); flush_list(); flush_table()
    return out


def footer(label: str):
    def draw(canvas, doc):
        canvas.saveState()
        canvas.setFont("DV", 8)
        canvas.drawString(18 * mm, 10 * mm, f"Oracle OS · {label}")
        canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, str(doc.page))
        canvas.restoreState()
    return draw


def year_calendar_number(year: int) -> int:
    start = dt.date(year, 1, 1).weekday()  # 0 = Monday
    return start + 1 + (7 if calendar.isleap(year) else 0)


def calendar_section(t: dict) -> list:
    out = [PageBreak(), Paragraph(t["cal_title"], H1), Paragraph(t["cal_help"], BASE)]
    years = list(range(2000, 2101))
    rows, row = [], []
    for y in years:
        row.append(f"{y}: <b>{year_calendar_number(y)}</b>")
        if len(row) == 8:
            rows.append(row); row = []
    if row:
        rows.append(row + [""] * (8 - len(row)))
    tbl = Table([[Paragraph(c, CELL) for c in r] for r in rows], colWidths=[(A4[0] - 36 * mm) / 8] * 8)
    tbl.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.3, colors.grey)]))
    out.append(tbl)
    for n in range(1, 15):
        leap = n > 7
        first = n - 1 - (7 if leap else 0)
        sample = next(y for y in range(2000, 2200) if calendar.isleap(y) == leap and dt.date(y, 1, 1).weekday() == first)
        months = []
        for m in range(1, 13):
            weeks = calendar.Calendar(0).monthdayscalendar(sample, m)
            data = [[t["months"][m - 1]] + [""] * 6, t["days"]] + [[str(d) if d else "" for d in w] for w in weeks]
            mt = Table(data, colWidths=[6.6 * mm] * 7, rowHeights=[4.2 * mm] * len(data))
            mt.setStyle(TableStyle([("FONT", (0, 0), (-1, -1), "DV", 6.5), ("FONT", (0, 0), (-1, 0), "DV-B", 7),
                                    ("SPAN", (0, 0), (-1, 0)), ("ALIGN", (0, 1), (-1, -1), "RIGHT"),
                                    ("FONT", (0, 1), (-1, 1), "DV-B", 6.5),
                                    ("TOPPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 0.5)]))
            months.append(mt)
        grid = Table([months[i:i + 4] for i in range(0, 12, 4)], colWidths=[46 * mm] * 4)
        grid.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))
        block = [Paragraph(f"{t['cal_n']} {n}" + (" (bissexto / leap)" if leap else ""), H2), grid]
        if n % 2 == 1:
            out.append(PageBreak())
        out.append(KeepTogether(block))
    return out


def build(lang: str) -> tuple[Path, list[dict]]:
    t = TXT[lang]
    sections = []
    for pt, en in ORDER:
        stem = pt if lang == "pt" else en
        if not stem or not (GUIDES / f"{stem}.md").exists():
            continue
        md = (GUIDES / f"{stem}.md").read_text(encoding="utf-8")
        title = md.splitlines()[0].lstrip("# ").replace(" (English)", "").strip()
        sections.append((stem, title, md))
    story = [Spacer(1, 70 * mm), Paragraph(t["title"], COVER), Spacer(1, 8 * mm), Paragraph(t["sub"], COVER2),
             Spacer(1, 6 * mm), Paragraph(f"{t['lang']} · {t['made']} {dt.date.today().isoformat()} · {len(sections)}", COVER2),
             PageBreak(), Paragraph(t["store_title"], H1)]
    story.append(ListFlowable([ListItem(Paragraph(inline(s), BASE)) for s in t["store"]], bulletType="1", leftIndent=20))
    story += [Spacer(1, 6 * mm), Paragraph(t["index"], H1)]
    story += [Paragraph(f"{i}. {inline(title)}", BASE) for i, (_, title, _) in enumerate(sections, 1)]
    story.append(Paragraph(f"{len(sections) + 1}. {t['cal_title']}", BASE))
    listing = []
    for i, (stem, title, md) in enumerate(sections, 1):
        story.append(PageBreak())
        story += md_flowables(md, i)
        listing.append({"n": i, "stem": stem, "title": title})
        # Also a one-guide PDF, for printing a single topic.
        single = OUT / ("guias-pt" if lang == "pt" else "guides-en") / f"{i:02d}-{stem}.pdf"
        single.parent.mkdir(parents=True, exist_ok=True)
        SimpleDocTemplate(str(single), pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm,
                          bottomMargin=16 * mm, title=title, author="Oracle OS").build(
            md_flowables(md, i), onFirstPage=footer(title), onLaterPages=footer(title))
    story += calendar_section(t)
    path = OUT / t["file"]
    SimpleDocTemplate(str(path), pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm,
                      bottomMargin=16 * mm, title=t["sub"], author="Oracle OS").build(
        story, onFirstPage=lambda c, d: None, onLaterPages=footer(t["sub"]))
    return path, listing


def copy_manuals() -> list[dict]:
    from pypdf import PdfWriter
    dest = OUT / "manuais"
    dest.mkdir(parents=True, exist_ok=True)
    done = []
    for name, sources in MANUALS:
        paths = []
        for s in sources:
            paths += sorted(DL.glob(s)) if "*" in s else ([DL / s] if (DL / s).exists() else [])
        if not paths:
            print(f"  (sem) {name}")
            continue
        target = dest / name
        if len(paths) == 1:
            shutil.copyfile(paths[0], target)
        else:
            writer = PdfWriter()
            for p in paths:
                writer.append(str(p))
            with open(target, "wb") as fh:
                writer.write(fh)
        done.append({"file": f"manuais/{name}", "bytes": target.stat().st_size})
        print(f"  manual {name} {target.stat().st_size // 1024} KB")
    return done


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    index = {"generated": dt.datetime.now().isoformat(timespec="seconds"), "files": []}
    for lang in ("pt", "en"):
        path, listing = build(lang)
        print(f"{path.name}: {path.stat().st_size // 1024} KB, {len(listing)} guias")
        index["files"].append({"file": path.name, "lang": lang, "bytes": path.stat().st_size, "guides": listing})
    index["manuals"] = copy_manuals()
    (OUT / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "LEIA-ME.txt").write_text(
        "ORACLE OS — Pacote de impressão / Print pack\n\n"
        "Oracle-Impressao-PT.pdf  todos os guias em português, por ordem de prioridade, + calendário perpétuo\n"
        "Oracle-Print-EN.pdf      all guides in English, priority order, + perpetual calendar\n"
        "guias-pt/ guides-en/     um PDF por guia / one PDF per guide\n"
        "manuais/                 manuais completos essenciais / essential full manuals\n\n"
        "Imprima em A4, frente e verso. Guarde seco, em 2 locais. / Print A4 double-sided. Keep dry, in 2 places.\n",
        encoding="utf-8")


if __name__ == "__main__":
    main()
