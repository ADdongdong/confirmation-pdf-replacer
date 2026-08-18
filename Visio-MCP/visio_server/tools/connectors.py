"""Connector tools — connect shapes with routable connectors."""

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

_CONNECTOR_STENCIL = "CONNEC_U.VSSX"
_CONNECTOR_MASTER = "Dynamic connector"


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False))
async def connect_shapes(
    file_path: str,
    shape1_id: int,
    shape2_id: int,
    connector_type: Optional[str] = "Dynamic",
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Connect two shapes in a Visio document with a routable connector.

    Args:
        file_path: Path to the Visio file.
        shape1_id: ID of the source shape.
        shape2_id: ID of the target shape.
        connector_type: "Dynamic" (default), "Straight", or "Curved".
        page_name: Target page by name (default: first page).
        page_index: Target page by 1-based index.

    Returns:
        Confirmation message.
    """
    app = get_visio_app()  # noqa: F841 — ensures COM is initialized
    docs = get_open_documents()
    file_path = normalize_path(file_path)

    if file_path not in docs:
        from visio_server.tools.documents import open_visio_file
        await open_visio_file(file_path)

    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)

    shape1 = find_shape_on_page(page, shape1_id)
    shape2 = find_shape_on_page(page, shape2_id)

    # Drop a real connector master instead of using ConnectorToolDataObject
    master = get_master(_CONNECTOR_STENCIL, _CONNECTOR_MASTER)
    connector = page.Drop(master, 0, 0)

    # Style the connector
    ct = connector_type.lower()
    if ct == "straight":
        connector.Cells("ShapeRouteStyle").FormulaU = "16"  # visLORouteStraight
        connector.Cells("ConLineRouteExt").FormulaU = "1"   # Straight
    elif ct == "curved":
        connector.Cells("ShapeRouteStyle").FormulaU = "1"   # visLORouteCurve
        connector.Cells("Rounding").FormulaU = "0.25 in"

    # Glue endpoints to shape centers
    connector.Cells("BeginX").GlueTo(shape1.Cells("PinX"))
    connector.Cells("EndX").GlueTo(shape2.Cells("PinX"))

    doc.Save()
    return f"Shapes {shape1_id} and {shape2_id} connected ({connector_type})"
