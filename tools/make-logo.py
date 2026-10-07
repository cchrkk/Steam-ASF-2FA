"""Regenerate the README PNGs and the Android vector icons from docs/logo.svg.

The source SVG leaves a lot of empty margin around the glyph, which makes it look tiny when used at
a fixed box. This crops to the glyph's bounding box (with a small margin, kept square) and bakes the
same crop into the Android vectors via a translate group.
"""
import os
import re
import tempfile

from PIL import Image, ImageChops
from reportlab.graphics import renderPDF
from svglib.svglib import svg2rlg
import pypdfium2 as pdfium

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG = os.path.join(BASE, 'docs', 'logo.svg')
VIEWBOX = 1024.0
RENDER_PX = 2048
MARGIN = 0.06  # extra margin around the glyph, as a fraction of its largest side

svg_text = open(SVG, encoding='utf-8').read()
paths = []
for tag in re.findall(r'<path\b[^>]*>', svg_text):
    d = re.search(r'\bd="([^"]+)"', tag)
    fill = re.search(r'\bfill="([^"]+)"', tag)
    if d:
        paths.append((fill.group(1) if fill else '#000000', d.group(1)))
assert paths, 'no <path> found in SVG'

ink_hex = paths[0][0].lstrip('#').upper()
if len(ink_hex) == 3:
    ink_hex = ''.join(c * 2 for c in ink_hex)
ink_argb = '#FF' + ink_hex
ink_rgb = tuple(int(ink_hex[i:i + 2], 16) for i in (0, 2, 4))


def render_rgb(px):
    drawing = svg2rlg(SVG)
    pdf_path = os.path.join(tempfile.gettempdir(), 'asf2fa-logo.pdf')
    renderPDF.drawToFile(drawing, pdf_path)
    page = pdfium.PdfDocument(pdf_path)[0]
    return page.render(scale=px / page.get_size()[0]).to_pil().convert('RGB')


def alpha_from_white(rgb):
    r, g, b = rgb.split()
    return ImageChops.darker(ImageChops.darker(r, g), b).point(lambda v: 255 - v)


def solid(alpha, size=None):
    if size:
        alpha = alpha.resize(size, Image.LANCZOS)
    ink = Image.new('L', alpha.size, ink_rgb[0])
    zero = Image.new('L', alpha.size, 0)
    return Image.merge('RGBA', (ink, zero, zero, alpha))


high = render_rgb(RENDER_PX)
mask = alpha_from_white(high)
x0, y0, x1, y1 = mask.getbbox()

side = max(x1 - x0, y1 - y0) * (1 + 2 * MARGIN)
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
px0, py0 = cx - side / 2, cy - side / 2
px1, py1 = px0 + side, py0 + side

crop = alpha_from_white(high).crop((round(px0), round(py0), round(px1), round(py1)))
for size, out in ((512, 'logo.png'), (128, 'logo-128.png')):
    solid(crop, (size, size)).save(os.path.join(BASE, 'docs', out))

# SVG-space square for the Android vectors
to_svg = VIEWBOX / RENDER_PX
side_svg = side * to_svg
tx_svg = -px0 * to_svg
ty_svg = -py0 * to_svg


def vector(width_dp, height_dp, background):
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'\tandroid:width="{width_dp}dp"',
        f'\tandroid:height="{height_dp}dp"',
        f'\tandroid:viewportWidth="{side_svg:.3f}"',
        f'\tandroid:viewportHeight="{side_svg:.3f}">',
    ]
    if background:
        lines.append(f'\t<path\n\t\tandroid:fillColor="{background}"\n\t\tandroid:pathData="M0,0h{side_svg:.3f}v{side_svg:.3f}h-{side_svg:.3f}z" />')
    lines.append(f'\t<group android:translateX="{tx_svg:.3f}" android:translateY="{ty_svg:.3f}">')
    for _fill, d in paths:
        lines.append(f'\t\t<path\n\t\t\tandroid:fillColor="{ink_argb}"\n\t\t\tandroid:pathData="{d}" />')
    lines.append('\t</group>')
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'


drawable = os.path.join(BASE, 'app-android', 'app', 'src', 'main', 'res', 'drawable')
open(os.path.join(drawable, 'ic_logo.xml'), 'w', encoding='utf-8').write(vector(24, 24, None))
open(os.path.join(drawable, 'ic_launcher.xml'), 'w', encoding='utf-8').write(vector(108, 108, '#0D1014'))

print('glyph bbox px:', (x0, y0, x1, y1), 'square px:', round(side), 'svg side:', round(side_svg, 1))
print('translate svg:', round(tx_svg, 1), round(ty_svg, 1), 'ink:', ink_argb)
