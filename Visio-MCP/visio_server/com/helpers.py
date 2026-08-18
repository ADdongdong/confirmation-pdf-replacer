"""COM helper utilities — path normalization, document validation, page resolution."""

import os
from typing import Optional

from visio_server.errors import COMError, DocumentNotFoundError, PageNotFoundError


def normalize_path(file_path: str) -> str:
    """Normalize a file path to a canonical absolute form for consistent dict keying."""
    return os.path.normpath(os.path.abspath(file_path))


def get_valid_doc(open_documents: dict, file_path: str):
    """Return a validated COM document object, or raise if stale/missing.

    Checks that the cached COM reference is still alive by probing .Name.
    If the reference is stale, removes it from the registry.  When a stale
    reference is detected, also verifies that the Visio app itself is alive
    (calling ``get_visio_app()``, which handles reconnection automatically).
    """
    doc = open_documents.get(file_path)
    if doc is None:
        raise DocumentNotFoundError(f"Document not open: {file_path}")

    try:
        _ = doc.Name  # Probe COM object health
        return doc
    except Exception:
        open_documents.pop(file_path, None)
        # Ensure the app is still alive (triggers reconnect if dead)
        from visio_server.com.lifecycle import get_visio_app
        try:
            get_visio_app()
        except Exception:
            pass
        raise COMError(
            f"Lost COM connection to document: {file_path}. "
            "It may have been closed externally. Please re-open it."
        )


def resolve_page(doc, page_name: Optional[str] = None, page_index: Optional[int] = None):
    """Return a page from the document by name, index, or default to first page.

    Args:
        doc: Visio Document COM object.
        page_name: Page name to look up.
        page_index: 1-based page index.

    Returns:
        Visio Page COM object.
    """
    if page_name is not None:
        try:
            return doc.Pages.ItemU(page_name)
        except Exception:
            raise PageNotFoundError(f"Page not found: {page_name}")

    if page_index is not None:
        try:
            return doc.Pages.Item(page_index)
        except Exception:
            raise PageNotFoundError(f"Page index out of range: {page_index}")

    # Default: first page
    try:
        return doc.Pages.Item(1)
    except Exception:
        raise PageNotFoundError("Document has no pages")


def find_shape_on_page(page, shape_id: int):
    """Find a shape by ID on the given page, or raise ShapeNotFoundError."""
    from visio_server.errors import ShapeNotFoundError

    for shape in page.Shapes:
        if shape.ID == shape_id:
            return shape
    raise ShapeNotFoundError(f"Shape ID {shape_id} not found on page '{page.Name}'")
