"""Shape registry — maps friendly names to built-in stencil masters."""

from __future__ import annotations

import difflib
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class ShapeSpec:
    """Describes how to drop a shape from a built-in stencil."""
    stencil: str       # e.g. "BASIC_U.VSSX"
    master: str        # Universal master name in the stencil
    category: str      # Human-readable category
    aliases: tuple[str, ...] = ()  # Alternative lookup names


# ---------------------------------------------------------------------------
# Registry keyed by lowercase canonical name → ShapeSpec
# ---------------------------------------------------------------------------

_BASIC = "BASIC_U.VSSX"
_FLOW = "BASFLO_U.VSSX"
_ARROWS = "ARROWS_U.VSSX"

SHAPE_REGISTRY: dict[str, ShapeSpec] = {}

def _reg(name: str, stencil: str, master: str, category: str,
         aliases: tuple[str, ...] = ()) -> None:
    spec = ShapeSpec(stencil=stencil, master=master, category=category, aliases=aliases)
    SHAPE_REGISTRY[name.lower()] = spec
    for a in aliases:
        SHAPE_REGISTRY[a.lower()] = spec

# ── Basic Shapes ──────────────────────────────────────────────────────────
_reg("rectangle",          _BASIC, "Rectangle",          "basic", ("rect", "box"))
_reg("square",             _BASIC, "Square",             "basic")
_reg("circle",             _BASIC, "Circle",             "basic")
_reg("ellipse",            _BASIC, "Ellipse",            "basic", ("oval",))
_reg("right triangle",     _BASIC, "Right Triangle",     "basic", ("right-triangle",))
_reg("triangle",           _BASIC, "Triangle",           "basic")
_reg("rotated triangle",   _BASIC, "Rotated Triangle",   "basic")
_reg("pentagon",           _BASIC, "Pentagon",           "basic")
_reg("hexagon",            _BASIC, "Hexagon",            "basic")
_reg("heptagon",           _BASIC, "Heptagon",           "basic")
_reg("octagon",            _BASIC, "Octagon",            "basic")
_reg("decagon",            _BASIC, "Decagon",            "basic")
_reg("diamond",            _BASIC, "Diamond",            "basic", ("rhombus",))
_reg("cross",              _BASIC, "Cross",              "basic", ("plus",))
_reg("chevron",            _BASIC, "Chevron",            "basic")
_reg("can",                _BASIC, "Can",                "basic", ("cylinder",))
_reg("cube",               _BASIC, "Cube",               "basic")
_reg("parallelogram",      _BASIC, "Parallelogram",      "basic")
_reg("trapezoid",          _BASIC, "Trapezoid",          "basic")
_reg("drop",               _BASIC, "Drop",               "basic", ("teardrop",))
_reg("semi circle",        _BASIC, "Semi Circle",        "basic", ("semicircle", "half circle"))
_reg("semi ellipse",       _BASIC, "Semi Ellipse",       "basic", ("semiellipse", "half ellipse"))
_reg("cone",               _BASIC, "Cone",               "basic")
_reg("inverted cone",      _BASIC, "Inverted Cone",      "basic")
_reg("pyramid",            _BASIC, "Pyramid",            "basic")
_reg("pointed oval",       _BASIC, "Pointed Oval",       "basic")
_reg("funnel",             _BASIC, "Funnel",             "basic")
_reg("gear",               _BASIC, "Gear",               "basic", ("cog",))
_reg("4-point star",       _BASIC, "4-Point Star",       "basic", ("star4", "4 point star"))
_reg("5-point star",       _BASIC, "5-Point Star",       "basic", ("star", "star5", "5 point star"))
_reg("6-point star",       _BASIC, "6-Point Star",       "basic", ("star6", "6 point star"))
_reg("7-point star",       _BASIC, "7-Point Star",       "basic", ("star7", "7 point star"))
_reg("16-point star",      _BASIC, "16-Point Star",      "basic", ("star16",))
_reg("24-point star",      _BASIC, "24-Point Star",      "basic", ("star24",))
_reg("32-point star",      _BASIC, "32-Point Star",      "basic", ("star32",))
_reg("rounded rectangle",  _BASIC, "Rounded Rectangle",  "basic", ("rounded rect", "round rect"))
_reg("snip corner rectangle",           _BASIC, "Snip Corner Rectangle",           "basic")
_reg("round corner rectangle",          _BASIC, "Round Corner Rectangle",          "basic")
_reg("single snip corner rectangle",    _BASIC, "Single Snip Corner Rectangle",    "basic")
_reg("single round corner rectangle",   _BASIC, "Single Round Corner Rectangle",   "basic")
_reg("snip same side corner rectangle", _BASIC, "Snip Same Side Corner Rectangle", "basic")
_reg("snip diagonal corner rectangle",  _BASIC, "Snip Diagonal Corner Rectangle",  "basic")
_reg("round same side corner rectangle", _BASIC, "Round Same Side Corner Rectangle", "basic")
_reg("round diagonal corner rectangle",  _BASIC, "Round Diagonal Corner Rectangle",  "basic")
_reg("snip and round single corner rectangle", _BASIC, "Snip and Round Single Corner Rectangle", "basic")
_reg("snip and round corner rectangle",        _BASIC, "Snip and Round Corner Rectangle",        "basic")
_reg("frame",              _BASIC, "Frame",              "basic")
_reg("frame corner",       _BASIC, "Frame Corner",       "basic")
_reg("l shape",            _BASIC, "L Shape",            "basic", ("l-shape",))
_reg("diagonal stripe",    _BASIC, "Diagonal Stripe",    "basic")
_reg("plaque",             _BASIC, "Plaque",             "basic")
_reg("donut",              _BASIC, "Donut",              "basic", ("ring",))
_reg("no symbol",          _BASIC, "No Symbol",          "basic", ("prohibition",))
_reg("left parenthesis",   _BASIC, "Left Parenthesis",   "basic")
_reg("right parenthesis",  _BASIC, "Right Parenthesis",  "basic")
_reg("left brace",         _BASIC, "Left Brace",         "basic")
_reg("right brace",        _BASIC, "Right Brace",        "basic")

