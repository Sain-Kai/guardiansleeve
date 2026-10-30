#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw, ImageFont

def generate_svg():
    svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 320" width="100%" height="100%">
  <defs>
    <!-- Background Gradient -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0A0E17"/>
      <stop offset="50%" stop-color="#0F172A"/>
      <stop offset="100%" stop-color="#070A0F"/>
    </linearGradient>

    <!-- Shield & Accent Gradient -->
    <linearGradient id="primaryGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#00F0FF"/>
      <stop offset="50%" stop-color="#38BDF8"/>
      <stop offset="100%" stop-color="#6366F1"/>
    </linearGradient>

    <!-- Pulse Gradient -->
    <linearGradient id="pulseGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#00F0FF"/>
      <stop offset="50%" stop-color="#10B981"/>
      <stop offset="100%" stop-color="#00F0FF"/>
    </linearGradient>

    <!-- Glow Filters -->
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="6" result="blur"/>
      <feComposite in="SourceGraphic" in2="blur" operator="over"/>
    </filter>

    <radialGradient id="centerGlow" cx="25%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#00F0FF" stop-opacity="0.12"/>
      <stop offset="50%" stop-color="#6366F1" stop-opacity="0.04"/>
      <stop offset="100%" stop-color="#0A0E17" stop-opacity="0"/>
    </radialGradient>
  </defs>

  <!-- Background -->
  <rect width="1200" height="320" fill="url(#bgGrad)" rx="16"/>
  <rect width="1200" height="320" fill="url(#centerGlow)" rx="16"/>
  <rect width="1198" height="318" x="1" y="1" fill="none" stroke="#1E293B" stroke-width="1.5" rx="15"/>

  <!-- Left: Shield Emblem -->
  <g transform="translate(130, 48)">
    <!-- Outer Glow Shield -->
    <path d="M 110,8 L 192,42 C 192,125 145,188 110,212 C 75,188 28,125 28,42 Z"
          fill="none" stroke="url(#primaryGrad)" stroke-width="6" filter="url(#glow)" opacity="0.4"/>

    <!-- Main Shield -->
    <path d="M 110,8 L 192,42 C 192,125 145,188 110,212 C 75,188 28,125 28,42 Z"
          fill="#0B132B" fill-opacity="0.85" stroke="url(#primaryGrad)" stroke-width="3.5"/>

    <!-- Inner Shield Inset -->
    <path d="M 110,26 L 176,54 C 176,120 138,170 110,192 C 82,170 44,120 44,54 Z"
          fill="none" stroke="#1E293B" stroke-width="1.8"/>

    <!-- Pulse ECG Line Inside Shield -->
    <path d="M 52,110 L 80,110 L 92,84 L 104,142 L 118,66 L 128,132 L 138,102 L 148,110 L 168,110"
          fill="none" stroke="url(#pulseGrad)" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"
          filter="url(#glow)"/>

    <!-- Radio / BLE Arc -->
    <path d="M 110,-4 C 132,-4 150,5 164,18" fill="none" stroke="#00F0FF" stroke-width="2.5" stroke-linecap="round" opacity="0.8"/>
    <circle cx="110" cy="8" r="4" fill="#00F0FF"/>
  </g>

  <!-- Right: Typography & Badges -->
  <g transform="translate(390, 85)">
    <!-- Brand Title -->
    <text x="0" y="52" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', Helvetica, sans-serif"
          font-size="52" font-weight="900" fill="#FFFFFF" letter-spacing="3.5">
      GUARDIAN <tspan fill="url(#primaryGrad)">SLEEVE</tspan>
    </text>

    <!-- Subtitle / Tagline -->
    <text x="0" y="98" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', Helvetica, sans-serif"
          font-size="16" font-weight="600" fill="#94A3B8" letter-spacing="2">
      INTELLIGENT IOT WEARABLE &amp; MULTI-MODAL DISTRESS ESCALATION SYSTEM
    </text>

    <!-- Feature Pills -->
    <g transform="translate(0, 126)">
      <!-- Pill 1: ESP32 IoT -->
      <rect x="0" y="0" width="145" height="32" rx="16" fill="#131C31" stroke="#25334E" stroke-width="1.2"/>
      <circle cx="16" cy="16" r="4" fill="#00F0FF"/>
      <text x="28" y="21" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="12" font-weight="700" fill="#E2E8F0">ESP32 Sleeve</text>

      <!-- Pill 2: Android Native -->
      <rect x="155" y="0" width="145" height="32" rx="16" fill="#131C31" stroke="#25334E" stroke-width="1.2"/>
      <circle cx="171" cy="16" r="4" fill="#10B981"/>
      <text x="183" y="21" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="12" font-weight="700" fill="#E2E8F0">Android Native</text>

      <!-- Pill 3: Telegram SOS -->
      <rect x="310" y="0" width="160" height="32" rx="16" fill="#131C31" stroke="#25334E" stroke-width="1.2"/>
      <circle cx="326" cy="16" r="4" fill="#38BDF8"/>
      <text x="338" y="21" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="12" font-weight="700" fill="#E2E8F0">Telegram Live SOS</text>

      <!-- Pill 4: Stealth Mode -->
      <rect x="480" y="0" width="145" height="32" rx="16" fill="#131C31" stroke="#25334E" stroke-width="1.2"/>
      <circle cx="496" cy="16" r="4" fill="#A855F7"/>
      <text x="508" y="21" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="12" font-weight="700" fill="#E2E8F0">Stealth Blackout</text>
    </g>
  </g>
