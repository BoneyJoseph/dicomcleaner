"""Encode the existing app artwork as Explorer-compatible ICO resources.
Pillow is a build-machine dependency only. Small sizes are 32-bit DIBs;
the 256-pixel image is PNG. This changes encoding, not the icon design.
"""
from pathlib import Path
from io import BytesIO
import struct
from PIL import Image

def encode(source, destination):
    image = Image.open(source).convert('RGBA')
    sizes = (16, 24, 32, 48, 64, 128, 256)
    entries, pixels = [], []
    offset = 6 + 16 * len(sizes)
    for size in sizes:
        resized = image.resize((size, size), Image.Resampling.LANCZOS)
        if size == 256:
            buffer = BytesIO(); resized.save(buffer, format='PNG'); data = buffer.getvalue()
        else:
            bottom_up = resized.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            xor = bottom_up.tobytes('raw', 'BGRA')
            stride = ((size + 31) // 32) * 4
            mask = bytearray(stride * size)
            for y in range(size):
                for x in range(size):
                    if bottom_up.getpixel((x, y))[3] == 0:
                        mask[y * stride + x // 8] |= 0x80 >> (x % 8)
            data = struct.pack('<IiiHHIIiiII',40,size,size*2,1,32,0,len(xor)+len(mask),0,0,0,0)+xor+mask
        entries.append(struct.pack('<BBBBHHII',size%256,size%256,0,0,1,32,len(data),offset))
        pixels.append(data); offset += len(data)
    destination.write_bytes(struct.pack('<HHH',0,1,len(sizes))+b''.join(entries)+b''.join(pixels))
    # Independent decoder must recognize every encoded size.
    with Image.open(destination) as check:
        assert check.ico.sizes() == {(n,n) for n in sizes}
        for size in sizes:
            decoded = check.ico.getimage((size,size)).convert('RGBA')
            expected = image.resize((size,size),Image.Resampling.LANCZOS)
            assert decoded.tobytes() == expected.tobytes()

if __name__ == '__main__':
    root=Path(__file__).resolve().parent
    source=root/'artwork/Approved-icon.png'
    if not source.exists():
        source=root/'artwork/Icon_reference.png'
    encode(source if source.exists() else root/'resources/app-icon.png',root/'resources/app-icon.ico')
