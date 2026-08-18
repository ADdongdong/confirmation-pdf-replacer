"""Stencil & master shape tools — open stencils, list masters, drop shapes."""

import os
import json
import glob as globmod
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_visio_app, get_open_documents
from visio_server.com.helpers import normalize_path, get_valid_doc, resolve_page
from visio_server.errors import StencilError


def _stencil_search_dirs() -> list[str]:
    """Return directories where Visio stencils may be found."""
    dirs = []
    for env_var in ("ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(env_var, "")
        if base:
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16\Visio Content\1033"))
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16"))
    my_shapes = os.path.expandvars(r"%USERPROFILE%\Documents\My Shapes")
    if os.path.isdir(my_shapes):
        dirs.append(my_shapes)
    return [d for d in dirs if os.path.isdir(d)]


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def list_stencils() -> str:
    """List available Visio stencil files (.vssx, .vss) on this system.

    Returns:
        JSON array of stencil info (name, path).
    """
    stencils = []
    for d in _stencil_search_dirs():
        for ext in ("*.vssx", "*.vss"):
            for f in globmod.glob(os.path.join(d, "**", ext), recursive=True):
                stencils.append({
                    "name": os.path.splitext(os.path.basename(f))[0],
                    "path": f,
                })
    return json.dumps(stencils, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def open_stencil(stencil_path: str) -> str:
    """Open a Visio stencil to make its master shapes available.

    Args:
        stencil_path: Full path to the stencil file, or just the filename
                      (Visio will search its content paths).

    Returns:
        Confirmation with list of master shape names.
    """
    app = get_visio_app()

    # Check if already open
    basename = os.path.basename(stencil_path).upper()
    for doc in app.Documents:
        try:
            if doc.Name.upper() == basename:
                masters = [doc.Masters.Item(i + 1).Name for i in range(doc.Masters.Count)]
                return json.dumps({
                    "status": "already open",
                    "stencil": doc.Name,
                    "masters": masters,
                }, indent=2)
        except Exception:
            continue

    try:
        # visOpenDocked = 64: opens stencil in the stencil pane
        stencil_doc = app.Documents.OpenEx(stencil_path, 64)
    except Exception as exc:
        raise StencilError(f"Failed to open stencil '{stencil_path}': {exc}")

    masters = [stencil_doc.Masters.Item(i + 1).Name for i in range(stencil_doc.Masters.Count)]
    return json.dumps({
        "status": "opened",
        "stencil": stencil_doc.Name,
        "masters": masters,
    }, indent=2)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def list_masters(stencil_name: str) -> str:
    """List master shapes in an open stencil.

    Args:
        stencil_name: Name of the stencil (e.g., "CONNEC_U.VSSX" or "Basic Shapes").

    Returns:
        JSON array of master shape names.
    """
    app = get_visio_app()
    target = stencil_name.upper()

    for doc in app.Documents:
        try:
            if doc.Name.upper() == target or doc.Name.upper().startswith(target):
                masters = []
                for i in range(doc.Masters.Count):
                    m = doc.Masters.Item(i + 1)
                    masters.append({"name": m.Name, "id": m.ID})
                return json.dumps(masters, indent=2)
        except Exception:
            continue

    raise StencilError(f"Stencil '{stencil_name}' is not open. Use open_stencil first.")


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False))
async def drop_master(
    file_path: str,
    stencil_name: str,
    master_name: str,
    x: float,
    y: float,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Drop a master shape from an open stencil onto a page.

    Args:
        file_path: Path to the Visio file.
        stencil_name: Name of the open stencil.
        master_name: Name of the master shape to drop.
        x: X-coordinate (inches).
        y: Y-coordinate (inches).
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation with the new shape's ID.
    """
    app = get_visio_app()
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    # Find the stencil and master
    target = stencil_name.upper()
    master = None
    for sdoc in app.Documents:
        try:
            if sdoc.Name.upper() == target or sdoc.Name.upper().startswith(target):
                master = sdoc.Masters.ItemU(master_name)
                break
        except Exception:
            continue

    if master is None:
        raise StencilError(
            f"Master '{master_name}' not found in stencil '{stencil_name}'. "
            "Ensure the stencil is open via open_stencil."
        )

    shape = page.Drop(master, x, y)
    doc.Save()
    return f"Master '{master_name}' dropped at ({x}, {y}) with ID {shape.ID}"
