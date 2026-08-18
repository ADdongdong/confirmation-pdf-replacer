"""Document metadata tools — read and write document properties."""

import json
from typing import Optional

from mcp.types import ToolAnnotations

from visio_server.app import mcp
from visio_server.com.lifecycle import get_open_documents
from visio_server.com.helpers import normalize_path, get_valid_doc


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def get_document_info(file_path: str) -> str:
    """Get document properties and metadata.

    Args:
        file_path: Path to the Visio file.

    Returns:
        JSON with title, creator, description, keywords, page count, etc.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    info = {
        "path": file_path,
        "name": doc.Name,
        "title": doc.Title,
        "creator": doc.Creator,
        "description": doc.Description,
        "keywords": doc.Keywords,
        "subject": doc.Subject,
        "manager": doc.Manager,
        "company": doc.Company,
        "category": doc.Category,
        "pages": doc.Pages.Count,
        "saved": doc.Saved != 0,
        "read_only": doc.ReadOnly != 0,
    }
    return json.dumps(info, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def set_document_info(
    file_path: str,
    title: Optional[str] = None,
    creator: Optional[str] = None,
    description: Optional[str] = None,
    keywords: Optional[str] = None,
    subject: Optional[str] = None,
    manager: Optional[str] = None,
    company: Optional[str] = None,
    category: Optional[str] = None,
) -> str:
    """Update document properties.

    Args:
        file_path: Path to the Visio file.
        title: Document title.
        creator: Author name.
        description: Document description.
        keywords: Search keywords.
        subject: Document subject.
        manager: Manager name.
        company: Company name.
        category: Document category.

    Returns:
        Confirmation with updated fields.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)

    updated = []
    if title is not None:
        doc.Title = title
        updated.append("title")
    if creator is not None:
        doc.Creator = creator
        updated.append("creator")
    if description is not None:
        doc.Description = description
        updated.append("description")
    if keywords is not None:
        doc.Keywords = keywords
        updated.append("keywords")
    if subject is not None:
        doc.Subject = subject
        updated.append("subject")
    if manager is not None:
        doc.Manager = manager
        updated.append("manager")
    if company is not None:
        doc.Company = company
        updated.append("company")
    if category is not None:
        doc.Category = category
        updated.append("category")

    doc.Save()
    return f"Document info updated: {', '.join(updated)}"
