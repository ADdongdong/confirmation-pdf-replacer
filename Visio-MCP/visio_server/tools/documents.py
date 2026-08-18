"""Document lifecycle tools — create, open, close, undo, redo."""

import os
import asyncio
import time
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_visio_app, get_open_documents
from visio_server.com.helpers import normalize_path, get_valid_doc
from visio_server.errors import DocumentNotFoundError

DEFAULT_SAVE_PATH = os.path.expandvars(r"%USERPROFILE%\Documents")


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False, openWorldHint=True))
async def create_visio_file(
    template_path: Optional[str] = None,
    save_path: Optional[str] = None,
) -> str:
    """Create a new Visio file.

    Args:
        template_path: Path to a Visio template (.vstx/.vst). Uses blank if omitted.
        save_path: Where to save. Defaults to Documents folder with a timestamped name.

    Returns:
        The absolute path to the created file.
    """
    app = get_visio_app()
    docs = get_open_documents()

    if not save_path:
        filename = f"New_Diagram_{int(time.time())}.vsdx"
        save_path = os.path.join(DEFAULT_SAVE_PATH, filename)
    elif os.path.dirname(save_path) == "":
        save_path = os.path.join(DEFAULT_SAVE_PATH, save_path)

    save_path = normalize_path(save_path)
    os.makedirs(os.path.dirname(save_path), exist_ok=True)

    template = ""
    if template_path and os.path.exists(template_path):
        template = template_path

    doc = app.Documents.Add(template)
    await asyncio.sleep(1)  # Let COM stabilize (non-blocking)
    doc.SaveAs(save_path)
    docs[save_path] = doc

    return f"Visio file created successfully at: {save_path}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True, openWorldHint=True))
async def open_visio_file(file_path: str) -> str:
    """Open an existing Visio file.

    Args:
        file_path: Path to the Visio file to open.

    Returns:
        Result message.
    """
    app = get_visio_app()
    docs = get_open_documents()
    file_path = normalize_path(file_path)

    if not os.path.exists(file_path):
        raise DocumentNotFoundError(f"File does not exist: {file_path}")

    # Already open and healthy?
    if file_path in docs:
        try:
            _ = docs[file_path].Name
            return f"Visio file is already open: {file_path}"
        except Exception:
            docs.pop(file_path, None)

    doc = app.Documents.Open(file_path)
    docs[file_path] = doc
    return f"Visio file opened successfully: {file_path}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True, idempotentHint=True))
async def close_document(
    file_path: str, save_changes: Optional[bool] = True
) -> str:
    """Close a Visio document.

    Args:
        file_path: Path to the Visio file.
        save_changes: Save before closing (default True).

    Returns:
        Result message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    if save_changes:
        doc.Save()
    doc.Close()
    docs.pop(file_path, None)
    return f"Document closed: {file_path}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True, idempotentHint=False))
async def undo(file_path: str) -> str:
    """Undo the last operation in a Visio document.

    Args:
        file_path: Path to the Visio file.

    Returns:
        Result message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    doc.Undo()
    return f"Undo performed on: {file_path}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True, idempotentHint=False))
async def redo(file_path: str) -> str:
    """Redo the last undone operation in a Visio document.

    Args:
        file_path: Path to the Visio file.

    Returns:
        Result message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    doc.Redo()
    return f"Redo performed on: {file_path}"
