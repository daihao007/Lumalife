#!/usr/bin/env python3
"""Export current formal Markdown documents as verified A4 PDFs."""

from __future__ import annotations

import argparse
import html
import re
from datetime import date
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    Image as RLImage,
    KeepTogether,
    PageBreak,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
FONT_CANDIDATES = (
    (Path("C:/Windows/Fonts/msyh.ttc"), Path("C:/Windows/Fonts/msyhbd.ttc")),
    (Path("/System/Library/Fonts/STHeiti Light.ttc"), Path("/System/Library/Fonts/STHeiti Medium.ttc")),
    (Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"), Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc")),
)

DOCUMENTS = (
    (
        "test-report",
        DOCS / "07_测试报告.md",
        DOCS / "07_测试报告.pdf",
        "测试报告",
        "Software Test Report",
        "LUMALIFE-TR-003",
    ),
    (
        "deployment",
        DOCS / "08_部署文档.md",
        DOCS / "08_部署文档.pdf",
        "部署文档",
        "Deployment Guide",
        "LUMALIFE-DEP-003",
    ),
    (
        "user-manual",
        DOCS / "09_用户手册.md",
        DOCS / "09_用户手册.pdf",
        "用户手册",
        "User Manual",
        "LUMALIFE-UM-002",
    ),
    (
        "development-plan",
        DOCS / "10组-软件开发计划书.md",
        DOCS / "10组-软件开发计划书.pdf",
        "软件开发计划书",
        "Software Development Plan",
        "LUMALIFE-SDP-002",
    ),
    (
        "requirements",
        DOCS / "12_软件需求规格说明书.md",
        DOCS / "10组-软件需求规格说明书.pdf",
        "软件需求规格说明书",
        "Software Requirements Specification",
        "LUMALIFE-SRS-002",
    ),
    (
        "high-level-design",
        DOCS / "13_概要设计说明书.md",
        DOCS / "10组-软件概要设计说明书.pdf",
        "软件概要设计说明书",
        "High-Level Design Specification",
        "LUMALIFE-HLD-002",
    ),
    (
        "detailed-design",
        DOCS / "14_详细设计说明书.md",
        DOCS / "10组-软件详细设计说明书.pdf",
        "软件详细设计说明书",
        "Detailed Design Specification",
        "LUMALIFE-DD-001",
    ),
)

NAVY = colors.HexColor("#17324D")
TEAL = colors.HexColor("#087E8B")
PALE = colors.HexColor("#EAF3F5")
LIGHT = colors.HexColor("#F4F7F9")
MID = colors.HexColor("#7D8B99")
INK = colors.HexColor("#1F2933")


def register_fonts() -> None:
    pair = next(((regular, bold) for regular, bold in FONT_CANDIDATES if regular.exists() and bold.exists()), None)
    if pair is None:
        raise FileNotFoundError("A supported Chinese font pair is required (Microsoft YaHei, STHeiti, or Noto Sans CJK)")
    regular_font, bold_font = pair
    pdfmetrics.registerFont(TTFont("LumaSans", str(regular_font), subfontIndex=0))
    pdfmetrics.registerFont(TTFont("LumaSansBold", str(bold_font), subfontIndex=0))
    pdfmetrics.registerFontFamily(
        "LumaSans", normal="LumaSans", bold="LumaSansBold", italic="LumaSans", boldItalic="LumaSansBold"
    )


def make_styles():
    styles = getSampleStyleSheet()
    styles.add(
        ParagraphStyle(
            "BodyCN",
            fontName="LumaSans",
            fontSize=9.2,
            leading=15.2,
            textColor=INK,
            alignment=TA_JUSTIFY,
            spaceAfter=4.2,
            wordWrap="CJK",
        )
    )
    styles.add(
        ParagraphStyle(
            "CaptionCN",
            parent=styles["BodyCN"],
            fontSize=8.2,
            leading=12,
            alignment=TA_CENTER,
            textColor=MID,
            spaceAfter=7,
        )
    )
    styles.add(
        ParagraphStyle(
            "BulletCN",
            parent=styles["BodyCN"],
            leftIndent=14,
            firstLineIndent=-10,
            bulletIndent=2,
            spaceAfter=2.5,
        )
    )
    styles.add(
        ParagraphStyle(
            "QuoteCN",
            parent=styles["BodyCN"],
            leftIndent=12,
            rightIndent=8,
            borderColor=TEAL,
            borderWidth=0,
            borderLeft=2,
            borderPadding=7,
            backColor=LIGHT,
            textColor=colors.HexColor("#405261"),
            spaceBefore=4,
            spaceAfter=8,
        )
    )
    styles.add(
        ParagraphStyle(
            "H1CN",
            fontName="LumaSansBold",
            fontSize=17,
            leading=23,
            textColor=NAVY,
            spaceBefore=15,
            spaceAfter=8,
            keepWithNext=True,
        )
    )
    styles.add(
        ParagraphStyle(
            "H2CN",
            fontName="LumaSansBold",
            fontSize=12.5,
            leading=18,
            textColor=TEAL,
            spaceBefore=11,
            spaceAfter=5,
            keepWithNext=True,
        )
    )
    styles.add(
        ParagraphStyle(
            "H3CN",
            fontName="LumaSansBold",
            fontSize=10.5,
            leading=16,
            textColor=NAVY,
            spaceBefore=8,
            spaceAfter=4,
            keepWithNext=True,
        )
    )
    styles.add(
        ParagraphStyle(
            "TableCN",
            fontName="LumaSans",
            fontSize=7.3,
            leading=10.5,
            textColor=INK,
            wordWrap="CJK",
        )
    )
    styles.add(
        ParagraphStyle(
            "TableHeadCN",
            parent=styles["TableCN"],
            fontName="LumaSansBold",
            textColor=colors.white,
            alignment=TA_CENTER,
        )
    )
    return styles


