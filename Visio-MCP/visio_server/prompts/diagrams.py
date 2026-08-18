"""MCP Prompts — reusable prompt templates for common Visio diagram workflows."""

from visio_server.app import mcp


@mcp.prompt()
def flowchart_builder(
    steps: str,
    title: str = "Flowchart",
) -> str:
    """Build a flowchart from a list of steps.

    Args:
        steps: Newline-separated list of steps (e.g., "Start\\nProcess Data\\nDecision?\\nEnd").
        title: Title for the diagram.
    """
    return f"""You are creating a Visio flowchart titled "{title}".

Steps provided by the user:
{steps}

Instructions:
1. Create a new Visio file (or use the provided one).
2. For each step, add a shape at appropriate coordinates:
   - Use "Rectangle" for process steps.
   - Use "Circle" for start/end terminators.
   - Indent decision branches with appropriate spacing.
3. Connect shapes sequentially using connect_shapes.
4. Add the step text to each shape using add_text.
5. Space shapes vertically with ~1.5 inch gaps.
6. Start at coordinates (4, 10) and work downward.
7. After building, use list_shapes to verify the result.
"""


@mcp.prompt()
def org_chart_builder(
    hierarchy: str,
    title: str = "Organization Chart",
) -> str:
    """Build an organizational chart from a hierarchy description.

    Args:
        hierarchy: Indented text representing the org hierarchy
                   (e.g., "CEO\\n  VP Engineering\\n    Team Lead\\n  VP Sales").
        title: Title for the diagram.
    """
    return f"""You are creating a Visio organization chart titled "{title}".

Hierarchy provided by the user:
{hierarchy}

Instructions:
1. Create a new Visio file.
2. Parse the indented hierarchy — each indent level represents a reporting relationship.
3. For each person/role, add a Rectangle shape.
4. Layout strategy:
   - Root node at top center (x=4, y=10).
   - Each level down: decrease Y by 1.5 inches.
   - Spread siblings horizontally with 2-inch spacing, centered under parent.
5. Connect each child to their parent using connect_shapes.
6. Set each shape's text to the role/name using add_text.
7. Verify with list_shapes when complete.
"""


@mcp.prompt()
def network_diagram(
    devices: str,
    connections: str = "",
    title: str = "Network Diagram",
) -> str:
    """Build a network topology diagram.

    Args:
        devices: Newline-separated list of devices (e.g., "Router-1\\nSwitch-A\\nServer-Web\\nServer-DB").
        connections: Newline-separated pairs of connected devices
                     (e.g., "Router-1 -- Switch-A\\nSwitch-A -- Server-Web").
        title: Title for the diagram.
    """
    return f"""You are creating a Visio network diagram titled "{title}".

Devices:
{devices}

Connections:
{connections if connections else "(connect devices based on logical topology)"}

Instructions:
1. Create a new Visio file.
2. Add a shape for each device:
   - Use "Rectangle" for switches/routers.
   - Use "Circle" for servers/endpoints.
3. Layout strategy:
   - Network core devices (routers) at top: y=10.
   - Distribution layer (switches) at y=7.
   - Access layer (servers, endpoints) at y=4.
   - Space horizontally with 2.5-inch gaps.
4. Connect devices as specified using connect_shapes.
5. Label each shape with the device name using add_text.
6. Verify with list_shapes when complete.
"""
