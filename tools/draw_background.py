"""Draws the background bridge for RentCheck PGH (run from the repo root: python3 tools/draw_background.py).

Writes three files into src/main/resources/public/:
  bridge.svg                 - still picture (used when motion is paused or reduced)
  bridge-animated.svg        - daytime traffic
  bridge-animated-night.svg  - the same traffic with headlights

The bridge itself sits in x = 0..1200. The road, truss, river and approach piers continue far to the
left and right (x = -1800..3000) so the road reaches both edges of any screen, and the traffic drives
that whole length. No libraries needed: it just writes text.
"""
import math, random

OUT = "src/main/resources/public/"
XL, XR = -1800, 3000                 # how far the road runs past the bridge on each side
DECK, L, R, T1, T2, TOP = 300, 40, 1160, 380, 820, 110
F = 'fill="#8a929c" stroke="none"'

def quad(p0, p1, p2, n=400):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t*t*p2[0], (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t*t*p2[1])
            for t in (i/n for i in range(n+1))]

def bridge():
    out, pts = [], []
    # Main cables (self-anchored: they end at the deck, like the Three Sisters).
    for s in [((L, DECK-4), (290, 292), (T1, TOP)), ((T1, TOP), (600, 430), (T2, TOP)), ((T2, TOP), (910, 292), (R, DECK-4))]:
        out.append(f'<path d="M{s[0][0]},{s[0][1]} Q{s[1][0]},{s[1][1]} {s[2][0]},{s[2][1]}" stroke-width="5"/>')
        pts += quad(*s)
    for x in range(L+20, R, 20):                                   # hangers
        if abs(x-T1) < 12 or abs(x-T2) < 12: continue
        y = min(pts, key=lambda p: abs(p[0]-x))[1]
        if DECK - y > 6: out.append(f'<line x1="{x}" y1="{y:.1f}" x2="{x}" y2="{DECK}" stroke-width="1.5"/>')
    # Road deck + truss, now running the full width of the picture.
    out.append(f'<line x1="{XL}" y1="{DECK}" x2="{XR}" y2="{DECK}" stroke-width="4"/>')
    out.append(f'<line x1="{XL}" y1="{DECK+18}" x2="{XR}" y2="{DECK+18}" stroke-width="3"/>')
    zz = " ".join(f"{x},{DECK if (x//20) % 2 == 0 else DECK+18}" for x in range(XL, XR+1, 20))
    out.append(f'<polyline points="{zz}" stroke-width="1.5"/>')
    for tx in (T1, T2):                                            # towers and their piers
        for dx in (-7, 7):
            out.append(f'<line x1="{tx+dx}" y1="{TOP-6}" x2="{tx+dx}" y2="{DECK+18}" stroke-width="6"/>')
        for y in (TOP+10, TOP+80, DECK-20):
            out.append(f'<line x1="{tx-10}" y1="{y}" x2="{tx+10}" y2="{y}" stroke-width="4"/>')
        out.append(f'<rect x="{tx-18}" y="{DECK+18}" width="36" height="52" stroke-width="3"/>')
    for px in (L+10, R-10):                                        # end piers
        out.append(f'<rect x="{px-14}" y="{DECK+18}" width="28" height="52" stroke-width="3"/>')
    for px in list(range(L+10-240, XL, -240)) + list(range(R-10+240, XR, 240)):   # approach piers
        out.append(f'<rect x="{px-10}" y="{DECK+18}" width="20" height="52" stroke-width="3"/>')
    random.seed(3)                                                 # river, full width
    for _ in range(60):
        x = random.randint(XL, XR); y = random.choice([378, 392, 406, 420]); w = random.randint(30, 80)
        out.append(f'<path d="M{x},{y} q{w/4},-4 {w/2},0 t{w/2},0" stroke-width="1.5"/>')
    return out

