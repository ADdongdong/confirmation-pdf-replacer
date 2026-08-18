"""Export tools — export pages or documents to PNG, SVG, PDF."""

import os
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_open_documents
from visio_server.com.helpers import normalize_path, get_valid_doc, resolve_page

DEFAULT_EXPORT_DIR = os.path.expandvars(r"%USERPROFILE%\Documents")


def _resolve_export_path(output_path: Optional[str], doc_path: str, suffix: str, ext: str) -> str:
    """Build an export file path with sensible defaults."""
    if output_path:
        p = normalize_path(output_path)
    else:
        base = os.path.splitext(os.path.basename(doc_path))[0]
        p = normalize_path(os.path.join(DEFAULT_EXPORT_DIR, f"{base}{suffix}.{ext}"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True, openWorldHint=True))
async def export_page(
    file_path: str,
    format: str = "png",
    output_path: Optional[str] = None,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Export a single page to an image or PDF.

    Args:
        file_path: Path to the Visio file.
        format: Output format — "png", "svg", or "pdf".
        output_path: Where to save. Auto-generated if omitted.
        page_name: Page by name (default: first page).
        page_index: Page by 1-based index.

    Returns:
        Path to the exported file.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    fmt = format.lower()
    suffix = f"_page{page.Index}"
    out = _resolve_export_path(output_path, file_path, suffix, fmt)

    page.Export(out)
    return f"Page '{page.Name}' exported to: {out}"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True, openWorldHint=True))
async def export_document(
    file_path: str,
    output_path: Optional[str] = None,
) -> str:
    """Export the entire Visio document to PDF.

    Args:
        file_path: Path to the Visio file.
        output_path: Where to save the PDF. Auto-generated if omitted.

    Returns:
        Path to the exported PDF.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    out = _resolve_export_path(output_path, file_path, "", "pdf")
    doc.ExportAsFixedFormat(1, out, 0, 0)  # visFixedFormatPDF=1
    return f"Document exported to: {out}"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True, openWorldHint=True))
async def export_selection(
    file_path: str,
    shape_ids: list[int],
    format: str = "png",
    output_path: Optional[str] = None,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Export specific shapes to an image file.

    Args:
        file_path: Path to the Visio file.
        shape_ids: List of shape IDs to export.
        format: Output format — "png", "svg", or "pdf".
        output_path: Where to save. Auto-generated if omitted.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Path to the exported file.
    """
    from visio_server.com.lifecycle import get_visio_app

    app = get_visio_app()
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    # Build a selection from the shape IDs
    window = app.ActiveWindow
    window.DeselectAll()
    for sid in shape_ids:
        for shape in page.Shapes:
            if shape.ID == sid:
                window.Select(shape, 2)  # visSelect = 2
                break

    selection = window.Selection
    if selection.Count == 0:
        from visio_server.errors import ShapeNotFoundError
        raise ShapeNotFoundError(f"None of the shape IDs {shape_ids} found on page")

    fmt = format.lower()
    out = _resolve_export_path(output_path, file_path, "_selection", fmt)
    selection.Export(out)
    return f"Selection ({selection.Count} shapes) exported to: {out}"
