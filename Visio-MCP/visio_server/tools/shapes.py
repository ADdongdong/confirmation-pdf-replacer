"""Shape tools — add, list, modify text on shapes."""

import json
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_visio_app, get_open_documents
from visio_server.com.helpers import (
    normalize_path,
    get_valid_doc,
    resolve_page,
    find_shape_on_page,
)
from visio_server.com.stencils import get_master
from visio_server.tools.shape_registry import (
    SHAPE_REGISTRY,
    DRAW_PRIMITIVES,
    find_closest_shape,
    get_categories,
)


async def _ensure_open(file_path: str) -> str:
    """Open the file if not already tracked; return normalized path."""
    from visio_server.tools.documents import open_visio_file

    docs = get_open_documents()
    file_path = normalize_path(file_path)
    if file_path not in docs:
        await open_visio_file(file_path)
    return file_path


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False, openWorldHint=True))
async def add_shape(
    file_path: str,
    shape_type: str,
    x: float,
    y: float,
    width: Optional[float] = 1.0,
    height: Optional[float] = 1.0,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Add a shape to a Visio document.

    Supports 90+ shape types across basic shapes, flowchart symbols, and
    arrows.  Use ``list_shape_types`` to discover available names.

    Args:
        file_path: Path to the Visio file.
        shape_type: Shape name — e.g. "Rectangle", "Diamond", "Decision",
            "Star", "Block Arrow", etc.  Case-insensitive.  Unrecognised
            names are fuzzy-matched to the closest known shape; if nothing
            is close enough a rectangle is used as fallback.
        x: X-coordinate (inches from origin).
        y: Y-coordinate (inches from origin).
        width: Shape width (default 1.0).
        height: Shape height (default 1.0).
        page_name: Target page by name (default: first page).
        page_index: Target page by 1-based index.

    Returns:
        Confirmation with the new shape's ID.
    """
    file_path = await _ensure_open(file_path)
    docs = get_open_documents()
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    kind = shape_type.lower()
    note = ""

    # Fast path: line is the only remaining draw-primitive
    if kind in DRAW_PRIMITIVES:
        shape = page.DrawLine(x, y, x + width, y + height)
        shape.Text = shape_type
        doc.Save()
        return f"Shape '{shape_type}' added at ({x}, {y}) with ID {shape.ID}"

    # Look up in the stencil-backed registry
    spec = SHAPE_REGISTRY.get(kind)

    if spec is None:
        # Fuzzy match
        match = find_closest_shape(kind)
        if match:
            matched_key, spec = match
            note = f" (interpreted '{shape_type}' as '{spec.master}')"
        else:
            # Ultimate fallback: draw a rectangle
            shape = page.DrawRectangle(x, y, x + width, y + height)
            shape.Text = shape_type
            doc.Save()
            return (
                f"Shape '{shape_type}' not recognised — drew a Rectangle at "
                f"({x}, {y}) with ID {shape.ID}. "
                f"Use list_shape_types to see available shapes."
            )

    # Drop the master shape from the stencil
    master = get_master(spec.stencil, spec.master)
    shape = page.Drop(master, x, y)

    # Resize to requested dimensions
    if width is not None:
        shape.Cells("Width").ResultIU = width
    if height is not None:
        shape.Cells("Height").ResultIU = height

    doc.Save()
    return f"Shape '{spec.master}' added at ({x}, {y}) with ID {shape.ID}{note}"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True))
async def list_shape_types(
    category: Optional[str] = None,
) -> str:
    """List available shape types that can be used with ``add_shape``.

    Args:
        category: Optional filter — "basic", "flowchart", "arrows", or
            omit for all categories.

    Returns:
        JSON array of ``{"name", "category", "master", "aliases"}``.
    """
    seen_masters: set[str] = set()
    result = []
    for key, spec in SHAPE_REGISTRY.items():
        # Deduplicate: only emit once per (stencil, master) pair
        ident = f"{spec.stencil}::{spec.master}"
        if ident in seen_masters:
            continue
        seen_masters.add(ident)

        if category and spec.category != category.lower():
            continue

        aliases = [a for a in spec.aliases if a.lower() != key]
        result.append({
            "name": key,
            "category": spec.category,
            "master": spec.master,
            "aliases": aliases,
        })

    # Also include the draw primitive(s)
    if not category or category.lower() == "basic":
        result.append({
            "name": "line",
            "category": "basic",
            "master": "(draw primitive)",
            "aliases": [],
        })

    result.sort(key=lambda r: (r["category"], r["name"]))
    return json.dumps(result, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def add_text(
    file_path: str,
    shape_id: int,
    text: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Set the text of a shape in a Visio document.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the target shape.
        text: Text to set on the shape.
        page_name: Target page by name (default: first page).
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    file_path = await _ensure_open(file_path)
    docs = get_open_documents()
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    shape = find_shape_on_page(page, shape_id)
    shape.Text = text
    doc.Save()
    return f"Text set on shape {shape_id}"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True))
async def list_shapes(
    file_path: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """List all shapes on a page in a Visio document.

    Args:
        file_path: Path to the Visio file.
        page_name: Target page by name (default: first page).
        page_index: Target page by 1-based index.

    Returns:
        JSON array of shape info (ID, Name, Text, position, size).
    """
    file_path = await _ensure_open(file_path)
    docs = get_open_documents()
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    shapes_info = []
    for shape in page.Shapes:
        shapes_info.append(
            {
                "ID": shape.ID,
                "Name": shape.Name,
                "Text": shape.Text,
                "Type": shape.Type,
                "Position": {
                    "X": shape.Cells("PinX").Result(""),
                    "Y": shape.Cells("PinY").Result(""),
                },
                "Size": {
                    "Width": shape.Cells("Width").Result(""),
                    "Height": shape.Cells("Height").Result(""),
                },
            }
        )
    return json.dumps(shapes_info, indent=2)
