#!/usr/bin/env python3
"""
Bakes the battle screen's artwork into the app.

Run from anywhere:  python3 tools/battle-art/build_battle_art.py

Three sources, three destinations:

  * the hero -- the free "Hero Knight - Pixel Art" sheet already sitting in the sibling
    TurnBasedBattle project, re-cut to only the frames the arena plays and cropped to one
    common box so every frame stays registered to the same feet;
  * the monsters -- CC0 tiles exported from Dungeon Crawl Stone Soup (crawl/tiles), one per
    catalog art_code, trimmed and packed side by side into a single page;
  * the backdrop -- one frame of the free cartoon parallax pack, cropped to a stage.

All three go to assets/ rather than res/: the arena is a GL surface, and anything Compose drew
behind it would be composited over it by the window, not under it. The region tables this prints
are pasted into ui/game/art/BattleArt.kt -- generated numbers, checked in, so the app never
parses a manifest at runtime.
"""
from PIL import Image
import os, sys, json

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.abspath(os.path.join(HERE, "..", ".."))
QUIZ = os.path.abspath(os.path.join(APP, ".."))
TBB = os.path.join(QUIZ, "TurnBasedBattle", "Assets")
SRC_MON = os.path.join(HERE, "source", "monsters")

ASSETS = os.path.join(APP, "app", "src", "main", "assets", "battle")

HERO_SHEET = os.path.join(TBB, "_Project", "Sprites", "Characters", "HeroKnight.png")
HERO_COLS, HERO_FW, HERO_FH = 10, 100, 55

# Frame runs lifted from TurnBasedBattle's own .anim files, so the app plays exactly what the
# Unity project plays. (name, [frame indices], fps)
HERO_ANIMS = [
    ("IDLE",   list(range(0, 8)),   8.0),
    ("ATTACK", list(range(18, 24)), 14.0),
    ("HURT",   list(range(45, 48)), 11.0),
    ("CAST",   list(range(66, 71)), 12.0),
]

SRC_HERO = os.path.join(HERE, "source", "heroes")

# The other two schools, each from a CC0 pack of its own (see ATTRIBUTION.md). Unlike the knight
# these ship one horizontal strip per animation rather than one grid sheet, so they are cut by
# `build_hero_pack` instead.
#
# `shrink` is the one number that matters here. The three packs are drawn at wildly different
# resolutions -- the wizard stands 86px tall, the huntress 36 -- and dropping them on the same
# stage as-is would make the mage tower over the archer. Halving the wizard brings him to 43px,
# between the knight's 40 and the huntress's 36, and lines up the pixel density at the same time,
# so the three read as one set. It is an integer factor on purpose: anything else resamples pixel
# art into mush.
#
# None of the packs animates a spell being *held*, so CAST borrows a second attack where the pack
# has one and repeats the first where it does not.
#   (clip, file, frame count, fps)
HERO_PACKS = (
    dict(
        code="MAGE",
        page="hero_mage.png",
        root=os.path.join(SRC_HERO, "wizard"),
        shrink=2,
        clips=[
            ("IDLE", "Idle.png", 6, 8.0),
            ("ATTACK", "Attack1.png", 8, 14.0),
            ("HURT", "Hit.png", 4, 11.0),
            ("CAST", "Attack2.png", 8, 12.0),
        ],
    ),
    dict(
        code="ARCHER",
        page="hero_archer.png",
        root=os.path.join(SRC_HERO, "huntress"),
        shrink=1,
        clips=[
            ("IDLE", "Idle.png", 10, 8.0),
            ("ATTACK", "Attack.png", 6, 14.0),
            ("HURT", "Get Hit.png", 3, 11.0),
            # Huntress 2 has a single attack, so drawing a bow is what casting looks like too.
            ("CAST", "Attack.png", 6, 12.0),
        ],
    ),
)

# art_code -> (source tile, hue rotation in degrees, height in world units)
# The hue shift is what makes one CC0 blob serve as a green slime; the height is what makes a
# dragon read as bigger than a goblin without needing a second tile.
MONSTERS = [
    ("SLIME",        "jelly.png",         120, 74),
    ("SLIME_KING",   "royal_jelly.png",     0, 92),
    ("GOBLIN",       "goblin.png",          0, 88),
    ("GOBLIN_CHIEF", "hobgoblin.png",       0, 98),
    ("DIRE_WOLF",    "wolf.png",            0, 74),
    ("ALPHA_WOLF",   "hell_hound.png",      0, 84),
    ("GOLEM",        "flesh_golem.png",     0, 100),
    ("STONE_TITAN",  "iron_golem.png",      0, 112),
    ("WRAITH",       "shadow_wraith.png",   0, 92),
    ("LICH",         "lich.png",            0, 96),
    ("DRAKE",        "swamp_dragon.png",    0, 96),
    ("DRAGON",       "golden_dragon.png",   0, 116),
]

BACKDROP = os.path.join(
    TBB, "ThirdParty", "Free 2D Cartoon Parallax Background", "FullBG", "3_Graveyard.png"
)
BACKDROP_OUT = (1440, 900)