def inline_markup(value: str) -> str:
    value = html.escape(value.strip())
    code_spans: list[str] = []

    def protect_code(match: re.Match[str]) -> str:
        code_spans.append(match.group(1))
        return f"@@LUMA_CODE_{len(code_spans) - 1}@@"

    value = re.sub(r"`([^`]+)`", protect_code, value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", value)
    value = re.sub(r"\[([^]]+)]\([^)]+\)", r"\1", value)
    for index, code in enumerate(code_spans):
        value = value.replace(
            f"@@LUMA_CODE_{index}@@",
            f'<font name="LumaSans" color="#075E6B">{code}</font>',
        )
    value = value.replace("  ", " ")
    return value


def table_from_rows(rows: list[str], styles, width: float) -> Table:
    parsed = [[cell.strip() for cell in row.strip().strip("|").split("|")] for row in rows]
    if len(parsed) > 1 and all(re.fullmatch(r":?-{3,}:?", cell.replace(" ", "")) for cell in parsed[1]):
        parsed.pop(1)
    columns = max(len(row) for row in parsed)
    for row in parsed:
        row.extend([""] * (columns - len(row)))

    weights = []
    for index in range(columns):
        longest = max(6, min(32, max(len(row[index]) for row in parsed)))
        weights.append(longest)
    total = sum(weights)
    col_widths = [width * weight / total for weight in weights]
    cells = []
    for row_index, row in enumerate(parsed):
        style = styles["TableHeadCN"] if row_index == 0 else styles["TableCN"]
        cells.append([Paragraph(inline_markup(cell), style) for cell in row])

    table = Table(cells, colWidths=col_widths, repeatRows=1, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("BACKGROUND", (0, 1), (-1, -1), colors.white),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, LIGHT]),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD5DC")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    return table


def markdown_story(markdown: str, styles, usable_width: float, source_dir: Path):
    lines = markdown.splitlines()
    story = []
    paragraph: list[str] = []
    in_code = False
    code_lines: list[str] = []
    index = 0

    def flush_paragraph() -> None:
        if paragraph:
            story.append(Paragraph(inline_markup(" ".join(paragraph)), styles["BodyCN"]))
            paragraph.clear()

    while index < len(lines):
        line = lines[index].rstrip()
        stripped = line.strip()

        if stripped.startswith("```"):
            flush_paragraph()
            if in_code:
                story.append(
                    Preformatted(
                        "\n".join(code_lines),
                        ParagraphStyle(
                            "CodeCN",
                            fontName="LumaSans",
                            fontSize=7.1,
                            leading=10,
                            leftIndent=7,
                            rightIndent=7,
                            borderPadding=7,
                            borderColor=colors.HexColor("#D6E0E5"),
                            borderWidth=0.5,
                            backColor=LIGHT,
                            textColor=colors.HexColor("#203541"),
                            spaceBefore=4,
                            spaceAfter=8,
                        ),
                        maxLineLength=100,
                    )
                )
                code_lines = []
                in_code = False
            else:
                in_code = True
            index += 1
            continue

        if in_code:
            code_lines.append(line)
            index += 1
            continue

        image_match = re.fullmatch(r"!\[([^]]*)]\(([^)]+)\)", stripped)
        if image_match:
            flush_paragraph()
            image_path = (source_dir / image_match.group(2)).resolve()
            if not image_path.is_file():
                raise FileNotFoundError(f"Markdown image not found: {image_path}")
            figure = RLImage(str(image_path))
            figure._restrictSize(usable_width, 150 * mm)
            figure.hAlign = "CENTER"
            figure.spaceBefore = 5
            figure.spaceAfter = 3

            caption_index = index + 1
            while caption_index < len(lines) and not lines[caption_index].strip():
                caption_index += 1
            caption = lines[caption_index].strip() if caption_index < len(lines) else ""
            if re.match(r"^图\s*\d", caption):
                story.append(KeepTogether([figure, Paragraph(inline_markup(caption), styles["CaptionCN"])]))
                index = caption_index + 1
            else:
                story.append(figure)
                index += 1
            continue

        if stripped.startswith("|") and stripped.endswith("|"):
            flush_paragraph()
            table_rows = []
            while index < len(lines) and lines[index].strip().startswith("|") and lines[index].strip().endswith("|"):
                table_rows.append(lines[index].strip())
                index += 1
            story.extend([table_from_rows(table_rows, styles, usable_width), Spacer(1, 7)])
            continue

        heading = re.match(r"^(#{1,4})\s+(.+)$", stripped)
        if heading:
            flush_paragraph()
            level = len(heading.group(1))
            title = heading.group(2)
            if level == 1:
                index += 1
                continue
            style = styles["H1CN"] if level == 2 else styles["H2CN"] if level == 3 else styles["H3CN"]
            story.append(Paragraph(inline_markup(title), style))
            index += 1
            continue

        if stripped.startswith(">"):
            flush_paragraph()
            quote = []
            while index < len(lines) and lines[index].strip().startswith(">"):
                quote.append(lines[index].strip()[1:].strip())
                index += 1
            story.append(Paragraph(inline_markup(" ".join(quote)), styles["QuoteCN"]))
            continue

        bullet = re.match(r"^[-*]\s+(.+)$", stripped)
        numbered = re.match(r"^(\d+)\.\s+(.+)$", stripped)
        if bullet or numbered:
            flush_paragraph()
            marker = "•" if bullet else f"{numbered.group(1)}."
            content = bullet.group(1) if bullet else numbered.group(2)
            story.append(Paragraph(f"{marker}&nbsp;&nbsp;{inline_markup(content)}", styles["BulletCN"]))
            index += 1
            continue

        if not stripped:
            flush_paragraph()
            index += 1
            continue

        paragraph.append(stripped)
        index += 1

    flush_paragraph()
    return story


