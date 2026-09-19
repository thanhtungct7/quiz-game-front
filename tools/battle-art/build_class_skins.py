#!/usr/bin/env python3
"""
Bakes the shop's costumes for the mage and the archer.

Run from anywhere:  python3 tools/battle-art/build_class_skins.py

Same trick the knight's six skins use: rotate the hue of the costume colours and leave everything
else alone, so a skin can never desynchronise an animation. Lightness and saturation are kept, so
every shading step survives and the sprite still reads as the same cloth in a different dye.

What counts as costume is a hue band per class, not a list of colours. The two packs are drawn on
tight palettes where the garment occupies one band and skin, leather, wood and metal sit in
another (10-45 degrees, warm), so a band is both simpler to state and harder to get wrong than
naming nine hex values that a re-export could renumber.

Also cuts each costume's shop icon -- frame 0, cropped to the body -- so what the shop sells is a
picture of the thing the player will actually be wearing.
"""
from PIL import Image
import colorsys, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.abspath(os.path.join(HERE, "..", ".."))
BATTLE = os.path.join(APP, "app", "src", "main", "assets", "battle")
ITEMS = os.path.join(APP, "app", "src", "main", "assets", "items")

# (class, source page, costume hue band, frame box, body fractions from BattleArt)
BASES = {
    "MAGE": dict(
        page="hero_mage.png",
        band=(235, 305),
        frame=(93, 68), columns=8, body_centre=0.3548, body_half=0.1505,
    ),
    "ARCHER": dict(
        page="hero_archer.png",
        band=(80, 140),
        frame=(46, 45), columns=8, body_centre=0.3478, body_half=0.3478,
    ),
}

# (item code, class, file slug, hue rotation in degrees, saturation scale)
#
# The scale exists for one reason: the archer's tunic is drawn desaturated (S about 0.25), and a
# hue on its own turns it a muddy maroon that the character's own leather already occupies. Lifting
# saturation is what makes that costume read as a deliberate red rather than as more brown.
SKINS = (
    ("SKIN_MAGE_INK", "MAGE", "ink", -50, 1.0),
    ("SKIN_MAGE_EMBER", "MAGE", "ember", 105, 1.0),
    ("SKIN_MAGE_JADE", "MAGE", "jade", -110, 1.0),
    ("SKIN_ARCHER_DUSK", "ARCHER", "dusk", 245, 1.9),
    ("SKIN_ARCHER_AZURE", "ARCHER", "azure", 95, 1.25),
    ("SKIN_ARCHER_PLUM", "ARCHER", "plum", 175, 1.25),
)


def rotate_costume(im, band, degrees, sat_scale=1.0):
    """Hue-rotate only the pixels inside [band], keeping lightness."""
    low, high = band
    out = im.copy()
    px = out.load()
    width, height = out.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            degree = h * 360
            if not (low <= degree <= high):
                continue
            h2 = ((degree + degrees) % 360) / 360
            r2, g2, b2 = colorsys.hls_to_rgb(h2, l, min(1.0, s * sat_scale))
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def shop_icon(page, base):
    """Frame 0, cropped to the fighter, for the shop tile."""
    fw, fh = base["frame"]
    frame = page.crop((0, 0, fw, fh))
    left = round((base["body_centre"] - base["body_half"]) * fw)
    width = round(2 * base["body_half"] * fw)
    return frame.crop((left, 0, left + width, fh))


def main():
    made = []
    for code, class_code, slug, degrees, sat_scale in SKINS:
        base = BASES[class_code]
        source = os.path.join(BATTLE, base["page"])
        if not os.path.exists(source):
            sys.exit("missing base page: " + source)
        page = Image.open(source).convert("RGBA")
        skinned = rotate_costume(page, base["band"], degrees, sat_scale)

        page_name = base["page"].replace(".png", f"_{slug}.png")
        skinned.save(os.path.join(BATTLE, page_name))
        shop_icon(skinned, base).save(os.path.join(ITEMS, f"{code}.png"))
        made.append((code, class_code, page_name))

    print("--- paste into BattleArt.skinPageFor ---")
    for code, class_code, page_name in made:
        print(f'        "{code}" -> "battle/{page_name}"      // {class_code}')
    print(f"\n{len(made)} costumes written to {BATTLE} and {ITEMS}")


if __name__ == "__main__":
    main()
