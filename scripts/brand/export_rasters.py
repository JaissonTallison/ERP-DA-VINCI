#!/usr/bin/env python3
"""Exporta os PNGs, ícones por plataforma, favicon e peças sociais a partir dos SVGs
da marca, e sincroniza as cópias usadas pelo app web (apps/web).

Fonte da verdade: brand/svg/ e brand/favicon/icon.svg (gerados por build_svgs.py).
Requisitos: Pillow + ImageMagick 7 (`magick`) com suporte a SVG (librsvg).
Uso:        python3 scripts/brand/export_rasters.py
"""

from __future__ import annotations

import io
import shutil
import struct
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "brand"
SVG = BRAND / "svg"
PNG = BRAND / "png"
PLATFORMS = BRAND / "plataformas"
FAVICON = BRAND / "favicon"
WEB = ROOT / "apps" / "web"

NAME = "daVINCI ERP Enterprise"
NAVY = (0, 18, 40, 255)        # #001228
PAPER = (255, 255, 255, 255)
MODES = ["", "-fundo-escuro", "-mono-preto", "-mono-branco"]

ICONS = SVG / "icone-app"
APP_ICON = ICONS / "davinci-icone-app.svg"
MASKABLE = ICONS / "davinci-icone-app-maskable.svg"


def render(svg: Path, width: int, height: int | None = None) -> Image.Image:
    """Rasteriza um SVG via librsvg na largura (ou caixa) pedida, fundo transparente."""
    from xml.etree import ElementTree as ET

    vb_w, vb_h = (float(v) for v in ET.parse(svg).getroot().get("viewBox").split()[2:])
    scale = width / vb_w if height is None else min(width / vb_w, height / vb_h)
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out.png"
        subprocess.run(
            ["magick", "-background", "none", "-density", f"{96 * scale:.4f}", str(svg),
             "-resize", f"{round(vb_w * scale)}x{round(vb_h * scale)}!", f"PNG32:{out}"],
            check=True,
        )
        return Image.open(out).convert("RGBA").copy()


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"  ✓ {path.relative_to(ROOT)}  ({img.width}x{img.height})")


def on_canvas(img: Image.Image, size: tuple[int, int], bg) -> Image.Image:
    canvas = Image.new("RGBA", size, bg)
    canvas.alpha_composite(img, ((size[0] - img.width) // 2, (size[1] - img.height) // 2))
    return canvas


def write_ico(path: Path, layers: list[Image.Image]) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        files = []
        for img in layers:
            p = Path(tmp) / f"{img.width}.png"
            img.save(p)
            files.append(str(p))
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["magick", *files, str(path)], check=True)
    print(f"  ✓ {path.relative_to(ROOT)}  ({', '.join(str(i.width) for i in layers)})")


def write_icns(path: Path, svg: Path) -> None:
    """Gera .icns do macOS: cabeçalho 'icns' + entradas PNG (formato aceito desde o 10.7)."""
    entries = [("icp4", 16), ("icp5", 32), ("icp6", 64), ("ic07", 128), ("ic08", 256),
               ("ic09", 512), ("ic10", 1024), ("ic11", 32), ("ic12", 64), ("ic13", 256), ("ic14", 512)]
    cache: dict[int, bytes] = {}
    chunks = b""
    for kind, size in entries:
        if size not in cache:
            buf = io.BytesIO()
            render(svg, size).save(buf, format="PNG", optimize=True)
            cache[size] = buf.getvalue()
        chunks += kind.encode("ascii") + struct.pack(">I", len(cache[size]) + 8) + cache[size]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)
    print(f"  ✓ {path.relative_to(ROOT)}  (16–1024, @1x e @2x)")


# --------------------------------------------------------------------------- #
# Versões da logo
# --------------------------------------------------------------------------- #

def export_logos() -> None:
    groups = {
        "logo-horizontal": (512, 1024, 2048),
        "logo-empilhado": (512, 1024, 2048),
        "logotipo": (1024, 2048),
        "simbolo": (64, 128, 256, 512, 1024),
    }
    for group, widths in groups.items():
        for mode in MODES:
            src = SVG / group / f"davinci-{group}{mode}.svg"
            for w in widths:
                save(render(src, w), PNG / group / f"{src.stem}-{w}.png")


def export_app_icons() -> None:
    for size in (1024, 512, 192):
        save(render(APP_ICON, size), PNG / "icone-app" / f"davinci-icone-app-{size}.png")
    for size in (512, 192):
        save(render(MASKABLE, size), PNG / "icone-app" / f"davinci-icone-app-maskable-{size}.png")

    # favicon.ico multi-resolução (16/32/48) a partir do SVG de favicon (traço reforçado)
    write_ico(FAVICON / "favicon.ico", [render(FAVICON / "icon.svg", s) for s in (16, 32, 48)])


# --------------------------------------------------------------------------- #
# Plataformas: web, mobile (iOS/Android), tablet e desktop (Windows/macOS/Linux)
# --------------------------------------------------------------------------- #