def cover_story(title: str, subtitle: str, document_id: str):
    return [
        Spacer(1, 41 * mm),
        Table([[""]], colWidths=[32 * mm], rowHeights=[3 * mm], style=[("BACKGROUND", (0, 0), (-1, -1), TEAL)]),
        Spacer(1, 15 * mm),
        Paragraph(
            "LumaLife 综合生活助手平台",
            ParagraphStyle("CoverBrand", fontName="LumaSansBold", fontSize=18, leading=24, textColor=TEAL),
        ),
        Spacer(1, 7 * mm),
        Paragraph(
            title,
            ParagraphStyle("CoverTitle", fontName="LumaSansBold", fontSize=28, leading=38, textColor=NAVY),
        ),
        Spacer(1, 4 * mm),
        Paragraph(
            subtitle,
            ParagraphStyle("CoverSub", fontName="LumaSans", fontSize=11, leading=16, textColor=MID),
        ),
        Spacer(1, 35 * mm),
        Table(
            [
                ["文档编号", document_id],
                ["版本", "2.0 · 当前微服务验收版"],
                ["更新日期", date.today().isoformat()],
                ["状态", "最终答辩前验收基线"],
            ],
            colWidths=[30 * mm, 82 * mm],
            style=[
                ("FONTNAME", (0, 0), (-1, -1), "LumaSans"),
                ("FONTSIZE", (0, 0), (-1, -1), 9),
                ("TEXTCOLOR", (0, 0), (0, -1), MID),
                ("TEXTCOLOR", (1, 0), (1, -1), INK),
                ("LINEBELOW", (0, 0), (-1, -1), 0.35, colors.HexColor("#D7E0E5")),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ],
        ),
        PageBreak(),
    ]


def build_document(source: Path, target: Path, title: str, subtitle: str, document_id: str) -> None:
    styles = make_styles()
    page_width, _ = A4
    left_margin = 19 * mm
    right_margin = 17 * mm
    usable_width = page_width - left_margin - right_margin
    document = SimpleDocTemplate(
        str(target),
        pagesize=A4,
        leftMargin=left_margin,
        rightMargin=right_margin,
        topMargin=20 * mm,
        bottomMargin=18 * mm,
        title=f"LumaLife {title}",
        author="LumaLife Team 10",
        subject="Final pre-defense acceptance baseline",
    )
    markdown = source.read_text(encoding="utf-8")
    story = cover_story(title, subtitle, document_id)
    story.extend(markdown_story(markdown, styles, usable_width, source.parent))

    def page_frame(canvas, doc):
        canvas.saveState()
        if doc.page > 1:
            canvas.setStrokeColor(colors.HexColor("#D7E0E5"))
            canvas.setLineWidth(0.45)
            canvas.line(left_margin, A4[1] - 14 * mm, A4[0] - right_margin, A4[1] - 14 * mm)
            canvas.setFont("LumaSans", 7.5)
            canvas.setFillColor(MID)
            canvas.drawString(left_margin, A4[1] - 10.5 * mm, f"LumaLife · {title}")
            canvas.drawRightString(A4[0] - right_margin, 10 * mm, f"{doc.page}")
        canvas.restoreState()

    document.build(story, onFirstPage=page_frame, onLaterPages=page_frame)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=[item[0] for item in DOCUMENTS])
    args = parser.parse_args()
    register_fonts()
    selected = [item for item in DOCUMENTS if args.only is None or item[0] == args.only]
    for _, source, target, title, subtitle, document_id in selected:
        build_document(source, target, title, subtitle, document_id)
        print(f"generated: {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
