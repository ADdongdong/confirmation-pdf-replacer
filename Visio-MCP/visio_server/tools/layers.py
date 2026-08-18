"""Layer management tools — list, add, assign, toggle visibility."""

import json
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


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def list_layers(
    file_path: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """List all layers on a page.

    Args:
        file_path: Path to the Visio file.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        JSON array of layer info.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    layers = []
    for i in range(1, page.Layers.Count + 1):
        layer = page.Layers.Item(i)
        layers.append({
            "index": i,
            "name": layer.Name,
            "visible": layer.CellsC(2).ResultIU != 0,  # visLayerVisible
            "print": layer.CellsC(3).ResultIU != 0,     # visLayerPrint
            "active": layer.CellsC(4).ResultIU != 0,    # visLayerActive
            "lock": layer.CellsC(5).ResultIU != 0,      # visLayerLock
        })
    return json.dumps(layers, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False))
async def add_layer(
    file_path: str,
    name: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Create a new layer on a page.

    Args:
        file_path: Path to the Visio file.
        name: Name for the new layer.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    page.Layers.Add(name)
    doc.Save()
    return f"Layer '{name}' added"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def assign_shape_to_layer(
    file_path: str,
    shape_id: int,
    layer_name: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Assign a shape to a layer.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        layer_name: Name of the layer to assign the shape to.
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

    # Find the layer
    target_layer = None
    for i in range(1, page.Layers.Count + 1):
        if page.Layers.Item(i).Name == layer_name:
            target_layer = page.Layers.Item(i)
            break

    if target_layer is None:
        from visio_server.errors import VisioError
        raise VisioError(f"Layer '{layer_name}' not found on page")

    target_layer.Add(shape, 0)  # 0 = don't remove from other layers
    doc.Save()
    return f"Shape {shape_id} assigned to layer '{layer_name}'"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def set_layer_visibility(
    file_path: str,
    layer_name: str,
    visible: bool,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Show or hide a layer.

    Args:
        file_path: Path to the Visio file.
        layer_name: Name of the layer.
        visible: True to show, False to hide.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    target_layer = None
    for i in range(1, page.Layers.Count + 1):
        if page.Layers.Item(i).Name == layer_name:
            target_layer = page.Layers.Item(i)
            break

    if target_layer is None:
        from visio_server.errors import VisioError
        raise VisioError(f"Layer '{layer_name}' not found on page")

    target_layer.CellsC(2).FormulaU = "1" if visible else "0"
    doc.Save()
    return f"Layer '{layer_name}' visibility set to {visible}"