def export_platforms() -> None:
    # iOS / iPadOS: quadrado, opaco e sem cantos (o sistema aplica a máscara)
    ios = PLATFORMS / "ios"
    save(render(MASKABLE, 1024).convert("RGB"), ios / "davinci-ios-app-icon-1024.png")
    for size in (180, 167, 152, 120):  # iPhone @3x, iPad Pro, iPad, iPhone @2x
        save(render(MASKABLE, size).convert("RGB"), ios / f"davinci-ios-app-icon-{size}.png")

    # Android: ícone adaptativo (frente + fundo + monocromático), ícone legado e Play Store
    android = PLATFORMS / "android"
    save(render(ICONS / "davinci-icone-android-frente.svg", 432), android / "davinci-android-adaptativo-frente-432.png")
    save(Image.new("RGB", (432, 432), NAVY[:3]), android / "davinci-android-adaptativo-fundo-432.png")
    save(render(ICONS / "davinci-icone-android-monocromatico.svg", 432),
         android / "davinci-android-adaptativo-monocromatico-432.png")
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(render(APP_ICON, size), android / "legado" / f"davinci-android-{density}-{size}.png")
    save(render(MASKABLE, 512).convert("RGB"), android / "davinci-play-store-512.png")

    # Splash (mobile e tablet): 2732x2732 cobre todos os aparelhos; o sistema recorta o centro
    splash = PLATFORMS / "splash"
    stacked = SVG / "logo-empilhado"
    save(on_canvas(render(stacked / "davinci-logo-empilhado.svg", 900), (2732, 2732), PAPER).convert("RGB"),
         splash / "davinci-splash-2732.png")
    save(on_canvas(render(stacked / "davinci-logo-empilhado-fundo-escuro.svg", 900), (2732, 2732), NAVY)
         .convert("RGB"), splash / "davinci-splash-fundo-escuro-2732.png")

    # Desktop
    desktop = PLATFORMS / "desktop"
    small = [render(FAVICON / "icon.svg", s) for s in (16, 24, 32, 48)]
    large = [render(APP_ICON, s) for s in (64, 128, 256)]
    write_ico(desktop / "windows" / "davinci.ico", small + large)
    write_icns(desktop / "macos" / "davinci.icns", ICONS / "davinci-icone-macos.svg")
    save(render(ICONS / "davinci-icone-macos.svg", 1024), desktop / "macos" / "davinci-macos-1024.png")
    for size in (512, 256, 128):
        save(render(APP_ICON, size), desktop / "linux" / f"davinci-{size}.png")
    shutil.copyfile(APP_ICON, desktop / "linux" / "davinci.svg")
    print(f"  ✓ {(desktop / 'linux' / 'davinci.svg').relative_to(ROOT)}")