# ── Flowchart ─────────────────────────────────────────────────────────────
_reg("process",            _FLOW, "Process",             "flowchart")
_reg("decision",           _FLOW, "Decision",            "flowchart")
_reg("subprocess",         _FLOW, "Subprocess",          "flowchart", ("predefined process",))
_reg("start/end",          _FLOW, "Start/End",           "flowchart", ("terminator", "start", "end"))
_reg("document",           _FLOW, "Document",            "flowchart", ("doc",))
_reg("data",               _FLOW, "Data",                "flowchart", ("io", "input/output"))
_reg("database",           _FLOW, "Database",            "flowchart", ("db",))
_reg("external data",      _FLOW, "External Data",       "flowchart")
_reg("on-page reference",  _FLOW, "On-page reference",   "flowchart", ("on page reference",))
_reg("off-page reference", _FLOW, "Off-page reference",  "flowchart", ("off page reference",))

# ── Arrows ────────────────────────────────────────────────────────────────
_reg("simple arrow",       _ARROWS, "Simple Arrow",       "arrows", ("arrow",))
_reg("simple double arrow", _ARROWS, "Simple Double Arrow", "arrows", ("double arrow",))
_reg("modern arrow",       _ARROWS, "Modern Arrow",       "arrows")
_reg("flexible arrow",     _ARROWS, "Flexible Arrow",     "arrows")
_reg("bent arrow",         _ARROWS, "Bent Arrow",         "arrows")
_reg("u-turn arrow",       _ARROWS, "U-Turn Arrow",       "arrows", ("uturn arrow",))
_reg("sharp bent arrow",   _ARROWS, "Sharp Bent Arrow",   "arrows")
_reg("curved right arrow", _ARROWS, "Curved Right Arrow", "arrows")
_reg("curved left arrow",  _ARROWS, "Curved Left Arrow",  "arrows")
_reg("striped arrow",      _ARROWS, "Striped Arrow",      "arrows")
_reg("notched arrow",      _ARROWS, "Notched Arrow",      "arrows")
_reg("block arrow",        _ARROWS, "Block Arrow",        "arrows")
_reg("circular arrow",     _ARROWS, "Circular Arrow",     "arrows")
_reg("quad arrow",         _ARROWS, "Quad Arrow",         "arrows")
_reg("left-right-up arrow", _ARROWS, "Left-Right-Up Arrow", "arrows")
_reg("left-right arrow block", _ARROWS, "Left-Right Arrow Block", "arrows")
_reg("quad arrow block",   _ARROWS, "Quad Arrow Block",   "arrows")

# ── Draw primitives (bypass stencils) ────────────────────────────────────
DRAW_PRIMITIVES: set[str] = {"line"}


# ---------------------------------------------------------------------------
# Fuzzy matching
# ---------------------------------------------------------------------------

# Build a list of all unique lookup keys for fuzzy matching
_ALL_KEYS: list[str] = list(SHAPE_REGISTRY.keys())


def find_closest_shape(name: str, cutoff: float = 0.6) -> tuple[str, ShapeSpec] | None:
    """Return the closest matching (key, ShapeSpec) or None if nothing is close enough."""
    matches = difflib.get_close_matches(name.lower(), _ALL_KEYS, n=1, cutoff=cutoff)
    if matches:
        key = matches[0]
        return key, SHAPE_REGISTRY[key]
    return None


def get_categories() -> list[str]:
    """Return sorted list of unique categories."""
    return sorted({spec.category for spec in SHAPE_REGISTRY.values()})
