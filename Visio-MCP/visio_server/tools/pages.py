"""Page management tools — list, add, delete, navigate pages."""

import json
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_open_documents
from visio_server.com.helpers import normalize_path, get_valid_doc, resolve_page
from visio_server.errors import PageNotFoundError


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def list_pages(file_path: str) -> str:
    """List all pages in a Visio document.

    Args:
        file_path: Path to the Visio file.

    Returns:
        JSON array of page info (index, name, size, shape count).
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    pages = []
    for i in range(1, doc.Pages.Count + 1):
        pg = doc.Pages.Item(i)
        pages.append({
            "index": i,
            "name": pg.Name,
            "width": pg.PageSheet.Cells("PageWidth").Result(""),
            "height": pg.PageSheet.Cells("PageHeight").Result(""),
            "shape_count": pg.Shapes.Count,
            "background": pg.Background != 0,
        })
    return json.dumps(pages, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False))
async def add_page(
    file_path: str,
    name: Optional[str] = None,
    is_background: Optional[bool] = False,
) -> str:
    """Add a new page to a Visio document.

    Args:
        file_path: Path to the Visio file.
        name: Name for the new page (auto-generated if omitted).
        is_background: Create as a background page (default False).

    Returns:
        Confirmation with page name and index.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    page = doc.Pages.Add()
    if is_background:
        page.Background = -1  # True in Visio COM
    if name:
        page.Name = name

    doc.Save()
    return f"Page '{page.Name}' added at index {page.Index}"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True, idempotentHint=True))
async def delete_page(
    file_path: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Delete a page from a Visio document.

    Args:
        file_path: Path to the Visio file.
        page_name: Page to delete by name.
        page_index: Page to delete by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    if doc.Pages.Count <= 1:
        raise PageNotFoundError("Cannot delete the last page in a document")

    page = resolve_page(doc, page_name, page_index)
    deleted_name = page.Name
    page.Delete(0)  # 0 = don't renumber
    doc.Save()
    return f"Page '{deleted_name}' deleted"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def get_page_info(
    file_path: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Get detailed info about a specific page.

    Args:
        file_path: Path to the Visio file.
        page_name: Page by name.
        page_index: Page by 1-based index.

    Returns:
        JSON with page details including all shape IDs.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    shapes = []
    for s in page.Shapes:
        shapes.append({"id": s.ID, "name": s.Name, "text": s.Text})

    return json.dumps({
        "index": page.Index,
        "name": page.Name,
        "width": page.PageSheet.Cells("PageWidth").Result(""),
        "height": page.PageSheet.Cells("PageHeight").Result(""),
        "shape_count": page.Shapes.Count,
        "shapes": shapes,
    }, indent=2)
