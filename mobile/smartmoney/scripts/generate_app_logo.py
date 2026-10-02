#!/usr/bin/env python3
"""
Generates high-resolution SmartMoney app logos and Android adaptive launcher icons
by compositing logo_left.png and logo_right.png with brand dark #263228 background
and pale mint #DAEBE3 emblem.
"""

import os
from PIL import Image, ImageDraw

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res")
DRAWABLE_DIR = os.path.join(RES_DIR, "drawable")

BRAND_DARK_BG = (0x26, 0x32, 0x28, 0xFF)      # #263228 (Dark Forest)
PALE_MINT_TINT = (0xDA, 0xEB, 0xE3)          # #DAEBE3 (Pale Mint Brand Color)

def main():
    left_path = os.path.join(DRAWABLE_DIR, "logo_left.png")
    right_path = os.path.join(DRAWABLE_DIR, "logo_right.png")

    print(f"Loading {left_path} and {right_path}...")
    left = Image.open(left_path).convert("RGBA")
    right = Image.open(right_path).convert("RGBA")

    # 1. Composite left and right halves
    composite = Image.alpha_composite(left, right)
    bbox = composite.getbbox()
    print(f"Combined bounding box in 1000x1000 canvas: {bbox}")
    cropped_original = composite.crop(bbox)

    # 2. Generate Brand-Tinted (#DAEBE3) version
    alpha_mask = cropped_original.split()[3]
    cropped_mint = Image.new("RGBA", cropped_original.size, (*PALE_MINT_TINT, 0))
    cropped_mint.putalpha(alpha_mask)

    # 3. Master 1024x1024 App Logo (Transparent background, Pale Mint emblem)
    master_1024 = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    scale_1024 = 760.0 / cropped_mint.height
    w_1024 = int(cropped_mint.width * scale_1024)
    h_1024 = 760
    resized_mint_1024 = cropped_mint.resize((w_1024, h_1024), Image.Resampling.LANCZOS)
    x_1024 = (1024 - w_1024) // 2
    y_1024 = (1024 - h_1024) // 2
    master_1024.paste(resized_mint_1024, (x_1024, y_1024), resized_mint_1024)
    
    app_logo_path = os.path.join(DRAWABLE_DIR, "app_logo.png")
    master_1024.save(app_logo_path, "PNG", optimize=True)
    print(f"Saved master high-res logo: {app_logo_path} (1024x1024)")

    # 4. In-App System Logo (512x512 transparent PNG replacing system_logo.jpg)
    system_logo_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    scale_512 = 380.0 / cropped_mint.height
    w_512 = int(cropped_mint.width * scale_512)
    h_512 = 380
    resized_mint_512 = cropped_mint.resize((w_512, h_512), Image.Resampling.LANCZOS)
    x_512 = (512 - w_512) // 2
    y_512 = (512 - h_512) // 2
    system_logo_512.paste(resized_mint_512, (x_512, y_512), resized_mint_512)
    
    system_logo_path = os.path.join(DRAWABLE_DIR, "system_logo.png")
    system_logo_512.save(system_logo_path, "PNG", optimize=True)
    print(f"Saved in-app system logo: {system_logo_path} (512x512)")

    # Also save untinted original gradient composite
    original_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    resized_orig_512 = cropped_original.resize((w_512, h_512), Image.Resampling.LANCZOS)
    original_512.paste(resized_orig_512, (x_512, y_512), resized_orig_512)
    original_512.save(os.path.join(DRAWABLE_DIR, "logo_composite_original.png"), "PNG", optimize=True)

    # 5. Android Adaptive Icon Foreground (432x432 px, emblem safe zone inside 288px circle)
    fg_canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    # Safe zone is center 72dp out of 108dp -> 288 out of 432 px
    # Height of 220px fits comfortably inside safe circular mask
    fg_h = 220
    fg_scale = fg_h / cropped_mint.height
    fg_w = int(cropped_mint.width * fg_scale)
    resized_fg = cropped_mint.resize((fg_w, fg_h), Image.Resampling.LANCZOS)
    fg_x = (432 - fg_w) // 2
    fg_y = (432 - fg_h) // 2
    fg_canvas.paste(resized_fg, (fg_x, fg_y), resized_fg)
    
    fg_path = os.path.join(DRAWABLE_DIR, "ic_launcher_foreground.png")
    fg_canvas.save(fg_path, "PNG", optimize=True)
    print(f"Saved adaptive icon foreground: {fg_path} (432x432)")

    # 6. Legacy Density Mipmap Icons
    # Standard densities: mdpi (48), hdpi (72), xhdpi (96), xxhdpi (144), xxxhdpi (192)
    densities = {
        "mipmap-mdpi": (48, 30),
        "mipmap-hdpi": (72, 45),
        "mipmap-xhdpi": (96, 60),
        "mipmap-xxhdpi": (144, 90),
        "mipmap-xxxhdpi": (192, 120),
    }

    for folder_name, (size, emblem_h) in densities.items():
        folder_path = os.path.join(RES_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)

        emblem_scale = emblem_h / cropped_mint.height
        emblem_w = int(cropped_mint.width * emblem_scale)
        resized_emblem = cropped_mint.resize((emblem_w, emblem_h), Image.Resampling.LANCZOS)
        pos_x = (size - emblem_w) // 2
        pos_y = (size - emblem_h) // 2

        # Square / Rounded Rectangle launcher icon
        square_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_sq = ImageDraw.Draw(square_icon)
        corner_r = int(size * 0.22)
        draw_sq.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=corner_r, fill=BRAND_DARK_BG)
        square_icon.paste(resized_emblem, (pos_x, pos_y), resized_emblem)
        
        sq_out = os.path.join(folder_path, "ic_launcher.png")
        square_icon.save(sq_out, "PNG", optimize=True)

        # Round launcher icon
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_rd = ImageDraw.Draw(round_icon)
        draw_rd.ellipse([(0, 0), (size - 1, size - 1)], fill=BRAND_DARK_BG)
        round_icon.paste(resized_emblem, (pos_x, pos_y), resized_emblem)
        
        rd_out = os.path.join(folder_path, "ic_launcher_round.png")
        round_icon.save(rd_out, "PNG", optimize=True)

        print(f"Generated {folder_name} (size: {size}x{size}): ic_launcher.png, ic_launcher_round.png")

    print("\nAll logo and launcher icon assets successfully generated!")

if __name__ == "__main__":
    main()