</svg>
'''
    with open("assets/banner.svg", "w") as f:
        f.write(svg_content.strip())
    print("assets/banner.svg generated successfully.")

def generate_png():
    width, height = 1200, 320
    img = Image.new("RGBA", (width, height), (10, 14, 23, 255))
    draw = ImageDraw.Draw(img)

    # Smooth Gradient Background
    for y in range(height):
        factor = y / height
        r = int(10 + (15 - 10) * factor)
        g = int(14 + (23 - 14) * factor)
        b = int(23 + (42 - 23) * factor)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))

    # Border
    draw.rounded_rectangle([1, 1, width-2, height-2], radius=16, outline=(30, 41, 59, 255), width=2)

    # Shield drawing
    sx, sy = 160, 50
    shield_pts = [
        (sx + 85, sy + 8),
        (sx + 165, sy + 42),
        (sx + 165, sy + 125),
        (sx + 85, sy + 212),
        (sx + 5, sy + 125),
        (sx + 5, sy + 42)
    ]
    draw.polygon(shield_pts, fill=(11, 19, 43, 230), outline=(0, 240, 255, 255))
    draw.line(shield_pts + [shield_pts[0]], fill=(0, 240, 255, 255), width=4)

    # Inner Shield Outline
    inner_pts = [
        (sx + 85, sy + 26),
        (sx + 148, sy + 54),
        (sx + 148, sy + 120),
        (sx + 85, sy + 190),
        (sx + 22, sy + 120),
        (sx + 22, sy + 54)
    ]
    draw.line(inner_pts + [inner_pts[0]], fill=(30, 41, 59, 255), width=2)

    # ECG Wave
    ecg_pts = [
        (sx + 30, sy + 110),
        (sx + 60, sy + 110),
        (sx + 70, sy + 84),
        (sx + 80, sy + 142),
        (sx + 92, sy + 66),
        (sx + 102, sy + 132),
        (sx + 110, sy + 102),
        (sx + 120, sy + 110),
        (sx + 140, sy + 110)
    ]
    draw.line(ecg_pts, fill=(0, 240, 255, 255), width=3, joint="curve")

    # Typography
    try:
        font_large = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 46)
        font_sub = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
        font_pill = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 11)
    except Exception:
        font_large = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_pill = ImageFont.load_default()

    tx, ty = 410, 75
    draw.text((tx, ty), "GUARDIAN ", fill=(255, 255, 255, 255), font=font_large)
    bbox = draw.textbbox((tx, ty), "GUARDIAN ", font=font_large)
    draw.text((bbox[2], ty), "SLEEVE", fill=(0, 240, 255, 255), font=font_large)

    draw.text((tx, ty + 62), "INTELLIGENT IOT WEARABLE & MULTI-MODAL DISTRESS ESCALATION SYSTEM", fill=(148, 163, 184, 255), font=font_sub)

    # Pills
    py = ty + 105
    pills = [
        ("ESP32 Sleeve", (0, 240, 255)),
        ("Android Native", (16, 185, 129)),
        ("Telegram Live SOS", (56, 189, 248)),
        ("Stealth Blackout", (168, 85, 247))
    ]
    cur_x = tx
    for title, dot_color in pills:
        pill_w = 145
        draw.rounded_rectangle([cur_x, py, cur_x + pill_w, py + 30], radius=15, fill=(19, 28, 49, 255), outline=(37, 51, 78, 255), width=1)
        draw.ellipse([cur_x + 12, py + 11, cur_x + 20, py + 19], fill=dot_color)
        draw.text((cur_x + 26, py + 8), title, fill=(226, 232, 240, 255), font=font_pill)
        cur_x += pill_w + 14

    img.save("assets/banner.png", "PNG")
    print("assets/banner.png regenerated successfully.")

if __name__ == "__main__":
    generate_svg()
    generate_png()