def union_bbox(images):
    box = None
    for im in images:
        b = im.getbbox()
        if b is None:
            continue
        box = b if box is None else (
            min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3])
        )
    return box


def build_hero():
    sheet = Image.open(HERO_SHEET).convert("RGBA")
    wanted = [i for _, idxs, _ in HERO_ANIMS for i in idxs]
    frames = {}
    for i in wanted:
        col, row = i % HERO_COLS, i // HERO_COLS
        frames[i] = sheet.crop(
            (col * HERO_FW, row * HERO_FH, (col + 1) * HERO_FW, (row + 1) * HERO_FH)
        )

    # One box for every frame, not one per frame: a per-frame crop would let the knight's feet
    # slide around as the animation changed.
    box = union_bbox(frames.values())
    fw, fh = box[2] - box[0], box[3] - box[1]

    # Where the ground is inside that box, measured from the idle frames only -- an attack frame
    # can leave the floor, and lining the sprite up to that would sink him.
    idle = [frames[i].crop(box) for i in HERO_ANIMS[0][1]]
    idle_box = union_bbox(idle)
    feet = idle_box[3]
    # The union box is wider than the knight, because an attack frame throws a sword across it.
    # The arena needs to know where *he* is inside that box, or every stance would sit off centre.
    body_centre = (idle_box[0] + idle_box[2]) / 2.0 / (box[2] - box[0])
    body_half = (idle_box[2] - idle_box[0]) / 2.0 / (box[2] - box[0])

    cols = 8
    rows = (len(wanted) + cols - 1) // cols
    page = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    order, regions = [], {}
    for n, i in enumerate(wanted):
        page.paste(frames[i].crop(box), ((n % cols) * fw, (n // cols) * fh))
        order.append(i)

    os.makedirs(ASSETS, exist_ok=True)
    page.save(os.path.join(ASSETS, "hero.png"))

    at = 0
    for name, idxs, fps in HERO_ANIMS:
        regions[name] = (at, len(idxs), fps)
        at += len(idxs)
    return dict(
        code="WARRIOR", file="hero.png",
        cols=cols, frame=(fw, fh), feet=feet, anims=regions, page=page.size, count=len(wanted),
        body_h=idle_box[3] - idle_box[1],
        body_centre=round(body_centre, 4), body_half=round(body_half, 4), idle_box=idle_box,
    )


def build_hero_pack(spec):
    """One class page, cut from a pack that ships a strip per animation.

    Same output shape as `build_hero`: an 8-column page of equally sized frames, all registered
    to one box so the character cannot slide around between clips, plus where his feet are inside
    that box and where his body sits within it.
    """
    strips, frames, clips, idle = {}, [], [], []
    for name, filename, count, fps in spec["clips"]:
        if filename not in strips:
            strip = Image.open(os.path.join(spec["root"], filename)).convert("RGBA")
            fw, fh = strip.width // count, strip.height
            if strip.width % count:
                sys.exit(f"{spec['code']}/{filename}: {strip.width}px does not divide into "
                         f"{count} frames")
            cut = [strip.crop((i * fw, 0, (i + 1) * fw, fh)) for i in range(count)]
            n = spec.get("shrink", 1)
            if n > 1:
                cut = [c.resize((c.width // n, c.height // n), Image.NEAREST) for c in cut]
            strips[filename] = cut
        cut = strips[filename]
        clips.append((name, len(frames), len(cut), fps))
        frames.extend(cut)
        if name == "IDLE":
            idle = cut

    box = union_bbox(frames)
    fw, fh = box[2] - box[0], box[3] - box[1]
    idle_box = union_bbox([f.crop(box) for f in idle])
    feet = idle_box[3]
    body_centre = (idle_box[0] + idle_box[2]) / 2.0 / fw
    body_half = (idle_box[2] - idle_box[0]) / 2.0 / fw

    cols = 8
    rows = (len(frames) + cols - 1) // cols
    page = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    for n, frame in enumerate(frames):
        page.paste(frame.crop(box), ((n % cols) * fw, (n // cols) * fh))

    os.makedirs(ASSETS, exist_ok=True)
    page.save(os.path.join(ASSETS, spec["page"]))
    return dict(
        code=spec["code"], file=spec["page"], cols=cols, frame=(fw, fh), feet=feet,
        anims={name: (start, count, fps) for name, start, count, fps in clips},
        page=page.size, count=len(frames), body_h=idle_box[3] - idle_box[1],
        body_centre=round(body_centre, 4), body_half=round(body_half, 4), idle_box=idle_box,
    )


def build_monsters():
    tiles, meta = [], []
    for code, src, hue, height in MONSTERS:
        im = Image.open(os.path.join(SRC_MON, src)).convert("RGBA")
        if hue:
            im = rotate_hue(im, hue)
        b = im.getbbox()
        if b:
            im = im.crop(b)
        # The tiles are 32px squares and end up ten times that on screen. Doubling them with EPX
        # first -- the rule that rounds a corner only when its two neighbours agree -- keeps the
        # pixel look while halving the size of the blocks, so a goblin's shoulder is a curve
        # rather than a staircase.
        im = epx(im)
        tiles.append(im)
        meta.append((code, height))

    pad = 1
    width = sum(im.size[0] + pad for im in tiles) + pad
    height = max(im.size[1] for im in tiles) + 2 * pad
    page = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    regions, x = {}, pad
    for (code, world_h), im in zip(meta, tiles):
        # Bottom-aligned: the arena draws every creature standing on the same line.
        page.paste(im, (x, height - pad - im.size[1]))
        regions[code] = (x, height - pad - im.size[1], im.size[0], im.size[1], world_h)
        x += im.size[0] + pad

    os.makedirs(ASSETS, exist_ok=True)
    page.save(os.path.join(ASSETS, "monsters.png"))
    return dict(page=page.size, regions=regions)


def epx(im):
    """Scale2x/EPX: doubles a sprite by expanding each pixel into four, rounding a corner only
    where the two neighbours on that side match each other and disagree with the opposite pair."""
    im = im.convert("RGBA")
    w, h = im.size
    src = im.load()
    out = Image.new("RGBA", (w * 2, h * 2))
    dst = out.load()

    def at(x, y):
        return src[min(max(x, 0), w - 1), min(max(y, 0), h - 1)]

    for y in range(h):
        for x in range(w):
            p = at(x, y)
            up, right, left, down = at(x, y - 1), at(x + 1, y), at(x - 1, y), at(x, y + 1)
            e0 = up if (left == up and left != down and up != right) else p
            e1 = right if (up == right and up != left and right != down) else p
            e2 = left if (down == left and down != right and left != up) else p
            e3 = down if (right == down and right != up and down != left) else p
            dst[2 * x, 2 * y] = e0
            dst[2 * x + 1, 2 * y] = e1
            dst[2 * x, 2 * y + 1] = e2
            dst[2 * x + 1, 2 * y + 1] = e3
    return out


def rotate_hue(im, degrees):
    import colorsys
    px = im.load()
    w, h = im.size
    shift = (degrees % 360) / 360.0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            r, g, b = colorsys.hls_to_rgb((hh + shift) % 1.0, ll, ss)
            px[x, y] = (int(r * 255), int(g * 255), int(b * 255), a)
    return im


def build_backdrop():
    im = Image.open(BACKDROP).convert("RGB")
    w, h = im.size
    # The stage is roughly square; the pack's frame is 2:1, so take the middle of it rather than
    # squashing the hills.
    target_ratio = BACKDROP_OUT[0] / BACKDROP_OUT[1]
    crop_w = int(h * target_ratio)
    left = (w - crop_w) // 2
    im = im.crop((left, 0, left + crop_w, h)).resize(BACKDROP_OUT, Image.LANCZOS)
    os.makedirs(ASSETS, exist_ok=True)
    im.save(os.path.join(ASSETS, "backdrop.png"), optimize=True)
    return im.size


def main():
    if not os.path.exists(HERO_SHEET):
        sys.exit("missing hero sheet: " + HERO_SHEET)
    heroes = [build_hero()] + [build_hero_pack(spec) for spec in HERO_PACKS]
    mon = build_monsters()
    bg = build_backdrop()

    for h in heroes:
        print(f"{h['file']:<16}", h["page"], "frames", h["count"], "frame", h["frame"],
              "feet", h["feet"], "body", h["body_h"], "idle box", h["idle_box"])
        for name, (start, count, fps) in h["anims"].items():
            print(f"    {name:8s} start={start:2d} count={count} fps={fps}")
    print("monsters.png ", mon["page"])
    for code, r in mon["regions"].items():
        print(f"    {code:14s} x={r[0]:3d} y={r[1]:2d} w={r[2]:2d} h={r[3]:2d} worldH={r[4]}")
    print("backdrop     ", bg)

    print("\n--- paste into BattleArt.kt ---")
    for h in heroes:
        fw, fh = h["frame"]
        clips = ", ".join(
            f"HeroClip.{name} to Clip({start}, {count}, {fps}f)"
            for name, (start, count, fps) in h["anims"].items()
        )
        print(f'"{h["code"]}" to HeroArt(')
        print(f'    page = "battle/{h["file"]}",')
        print(f'    columns = {h["cols"]}, frameWidth = {fw}, frameHeight = {fh},')
        print(f'    feet = {h["feet"]}, bodyHeight = {h["body_h"]},')
        print(f'    bodyCentre = {h["body_centre"]}f, bodyHalfWidth = {h["body_half"]}f,')
        print(f'    clips = mapOf({clips}),')
        print("),")
    for code, r in mon["regions"].items():
        print(f'"{code}" to MonsterArt({r[0]}, {r[1]}, {r[2]}, {r[3]}, {r[4]}f),')


if __name__ == "__main__":
    main()
