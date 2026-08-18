"""MCP Resources — expose Visio documents, templates, and stencils as readable resources."""

import os
import json
import glob as globmod
from typing import Optional

from visio_server.app import mcp
from visio_server.com.lifecycle import get_visio_app, get_open_documents
from visio_server.com.helpers import normalize_path


def _visio_content_dirs() -> list[str]:
    """Return candidate directories for built-in Visio stencils/templates."""
    dirs = []
    for env_var in ("ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(env_var, "")
        if base:
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16\Visio Content\1033"))
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16"))
    # User's My Shapes folder
    docs = os.path.expandvars(r"%USERPROFILE%\Documents\My Shapes")
    if os.path.isdir(docs):
        dirs.append(docs)
    return [d for d in dirs if os.path.isdir(d)]


@mcp.resource("visio://documents")
def list_open_documents() -> str:
    """List all currently open Visio documents with basic metadata."""
    docs = get_open_documents()
    result = []
    for path, doc in docs.items():
        try:
            result.append({
                "path": path,
                "name": doc.Name,
                "pages": doc.Pages.Count,
                "saved": not doc.Saved == 0,
            })
        except Exception:
            result.append({"path": path, "status": "stale reference"})
    return json.dumps(result, indent=2)


@mcp.resource("visio://documents/{path}")
def get_document_detail(path: str) -> str:
    """Get detailed info for a specific open Visio document.

    Returns pages with shape counts and document properties.
    """
    docs = get_open_documents()
    norm = normalize_path(path)
    doc = docs.get(norm)
    if doc is None:
        return json.dumps({"error": f"Document not open: {path}"})

    try:
        pages_info = []
        for i in range(1, doc.Pages.Count + 1):
            pg = doc.Pages.Item(i)
            pages_info.append({
                "index": i,
                "name": pg.Name,
                "shape_count": pg.Shapes.Count,
            })

        return json.dumps({
            "path": norm,
            "name": doc.Name,
            "title": doc.Title,
            "creator": doc.Creator,
            "description": doc.Description,
            "pages": pages_info,
        }, indent=2)
    except Exception as exc:
        return json.dumps({"error": str(exc)})


@mcp.resource("visio://templates")
def list_available_templates() -> str:
    """List Visio template files (.vstx, .vst) found in standard locations."""
    templates = []
    for d in _visio_content_dirs():
        for ext in ("*.vstx", "*.vst"):
            for f in globmod.glob(os.path.join(d, "**", ext), recursive=True):
                templates.append({
                    "name": os.path.splitext(os.path.basename(f))[0],
                    "path": f,
                    "extension": os.path.splitext(f)[1],
                })
    return json.dumps(templates, indent=2)


@mcp.resource("visio://stencils")
def list_available_stencils() -> str:
    """List Visio stencil files (.vssx, .vss) and their master shapes.

    Only reads master names from stencils that are already open in Visio.
    For closed stencils, returns just the file path.
    """
    app = get_visio_app()
    stencils = []

    # Already-open stencils (can enumerate masters)
    seen_paths = set()
    for doc in app.Documents:
        try:
            if doc.Type == 2:  # visTypeStencil
                masters = [doc.Masters.Item(i + 1).Name for i in range(doc.Masters.Count)]
                stencils.append({
                    "name": doc.Name,
                    "path": doc.FullName,
                    "open": True,
                    "masters": masters,
                })
                seen_paths.add(os.path.normpath(doc.FullName).upper())
        except Exception:
            continue

    # On-disk stencils not yet open
    for d in _visio_content_dirs():
        for ext in ("*.vssx", "*.vss"):
            for f in globmod.glob(os.path.join(d, "**", ext), recursive=True):
                if os.path.normpath(f).upper() not in seen_paths:
                    stencils.append({
                        "name": os.path.splitext(os.path.basename(f))[0],
                        "path": f,
                        "open": False,
                    })

    return json.dumps(stencils, indent=2)
