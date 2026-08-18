"""Shape formatting tools — colors, lines, fonts, resize, move."""

from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_open_documents
from visio_server.com.helpers import (
    normalize_path,
    get_valid_doc,
    resolve_page,
    find_shape_on_page,
)


def _color_to_rgb_formula(color: str) -> str:
    """Convert a color string to a Visio RGB formula.

    Accepts hex (#RRGGBB), named colors, or raw RGB(r,g,b) formulas.
    """
    color = color.strip()
    if color.startswith("#") and len(color) == 7:
        r = int(color[1:3], 16)
        g = int(color[3:5], 16)
        b = int(color[5:7], 16)
        return f"RGB({r},{g},{b})"
    if color.upper().startswith("RGB("):
        return color
    # Named color mapping (common ones)
    named = {
        "red": "RGB(255,0,0)", "green": "RGB(0,128,0)", "blue": "RGB(0,0,255)",
        "black": "RGB(0,0,0)", "white": "RGB(255,255,255)", "yellow": "RGB(255,255,0)",
        "orange": "RGB(255,165,0)", "purple": "RGB(128,0,128)", "gray": "RGB(128,128,128)",
        "grey": "RGB(128,128,128)", "cyan": "RGB(0,255,255)", "magenta": "RGB(255,0,255)",
    }
    return named.get(color.lower(), color)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def format_shape(
    file_path: str,
    shape_id: int,
    fill_color: Optional[str] = None,
    line_color: Optional[str] = None,
    line_weight: Optional[str] = None,
    line_pattern: Optional[int] = None,
    fill_transparency: Optional[float] = None,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Format a shape's visual appearance.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape to format.
        fill_color: Fill color — hex (#FF0000), name ("red"), or RGB(r,g,b).
        line_color: Line/border color.
        line_weight: Line thickness (e.g., "2 pt", "0.5 mm").
        line_pattern: Line pattern number (0=none, 1=solid, 2=dashed, etc.).
        fill_transparency: Fill transparency 0.0 (opaque) to 1.0 (invisible).
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)
    shape = find_shape_on_page(page, shape_id)

    changes = []
    if fill_color is not None:
        shape.Cells("FillForegnd").FormulaU = _color_to_rgb_formula(fill_color)
        changes.append("fill_color")
    if line_color is not None:
        shape.Cells("LineColor").FormulaU = _color_to_rgb_formula(line_color)
        changes.append("line_color")
    if line_weight is not None:
        shape.Cells("LineWeight").FormulaU = line_weight
        changes.append("line_weight")
    if line_pattern is not None:
        shape.Cells("LinePattern").FormulaU = str(line_pattern)
        changes.append("line_pattern")
    if fill_transparency is not None:
        shape.Cells("FillForegndTrans").FormulaU = str(fill_transparency)
        changes.append("fill_transparency")

    doc.Save()
    return f"Shape {shape_id} formatted: {', '.join(changes)}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def format_text(
    file_path: str,
    shape_id: int,
    font: Optional[str] = None,
    size: Optional[str] = None,
    color: Optional[str] = None,
    bold: Optional[bool] = None,
    italic: Optional[bool] = None,
    alignment: Optional[str] = None,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Format the text of a shape.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        font: Font name (e.g., "Arial", "Calibri").
        size: Font size (e.g., "12 pt").
        color: Text color — hex, name, or RGB().
        bold: Set bold.
        italic: Set italic.
        alignment: Horizontal alignment — "left", "center", "right".
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)
    shape = find_shape_on_page(page, shape_id)

    changes = []
    if font is not None:
        shape.Cells("Char.Font").FormulaU = f'"{font}"'
        changes.append("font")
    if size is not None:
        shape.Cells("Char.Size").FormulaU = size
        changes.append("size")
    if color is not None:
        shape.Cells("Char.Color").FormulaU = _color_to_rgb_formula(color)
        changes.append("color")
    if bold is not None:
        shape.Cells("Char.Style").FormulaU = str(1 if bold else 0)
        changes.append("bold")
    if italic is not None:
        val = shape.Cells("Char.Style").ResultIU
        # Preserve bold bit (1), toggle italic bit (2)
        if italic:
            val = int(val) | 2
        else:
            val = int(val) & ~2
        shape.Cells("Char.Style").FormulaU = str(val)
        changes.append("italic")
    if alignment is not None:
        align_map = {"left": "0", "center": "1", "right": "2"}
        shape.Cells("Para.HorzAlign").FormulaU = align_map.get(alignment.lower(), "1")
        changes.append("alignment")

    doc.Save()
    return f"Text on shape {shape_id} formatted: {', '.join(changes)}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def resize_shape(
    file_path: str,
    shape_id: int,
    width: Optional[float] = None,
    height: Optional[float] = None,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Resize a shape.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        width: New width in inches.
        height: New height in inches.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)
    shape = find_shape_on_page(page, shape_id)

    if width is not None:
        shape.Cells("Width").FormulaU = f"{width} in"
    if height is not None:
        shape.Cells("Height").FormulaU = f"{height} in"

    doc.Save()
    return f"Shape {shape_id} resized"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def move_shape(
    file_path: str,
    shape_id: int,
    x: float,
    y: float,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Move a shape to new coordinates.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        x: New X-coordinate (inches from origin).
        y: New Y-coordinate (inches from origin).
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)
    shape = find_shape_on_page(page, shape_id)

    shape.Cells("PinX").FormulaU = f"{x} in"
    shape.Cells("PinY").FormulaU = f"{y} in"

    doc.Save()
    return f"Shape {shape_id} moved to ({x}, {y})"
