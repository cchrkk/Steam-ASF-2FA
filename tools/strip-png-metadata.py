"""Strip ancillary metadata chunks (eXIf, tEXt, iTXt, zTXt, ...) from PNG files, in place.

Re-encodes losslessly with Pillow, which keeps only IHDR/IDAT/IEND (plus optional palette chunks).
"""
import glob
import os
import struct
import sys

from PIL import Image

KEEP = {'IHDR', 'PLTE', 'IDAT', 'IEND', 'tRNS'}


def chunks(data):
    i, out = 8, []
    while i + 8 <= len(data):
        length = struct.unpack('>I', data[i:i + 4])[0]
        ctype = data[i + 4:i + 8].decode('latin1')
        out.append((ctype, data[i + 8:i + 8 + length]))
        i += 12 + length
        if ctype == 'IEND':
            break
    return out


def main(patterns):
    for pattern in patterns:
        for path in sorted(glob.glob(pattern)):
            raw = open(path, 'rb').read()
            removed = [t for t, _ in chunks(raw) if t not in KEEP]

            for ctype, payload in chunks(raw):
                if ctype == 'eXIf' and payload:
                    print(f'  {os.path.basename(path)} eXIf: {payload[:120]!r}')

            img = Image.open(path)
            img.load()
            img.save(path, 'PNG', optimize=True)

            after = [t for t, _ in chunks(open(path, 'rb').read())]
            print(f'{path}: removed {removed or "nothing"} -> now {after}')


if __name__ == '__main__':
    main(sys.argv[1:] or ['docs/screenshots/*.png'])