def wheels(*xs, r=2.2): return "".join(f'<circle cx="{x}" cy="-1.8" r="{r}" {F}/>' for x in xs)
VEHICLES = {   # all face right, wheels on y=0; (drawing, front x, headlight height)
  "sedan":  (f'<rect x="0" y="-9" width="30" height="6" rx="2.5" {F}/><path d="M7,-9 L10,-14 L20,-14 L24,-9 Z" {F}/>' + wheels(7, 23), 30, -6.5),
  "pickup": (f'<rect x="0" y="-8" width="34" height="5" rx="1.5" {F}/><rect x="19" y="-14" width="10" height="7" rx="1.5" {F}/>'
             f'<rect x="0" y="-10" width="17" height="2" {F}/>' + wheels(7, 27, r=2.4), 34, -6),
  "semi":   (f'<rect x="0" y="-19" width="52" height="14" rx="1" {F}/><rect x="52" y="-8" width="5" height="2" {F}/>'
             f'<path d="M56,-5 L56,-17 L66,-17 L70,-11 L72,-11 L72,-5 Z" {F}/>' + wheels(6, 12, 42, 48, 60, 68, r=2.4), 72, -8),
  "sports": (f'<path d="M0,-3 L2,-7 L10,-8 L15,-11 L22,-11 L28,-6 L31,-5 L31,-2 L0,-2 Z" {F}/>' + wheels(6, 24, r=2), 31, -4),
  "bus":    (f'<rect x="0" y="-18" width="56" height="15" rx="3" {F}/>' + wheels(11, 45, r=2.6), 56, -7),
  "suv":    (f'<path d="M0,-4 L0,-14 Q0,-16 2,-16 L22,-16 L28,-10 L32,-9 L32,-4 Z" {F}/>' + wheels(7, 25, r=2.5), 32, -7),
}
BEAM = 'fill="#ffd966" stroke="none"'
def headlight(x, y):
    return f'<path d="M{x},{y-1} L{x+26},{y-5} L{x+26},{y+5} L{x},{y+1} Z" {BEAM} opacity=".55"/><circle cx="{x-0.5}" cy="{y}" r="1.6" {BEAM}/>'

# Traffic: same speed within a lane and evenly spaced, so vehicles never overlap.
EAST = ["sedan", "semi", "sports", "pickup"] * 3
WEST = ["bus", "suv", "sedan", "pickup"] * 3
SPEED_E, SPEED_W = 63, 73                          # drawing units per second (same feel as before)
START, END = XL - 90, XR + 90
E_SEC, W_SEC = round((END-START)/SPEED_E), round((END-START)/SPEED_W)

def traffic(night):
    css = f'''<style>
  /* Vehicles loop along the whole road. Page CSS swaps to the still bridge.svg for reduced motion or "Pause". */
  .v {{ animation: east {E_SEC}s linear infinite; }}
  .w {{ animation: west {W_SEC}s linear infinite; }}
  @keyframes east {{ from {{ transform: translate({START}px, 299px); }} to {{ transform: translate({END}px, 299px); }} }}
  @keyframes west {{ from {{ transform: translate({END}px, 299px) scale(-.85, .85); }} to {{ transform: translate({START}px, 299px) scale(-.85, .85); }} }}
  @media (prefers-reduced-motion: reduce) {{ .v, .w {{ animation: none; display: none; }} }}
</style>'''
    g = []
    for lane, kinds, secs, off in (("v", EAST, E_SEC, 0), ("w", WEST, W_SEC, 0.5)):
        for i, kind in enumerate(kinds):
            body, fx, fy = VEHICLES[kind]
            lights = headlight(fx, fy) if night else ""
            g.append(f'<g class="{lane} {kind}" style="animation-delay:{-secs*(i+off)/len(kinds):.2f}s">{body}{lights}</g>')
    return [css] + g

def svg(parts, comment):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{XL} 0 {XR-XL} 440" fill="none" stroke="#8a929c" '
            'stroke-linecap="round" stroke-linejoin="round">\n'
            f'<!-- Drawn for RentCheck PGH by tools/draw_background.py: {comment} -->\n' + "\n".join(parts) + "\n</svg>\n")

b = bridge()
open(OUT + "bridge.svg", "w").write(svg(b, "Three Sisters-style bridge, still."))
open(OUT + "bridge-animated.svg", "w").write(svg(b + traffic(False), "bridge with daytime traffic."))
open(OUT + "bridge-animated-night.svg", "w").write(svg(b + traffic(True), "bridge with night traffic (headlights)."))
print("wrote 3 files; east lane", E_SEC, "s, west lane", W_SEC, "s,", len(EAST) + len(WEST), "vehicles")
