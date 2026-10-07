import os
import tempfile
from svglib.svglib import svg2rlg
from reportlab.graphics import renderPDF
import pypdfium2 as pdfium
from PIL import Image, ImageChops

BASE = r'C:\Users\SERVER\Desktop\Scripts\steam-qr-approver'
src = os.path.join(BASE, 'docs', 'logo.svg')

drawing = svg2rlg(src)
pdf_path = os.path.join(tempfile.gettempdir(), 'asf2fa-logo.pdf')
renderPDF.drawToFile(drawing, pdf_path)

pdf = pdfium.PdfDocument(pdf_path)
page = pdf[0]
page_w = page.get_size()[0]


def to_transparent(rgb):
    """White -> transparent, keeping the ink colour and its anti-aliasing."""
    ink = (255, 0, 0)

    r, g, b = rgb.split()
    min_channel = ImageChops.darker(ImageChops.darker(r, g), b)          # whiteness
    alpha = min_channel.point(lambda v: 255 - v)                        # 0 where white
    ink_channel = Image.new('L', rgb.size, ink[0])
    zero = Image.new('L', rgb.size, 0)

    return Image.merge('RGBA', (ink_channel, zero, zero, alpha)), ink


for size, out in ((512, os.path.join(BASE, 'docs', 'logo.png')),
                  (128, os.path.join(BASE, 'docs', 'logo-128.png'))):
    bitmap = page.render(scale=size / page_w)
    rgb = bitmap.to_pil().convert('RGB')
    img, ink = to_transparent(rgb)
    img.save(out)

    pixels = list(img.getdata())
    opaque = sum(1 for *_rgb, a in pixels if a > 200)
    transparent = sum(1 for *_rgb, a in pixels if a == 0)
    print(out, img.size, 'ink', ink, 'transparent px:', transparent, 'opaque px:', opaque)