def export_social() -> None:
    horiz_dark = SVG / "logo-horizontal" / "davinci-logo-horizontal-fundo-escuro.svg"
    symbol_dark = SVG / "simbolo" / "davinci-simbolo-fundo-escuro.svg"
    horiz = SVG / "logo-horizontal" / "davinci-logo-horizontal.svg"

    # Open Graph / Twitter / WhatsApp (1200x630)
    save(on_canvas(render(horiz_dark, 840), (1200, 630), NAVY).convert("RGB"),
         PNG / "social" / "davinci-og-1200x630.png")
    # Capa LinkedIn (1584x396) — logo deslocada à direita (avatar cobre a esquerda)
    cover = Image.new("RGBA", (1584, 396), NAVY)
    logo = render(horiz_dark, 760, 230)
    cover.alpha_composite(logo, (1584 - logo.width - 120, (396 - logo.height) // 2))
    save(cover.convert("RGB"), PNG / "social" / "davinci-capa-linkedin-1584x396.png")
    # Avatar de perfil (recorte circular seguro: símbolo em ~62% do lado)
    save(on_canvas(render(symbol_dark, 250), (400, 400), NAVY).convert("RGB"),
         PNG / "social" / "davinci-avatar-400.png")

    # Assinatura de e-mail: fundo branco opaco (seguro em clientes com modo escuro)
    save(on_canvas(render(horiz, 560), (600, 230), PAPER).convert("RGB"),
         PNG / "email" / "davinci-assinatura-email-600.png")


# --------------------------------------------------------------------------- #
# Folha de apresentação da marca (brand/preview.png)
# --------------------------------------------------------------------------- #

def _font(size: int, bold: bool = False):
    name = "LiberationSans-Bold.ttf" if bold else "LiberationSans-Regular.ttf"
    for base in (Path("/usr/share/fonts/liberation-sans-fonts"), Path("/usr/share/fonts/liberation")):
        if (base / name).exists():
            return ImageFont.truetype(str(base / name), size)
    return ImageFont.load_default(size)


def build_preview() -> None:
    W, cell_h, pad = 1600, 300, 40
    col_w = (W - pad * 3) // 2
    rows = [
        ("Logo horizontal — versão principal", "logo-horizontal", 600, 220),
        ("Logo empilhado", "logo-empilhado", 400, 230),
        ("Logotipo", "logotipo", 560, 200),
        ("Símbolo", "simbolo", 220, 220),
    ]
    H = 150 + len(rows) * (60 + 2 * (cell_h + 10)) + 330
    sheet = Image.new("RGBA", (W, H), (246, 246, 244, 255))
    d = ImageDraw.Draw(sheet)
    d.text((pad, 40), f"{NAME} — Identidade visual", font=_font(44, True), fill=NAVY)
    d.text((pad, 98), "Versões oficiais da marca  ·  gerado por scripts/brand", font=_font(22), fill=(90, 96, 110))

    y = 150
    for title, group, w, h in rows:
        d.text((pad, y), title, font=_font(24, True), fill=NAVY)
        y += 40
        for row_modes in (("", "-fundo-escuro"), ("-mono-preto", "-mono-branco")):
            for i, mode in enumerate(row_modes):
                x = pad + i * (col_w + pad)
                bg = NAVY if mode in ("-fundo-escuro", "-mono-branco") else PAPER
                art = render(SVG / group / f"davinci-{group}{mode}.svg", w, h)
                sheet.alpha_composite(on_canvas(art, (col_w, cell_h - 40), bg), (x, y))
                label = mode.strip("-").replace("-", " ") or "cor"
                d.text((x, y + cell_h - 34), label, font=_font(18), fill=(90, 96, 110))
            y += cell_h + 10
        y += 10

    d.text((pad, y), "Ícones: app · maskable/iOS · macOS · Android adaptativo · favicon", font=_font(24, True), fill=NAVY)
    y += 44
    x = pad
    for name in ("davinci-icone-app.svg", "davinci-icone-app-maskable.svg", "davinci-icone-macos.svg"):
        sheet.alpha_composite(render(ICONS / name, 220), (x, y))
        x += 260
    android = on_canvas(render(ICONS / "davinci-icone-android-frente.svg", 220), (220, 220), NAVY)
    mask = Image.new("L", (220, 220), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 219, 219), fill=255)
    android.putalpha(mask)
    sheet.alpha_composite(android, (x, y))
    x += 260
    for size in (64, 32, 16):
        sheet.alpha_composite(render(FAVICON / "icon.svg", size), (x, y + 220 - size))
        x += size + 30
    save(sheet.convert("RGB"), BRAND / "preview.png")


# --------------------------------------------------------------------------- #
# Sincroniza com o app web (Next.js — convenções de metadata em app/)
# --------------------------------------------------------------------------- #

def sync_web() -> None:
    if not WEB.exists():
        print("  · apps/web não existe: sincronização pulada")
        return
    app = WEB / "src" / "app"
    public = WEB / "public" / "brand"
    if public.exists():
        shutil.rmtree(public)
    copies = {
        # Convenções de arquivo do App Router (geram as <link>/<meta> automaticamente)
        FAVICON / "favicon.ico": app / "favicon.ico",
        FAVICON / "icon.svg": app / "icon.svg",
        PLATFORMS / "ios" / "davinci-ios-app-icon-180.png": app / "apple-icon.png",
        PNG / "social" / "davinci-og-1200x630.png": app / "opengraph-image.png",
        # Arquivos estáticos usados pela UI e pelo manifest (app/manifest.ts)
        SVG / "logo-horizontal" / "davinci-logo-horizontal.svg": public / "logo.svg",
        SVG / "logo-horizontal" / "davinci-logo-horizontal-fundo-escuro.svg": public / "logo-fundo-escuro.svg",
        SVG / "logo-empilhado" / "davinci-logo-empilhado.svg": public / "logo-empilhado.svg",
        SVG / "logo-empilhado" / "davinci-logo-empilhado-fundo-escuro.svg": public / "logo-empilhado-fundo-escuro.svg",
        SVG / "simbolo" / "davinci-simbolo.svg": public / "simbolo.svg",
        SVG / "simbolo" / "davinci-simbolo-fundo-escuro.svg": public / "simbolo-fundo-escuro.svg",
        PNG / "icone-app" / "davinci-icone-app-192.png": public / "icon-192.png",
        PNG / "icone-app" / "davinci-icone-app-512.png": public / "icon-512.png",
        PNG / "icone-app" / "davinci-icone-app-maskable-512.png": public / "icon-maskable-512.png",
    }
    for src, dst in copies.items():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        print(f"  → {dst.relative_to(ROOT)}")
    (app / "opengraph-image.alt.txt").write_text(f"{NAME}\n", encoding="utf-8")
    # Next.js recusa favicon duplicado em public/ e app/
    for stale in (WEB / "public" / "favicon.ico", WEB / "public" / "icon.svg"):
        stale.unlink(missing_ok=True)


def main() -> None:
    print(f"Exportando rasters da marca {NAME}…")
    for generated in (PNG, PLATFORMS):
        if generated.exists():
            shutil.rmtree(generated)
    export_logos()
    export_app_icons()
    export_platforms()
    export_social()
    build_preview()
    sync_web()
    print("Concluído.")


if __name__ == "__main__":
    main()
