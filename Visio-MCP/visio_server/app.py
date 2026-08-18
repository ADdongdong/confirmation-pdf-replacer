"""FastMCP application instance and server configuration."""

import sys

from mcp.server.fastmcp import FastMCP

from visio_server.com.lifecycle import check_visio_installed

mcp = FastMCP("visio-server")


def run_server():
    """Entry point: verify Visio is installed and start MCP transport."""
    if not check_visio_installed():
        sys.stderr.write(
            "Microsoft Visio is not installed. This MCP server requires Visio.\n"
        )
        sys.exit(1)

    # Import tools/resources/prompts so they register with the mcp instance
    import visio_server.tools  # noqa: F401
    import visio_server.resources.documents  # noqa: F401
    import visio_server.prompts.diagrams  # noqa: F401

    mcp.run(transport="stdio")
