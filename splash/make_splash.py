#!/usr/bin/env python3
"""Build the three PF1007 boot splash files ("HomeDash", grey on black).

Outputs, in the directory given as the first argument:
  bootlogo.bmp       U-Boot logo: 800x1280 24-bit BMP, on the bootloader FAT partition
  initlogo.rle       kernel logo: 800x1280 raw BGRA (the name is historical, it is not RLE),
                     /system/media/initlogo.rle
  bootanimation.zip  Android boot animation, 1280x800, stored (uncompressed) zip,
                     /system/media/bootanimation.zip

The panel is natively portrait, so the portrait images are the landscape picture rotated
90 degrees clockwise, matching the factory images. Sizes match the originals exactly.
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

TEXT = "HomeDash"
FG, BG = (140, 140, 140), (0, 0, 0)


def main(out):
    font_file = subprocess.run(["fc-match", "-f", "%{file}", "Ubuntu Sans"],
                               capture_output=True, text=True, check=True).stdout
    land = Image.new("RGB", (1280, 800), BG)
    ImageDraw.Draw(land).text((640, 400), TEXT, fill=FG,
                              font=ImageFont.truetype(font_file, 72), anchor="mm")
    port = land.rotate(-90, expand=True)
    assert port.size == (800, 1280)

    port.save(os.path.join(out, "bootlogo.bmp"))
    with open(os.path.join(out, "initlogo.rle"), "wb") as f:
        f.write(port.convert("RGBA").tobytes("raw", "BGRA"))

    anim = os.path.join(out, "anim")
    os.makedirs(os.path.join(anim, "part0"), exist_ok=True)
    land.save(os.path.join(anim, "part0", "000.png"))
    with open(os.path.join(anim, "desc.txt"), "w") as f:
        f.write("1280 800 1\np 0 0 part0\n")   # loop the single frame until boot completes
    zip_path = os.path.abspath(os.path.join(out, "bootanimation.zip"))
    if os.path.exists(zip_path):
        os.remove(zip_path)
    subprocess.run(["zip", "-0", "-q", "-r", zip_path, "desc.txt", "part0"], cwd=anim, check=True)

    sizes = {"bootlogo.bmp": 3072054, "initlogo.rle": 4096000}
    for name, size in sizes.items():
        assert os.path.getsize(os.path.join(out, name)) == size, name


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
