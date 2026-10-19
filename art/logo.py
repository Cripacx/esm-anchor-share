"""Pixel-art logo for ESM Anchor Share, drawn at GUI resolution and scaled up without smoothing."""
import sys
from PIL import Image, ImageDraw

W = H = 256           # logical GUI pixels
SCALE = 3             # 768 x 768 output, like ESM's logo

# 5x7 glyphs, '#' = pixel
FONT = {
    'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    'C': [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    'D': ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    'E': ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    'F': ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    'G': [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    'H': ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    'L': ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    'M': ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    'N': ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    'O': [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    'P': ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    'R': ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    'S': [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    'T': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    'V': ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    'Y': ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    '[': ["###", "#..", "#..", "#..", "#..", "#..", "###"],
    ']': ["###", "..#", "..#", "..#", "..#", "..#", "###"],
    ' ': ["...", "...", "...", "...", "...", "...", "..."],
}

BLACK = (0, 0, 0, 255)
PANEL = (198, 198, 198, 255)
LIGHT = (255, 255, 255, 255)
SHADOW = (85, 85, 85, 255)
BAR = (139, 139, 139, 255)
TAB_OFF = (160, 160, 160, 255)


def text_width(text, size=1, bold=0):
    return sum((len(FONT[c][0]) + 1) * size + bold for c in text) - size


def text_mask(text, size=1, bold=0):
    mask = Image.new('L', (text_width(text, size, bold), 7 * size), 0)
    d = ImageDraw.Draw(mask)
    x = 0
    for c in text:
        for row, line in enumerate(FONT[c]):
            for col, px in enumerate(line):
                if px == '#':
                    d.rectangle([x + col * size, row * size, x + (col + 1) * size - 1 + bold, (row + 1) * size - 1], fill=255)
        x += (len(FONT[c][0]) + 1) * size + bold
    return mask


def draw_text(img, text, x, y, color, size=1, shadow=None):
    mask = text_mask(text, size)
    if shadow:
        img.paste(Image.new('RGBA', mask.size, shadow), (x + size, y + size), mask)
    img.paste(Image.new('RGBA', mask.size, color), (x, y), mask)


def bevel(d, box, fill, light=LIGHT, dark=SHADOW, outline=BLACK):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=outline)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=fill)
    d.line([x0 + 1, y0 + 1, x1 - 2, y0 + 1], fill=light, width=2)
    d.line([x0 + 1, y0 + 1, x0 + 1, y1 - 2], fill=light, width=2)
    d.line([x0 + 2, y1 - 1, x1 - 1, y1 - 1], fill=dark, width=2)
    d.line([x1 - 1, y0 + 2, x1 - 1, y1 - 1], fill=dark, width=2)


def inset(d, box, fill):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=fill)
    d.line([x0, y0, x1, y0], fill=SHADOW)
    d.line([x0, y0, x0, y1], fill=SHADOW)
    d.line([x0, y1, x1, y1], fill=LIGHT)
    d.line([x1, y0, x1, y1], fill=LIGHT)


def gradient_title(img, text, cx, y, size):
    mask = text_mask(text, size, bold=3)
    w, h = mask.size
    # 2px black outline: dilate the mask
    outline = Image.new('L', (w + 6, h + 6), 0)
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            outline.paste(255, (3 + dx, 3 + dy), mask)
    x = cx - w // 2
    img.paste(Image.new('RGBA', outline.size, BLACK), (x - 3, y - 3), outline)
    # drop shadow one step down-right, like Minecraft text
    img.paste(Image.new('RGBA', mask.size, (0, 60, 50, 255)), (x + size // 2, y + size // 2), mask)
    grad = Image.new('RGBA', mask.size)
    gp = grad.load()
    for gx in range(w):
        for gy in range(h):
            t = (gx / w) * 0.6 + (gy / h) * 0.4
            gp[gx, gy] = (0, int(255 - 25 * t), int(110 + 145 * t), 255)
    img.paste(grad, (x, y), mask)


def icon(img, pixels, x, y, color):
    for px, py in pixels:
        img.putpixel((x + px, y + py), color)


SHARE = ({(x, 0) for x in range(6, 11)} | {(10, y) for y in range(0, 5)} | {(4, 6), (5, 5), (6, 4), (7, 3), (8, 2), (9, 1)}
         | {(x, 3) for x in range(0, 4)} | {(0, y) for y in range(3, 11)} | {(x, 10) for x in range(0, 8)} | {(7, y) for y in range(7, 11)})
PUBLISH = ({(5, 0), (4, 1), (6, 1), (3, 2), (7, 2)} | {(5, y) for y in range(1, 7)}
           | {(x, 8) for x in range(0, 11)} | {(x, 10) for x in range(0, 11)} | {(0, 9), (10, 9), (8, 9)})
PLUS = {(5, y) for y in range(2, 9)} | {(x, 5) for x in range(2, 9)}


def main(out):
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # tabs: GENERAL behind, ANCHORS selected and joined to the panel
    bevel(d, (34, 14, 118, 34), TAB_OFF)
    draw_text(img, "GENERAL", 76 - text_width("GENERAL") // 2, 20, (215, 215, 215, 255), shadow=SHADOW)
    bevel(d, (136, 10, 222, 34), PANEL)
    draw_text(img, "ANCHORS", 179 - text_width("ANCHORS") // 2, 17, (0, 220, 60, 255), shadow=(0, 70, 20, 255))

    # main panel
    bevel(d, (2, 30, 253, 236), PANEL)
    d.rectangle([137, 30, 220, 33], fill=PANEL)  # open the selected tab into the panel

    # anchor title bar with toolbar buttons
    inset(d, (14, 40, 241, 56), BAR)
    icon(img, PLUS, 18, 43, (40, 40, 40, 255))
    draw_text(img, "FARM", 128 - text_width("FARM") // 2, 45, LIGHT, shadow=SHADOW)
    icon(img, PUBLISH, 200, 43, (40, 40, 40, 255))
    icon(img, SHARE, 214, 43, (60, 75, 170, 255))  # hovered, like the real screen
    icon(img, {(x, y) for x in range(2, 9) for y in (2, 5, 8)}, 228, 43, (40, 40, 40, 255))

    # content area: ESM-style sound rows behind the title
    inset(d, (10, 62, 245, 200), (0, 0, 0, 255))
    for i, length in enumerate([150, 95, 180, 120, 60, 200, 130, 85, 170]):
        y = 64 + i * 15
        for x in range(11, 11 + length):
            t = (x - 11) / 220
            img.putpixel((x, y), (0, 0, 0, 255))
            d.line([x, y + 1, x, y + 12], fill=(int(110 - 30 * t), int(25 + 70 * t), 10, 255))
        d.rectangle([11 + length, y + 1, 244, y + 12], fill=(32, 32, 32, 255))
    gradient_title(img, "ANCHOR", 128, 89, 5)
    gradient_title(img, "SHARE", 128, 138, 5)

    # chat line along the bottom
    inset(d, (10, 206, 245, 226), (0, 0, 0, 255))
    x = 15
    for text, color in [("[ESM] ", (170, 170, 170, 255)), ("STEVE ", (255, 255, 85, 255)),
                        ("SHARED ", LIGHT), ("FARM ", (85, 255, 255, 255)), ("[COPY]", (85, 255, 85, 255))]:
        draw_text(img, text.rstrip(), x, 213, color, shadow=(40, 40, 40, 255))
        x += text_width(text.rstrip()) + (5 if text.endswith(' ') else 0)

    img.resize((W * SCALE, H * SCALE), Image.NEAREST).save(out)


if __name__ == '__main__':
    main(sys.argv[1])
