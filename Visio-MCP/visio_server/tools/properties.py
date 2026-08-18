"""Shape data/custom properties tools — read, write, add property rows."""

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

# Visio section/row constants
_visSectionProp = 243  # Shape Data section
_visTagDefault = 0


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
async def get_shape_data(
    file_path: str,
    shape_id: int,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Read all custom properties (Shape Data) from a shape.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        page_name: Target page by name.
        page_index: Target page by 1-based index.

    Returns:
        JSON object mapping property names to their values.
    """
    docs = get_open_documents()
    file_path = normalize_path(file_path)
    doc = get_valid_doc(docs, file_path)
    page = resolve_page(doc, page_name, page_index)
    shape = find_shape_on_page(page, shape_id)

    props = {}
    if shape.SectionExists(_visSectionProp, 0):
        for i in range(shape.Section(_visSectionProp).Count):
            row = shape.Section(_visSectionProp).Row(i)
            name = shape.CellsSRC(_visSectionProp, i, 2).FormulaU  # Label cell
            value = shape.CellsSRC(_visSectionProp, i, 0).FormulaU  # Value cell
            # Clean up label — remove quotes
            label = name.strip('"') if name else f"Row{i}"
            props[label] = value
    return json.dumps(props, indent=2)


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=True))
async def set_shape_data(
    file_path: str,
    shape_id: int,
    property_name: str,
    value: str,
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Set the value of an existing custom property on a shape.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        property_name: Name (label) of the property to set.
        value: New value (as a Visio formula string, e.g., '"text"' or '42').
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

    if not shape.SectionExists(_visSectionProp, 0):
        from visio_server.errors import VisioError
        raise VisioError(f"Shape {shape_id} has no Shape Data section")

    # Find row by label
    for i in range(shape.Section(_visSectionProp).Count):
        label = shape.CellsSRC(_visSectionProp, i, 2).FormulaU.strip('"')
        if label == property_name:
            shape.CellsSRC(_visSectionProp, i, 0).FormulaU = value
            doc.Save()
            return f"Property '{property_name}' set to {value} on shape {shape_id}"

    from visio_server.errors import VisioError
    raise VisioError(f"Property '{property_name}' not found on shape {shape_id}")


@mcp.tool(annotations=ToolAnnotations(destructiveHint=False, idempotentHint=False))
async def add_shape_data_row(
    file_path: str,
    shape_id: int,
    property_name: str,
    label: str,
    value: str = '""',
    prompt: str = '""',
    page_name: Optional[str] = None,
    page_index: Optional[int] = None,
) -> str:
    """Add a new custom property (Shape Data row) to a shape.

    Args:
        file_path: Path to the Visio file.
        shape_id: ID of the shape.
        property_name: Internal row name (e.g., "Prop.Department").
        label: Display label for the property.
        value: Initial value formula (default: empty string).
        prompt: Prompt text shown in the Shape Data window.
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

    # Ensure section exists
    if not shape.SectionExists(_visSectionProp, 0):
        shape.AddSection(_visSectionProp)

    row_idx = shape.AddNamedRow(_visSectionProp, property_name, _visTagDefault)
    shape.CellsSRC(_visSectionProp, row_idx, 0).FormulaU = value      # Value
    shape.CellsSRC(_visSectionProp, row_idx, 1).FormulaU = prompt     # Prompt
    shape.CellsSRC(_visSectionProp, row_idx, 2).FormulaU = f'"{label}"'  # Label

    doc.Save()
    return f"Property '{label}' added to shape {shape_id}"
