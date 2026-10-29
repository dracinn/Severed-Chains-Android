import zlib, struct, math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "mods", "stardustindicator", "sparkle")
SIZE = 48
FRAMES = 8

def png(path, w, h, pixels):
    # pixels: list of rows, each row list of (r,g,b,a)
    raw = b''.join(b'\x00' + b''.join(struct.pack('4B', *px) for px in row) for row in pixels)
    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    hdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', hdr))
        f.write(chunk(b'IDAT', zlib.compress(raw, 9)))
        f.write(chunk(b'IEND', b''))

def frame(pulse):
    cx = cy = (SIZE - 1) / 2.0
    sigma = 0.13 + 0.05 * pulse          # core bloom size
    ray_half = 0.10 + 0.06 * pulse       # ray width
    ray_len = 0.42 + 0.18 * pulse        # ray reach
    diag_scale = 0.35                    # short diagonal glints
    px = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            x = (i - cx) / (SIZE / 2.0)
            y = (j - cy) / (SIZE / 2.0)
            r = math.hypot(x, y)
            # central bloom
            glow = math.exp(-(r*r) / (2 * sigma * sigma))
            # 4 cardinal rays: bright where one axis ~0, decay along the other
            ax = abs(x); ay = abs(y)
            cross = max(math.exp(-(ay / ray_half) ** 2) * math.exp(-(ax / ray_len) ** 2),
                        math.exp(-(ax / ray_half) ** 2) * math.exp(-(ay / ray_len) ** 2))
            # faint diagonal glints
            d1 = (x + y) / math.sqrt(2); d2 = (x - y) / math.sqrt(2)
            diag = max(math.exp(-(d2 / (ray_half * 0.7)) ** 2) * math.exp(-(d1 / (ray_len * 0.6)) ** 2),
                       math.exp(-(d1 / (ray_half * 0.7)) ** 2) * math.exp(-(d2 / (ray_len * 0.6)) ** 2))
            v = glow * 1.15 + cross * 0.9 + diag * diag_scale * 0.8
            a = min(1.0, v)
            # warm gold: white-hot core fading to amber edge
            cr = min(1.0, 0.75 + 0.4 * a)
            cg = min(1.0, 0.55 + 0.4 * a)
            cb = min(1.0, 0.15 + 0.5 * a)
            row.append((int(cr * 255), int(cg * 255), int(cb * 255), int(a * 255)))
        px.append(row)
    return px

os.makedirs(OUT, exist_ok=True)
for n in range(FRAMES):
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * n / FRAMES)
    png(os.path.join(OUT, f'{n}.png'), SIZE, SIZE, frame(pulse))
    print(f'wrote {n}.png')
