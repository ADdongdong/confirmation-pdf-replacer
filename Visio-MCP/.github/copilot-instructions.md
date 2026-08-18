# Copilot Instructions — Visio MCP Server

## Architecture

This is a modular MCP (Model Context Protocol) server that exposes Microsoft Visio diagram automation as MCP tools, resources, and prompts over stdio transport. It uses `FastMCP` from the `mcp` Python package and drives Visio through COM automation via `win32com.client`.

### Package layout
```
visio_server/
├── app.py              # FastMCP instance, server startup
├── __main__.py         # `python -m visio_server` entry point
├── errors.py           # Custom exceptions (VisioError, COMError, etc.)
├── com/
│   ├── lifecycle.py    # Singleton COM app, open_documents registry, cleanup
│   └── helpers.py      # normalize_path(), get_valid_doc(), resolve_page(), find_shape_on_page()
├── tools/              # @mcp.tool() functions — one module per domain
│   ├── documents.py    # create, open, close, undo, redo
│   ├── shapes.py       # add_shape, add_text, list_shapes
│   ├── connectors.py   # connect_shapes (uses stencil master)
│   ├── pages.py        # list, add, delete pages
│   ├── formatting.py   # format_shape, format_text, resize, move
│   ├── stencils.py     # list, open stencils, list/drop masters
│   ├── layers.py       # list, add, assign, visibility
│   ├── properties.py   # shape data (custom properties) CRUD
│   ├── export.py       # export page/document/selection to PNG/SVG/PDF
│   └── metadata.py     # document properties get/set
├── resources/          # @mcp.resource() — visio:// URI scheme
│   └── documents.py    # open docs, doc detail, templates, stencils
└── prompts/            # @mcp.prompt() — diagram workflow templates
    └── diagrams.py     # flowchart, org chart, network diagram
```

### Key patterns
- **COM singleton**: `com/lifecycle.py` manages one `visio_app` instance and a `dict[str, COMDoc]` registry keyed by normalized absolute paths.
- **Path normalization**: All file paths go through `normalize_path()` before use as dict keys (prevents duplicates from mixed separators/casing).
- **Page resolution**: Tools accept optional `page_name`/`page_index` params; `resolve_page()` dispatches to the correct page (defaults to first).
- **Structured errors**: Tools raise typed exceptions from `errors.py` (not string-based error returns). Exception types: `COMError`, `DocumentNotFoundError`, `ShapeNotFoundError`, `PageNotFoundError`, `StencilError`.
- **Tool annotations**: All tools carry `ToolAnnotations` (readOnlyHint, destructiveHint, idempotentHint) for MCP client awareness.

## Build & Run

```bash
pip install -e .              # Install with dependencies
python -m visio_server        # Run the MCP server (stdio)
```

No test suite yet. Compile-check all modules:
```bash
python -m py_compile visio_server/app.py  # repeat per file, or use a glob
```

## Conventions

- All tool functions are **async** and return plain strings or JSON strings.
- Shape coordinates use **Visio's coordinate system** (inches, origin at bottom-left).
- Connectors use real master shapes from the built-in `CONNEC_U.VSSX` stencil (not the UI-dependent `ConnectorToolDataObject`).
- Colors accept hex (`#FF0000`), named (`red`), or `RGB(r,g,b)` formula strings.
- The server lazily initializes Visio COM on the first tool/resource request that needs it.
