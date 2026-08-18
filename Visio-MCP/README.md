# Visio MCP Server

An MCP server that gives GitHub Copilot CLI (and other MCP clients) full control over Microsoft Visio diagrams through COM automation.

## Prerequisites

- **Windows** with **Microsoft Visio** installed (desktop version, not web)
- **Python 3.10+** (`python --version` to check)
- **GitHub Copilot CLI** installed and working (see below)

## Installing GitHub Copilot CLI

If you don't already have GitHub Copilot CLI, follow these steps:

1. **Install Node.js 18+** if you don't have it — download from [nodejs.org](https://nodejs.org/)

2. **Install the Copilot CLI extension**

   ```powershell
   npm install -g @githubnext/github-copilot-cli
   ```

3. **Authenticate with GitHub**

   ```powershell
   github-copilot-cli auth
   ```

   Follow the prompts to sign in with a GitHub account that has an active Copilot subscription.

4. **Verify it works**

   ```powershell
   copilot --version
   ```

> **Note:** GitHub Copilot CLI requires a [GitHub Copilot](https://github.com/features/copilot) subscription (Individual, Business, or Enterprise).

## Installation

1. **Extract the project files** into a folder (e.g., `C:\Users\nmcgee\source\repos\Visio-MCP`)

   ```powershell
   cd C:\Users\nmcgee\source\repos\Visio-MCP
   ```

2. **Create a virtual environment and install dependencies**

   ```powershell
   python -m venv .venv
   .venv\Scripts\activate
   pip install mcp pywin32
   ```

3. **Verify it starts** (Visio will launch — this is expected)

   ```powershell
   .venv\Scripts\python.exe -m visio_server
   ```

   You should see Visio open. Press `Ctrl+C` to stop the server.

## Configuring Copilot CLI

Copilot CLI reads MCP server configs from two locations:

| Scope | File | Use when |
|-------|------|----------|
| **User-level** (all sessions) | `~\.copilot\mcp-config.json` | You want Visio tools available everywhere |
| **Project-level** (one repo) | `.copilot\mcp-config.json` in repo root | You want Visio tools only in this project |

### Option A: User-level setup (recommended)

Edit `%USERPROFILE%\.copilot\mcp-config.json` and add the `visio` entry to the existing `mcpServers` object:

```json
{
  "mcpServers": {
    "visio": {
      "type": "stdio",
      "command": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP\\.venv\\Scripts\\python.exe",
      "args": ["-m", "visio_server"],
      "env": {
        "PYTHONPATH": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP"
      }
    }
  }
}
```

> **Important:** Use the full absolute path to the **venv** Python executable, not the system Python. This ensures dependencies (`mcp`, `pywin32`) are found.

If you already have other MCP servers configured (like Playwright), just add the `"visio"` key alongside them:

```json
{
  "mcpServers": {
    "playwright": {
      "...": "your existing playwright config"
    },
    "visio": {
      "type": "stdio",
      "command": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP\\.venv\\Scripts\\python.exe",
      "args": ["-m", "visio_server"],
      "env": {
        "PYTHONPATH": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP"
      }
    }
  }
}
```

### Option B: Project-level setup

Create `.copilot/mcp-config.json` in the root of any repo where you want Visio tools:

```json
{
  "mcpServers": {
    "visio": {
      "type": "stdio",
      "command": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP\\.venv\\Scripts\\python.exe",
      "args": ["-m", "visio_server"],
      "env": {
        "PYTHONPATH": "C:\\Users\\nmcgee\\source\\repos\\Visio-MCP"
      }
    }
  }
}
```

### Option C: One-off via CLI flag

```powershell
copilot --additional-mcp-config @.copilot\mcp-config.json
```

## Verifying the Connection

After configuring, start a new Copilot CLI session and ask:

```
Create a new Visio diagram with three boxes connected in sequence
```

Copilot should call `create_visio_file`, `add_shape` (3×), and `connect_shapes` (2×). You'll see Visio open and the diagram appear.

If it doesn't work, check:
1. **Visio is installed** — the server checks the registry at startup and exits with an error if Visio isn't found
2. **Python path is correct** — run the `command` + `args` manually in PowerShell to see if the server starts
3. **PYTHONPATH is set** — must point to the repo root so `python -m visio_server` finds the package

## Available Tools

Once connected, Copilot has access to 32 tools across 10 categories:

### Document Lifecycle
| Tool | Description |
|------|-------------|
| `create_visio_file` | Create a new Visio file (blank or from template) |
| `open_visio_file` | Open an existing .vsdx file |
| `close_document` | Close a document (with optional save) |
| `undo` / `redo` | Undo or redo the last operation |

### Shapes
| Tool | Description |
|------|-------------|
| `add_shape` | Add a shape — supports 85+ types including basic shapes, flowchart symbols, and arrows (see below) |
| `list_shape_types` | Discover available shape names, filterable by category |
| `add_text` | Set text on a shape |
| `list_shapes` | List all shapes on a page (returns JSON) |
| `connect_shapes` | Connect two shapes with a routable connector |

#### Supported Shape Categories

`add_shape` accepts any of the following shape names (case-insensitive). Use `list_shape_types` to get the full list programmatically.

| Category | Examples |
|----------|----------|
| **Basic** (57 shapes) | Rectangle, Square, Circle, Ellipse, Diamond, Triangle, Pentagon, Hexagon, Octagon, Star (4/5/6/7/16/24/32-point), Cross, Chevron, Can, Cube, Parallelogram, Trapezoid, Rounded Rectangle, Cone, Pyramid, Funnel, Gear, Donut, Frame, L Shape, and many more |
| **Flowchart** (10 shapes) | Process, Decision, Subprocess, Start/End (Terminator), Document, Data (I/O), Database, External Data, On-page Reference, Off-page Reference |
| **Arrows** (17 shapes) | Simple Arrow, Double Arrow, Modern Arrow, Flexible Arrow, Bent Arrow, U-Turn Arrow, Block Arrow, Circular Arrow, Notched Arrow, Striped Arrow, Quad Arrow, and more |

**Aliases** are supported — e.g. `"star"` → 5-Point Star, `"box"` → Rectangle, `"terminator"` → Start/End, `"cylinder"` → Can.

**Fuzzy matching** handles typos — e.g. `"Octogon"` is auto-corrected to `"Octagon"` with a note in the response.

**Fallback**: completely unrecognised names draw a Rectangle with a suggestion to use `list_shape_types`.

### Pages
| Tool | Description |
|------|-------------|
| `list_pages` | List all pages with metadata |
| `add_page` | Add a new page |
| `delete_page` | Remove a page |
| `get_page_info` | Detailed page info including all shapes |

### Formatting
| Tool | Description |
|------|-------------|
| `format_shape` | Set fill color, line color, weight, pattern, transparency |
| `format_text` | Set font, size, color, bold, italic, alignment |
| `resize_shape` | Change shape dimensions |
| `move_shape` | Reposition a shape |

### Stencils & Masters
| Tool | Description |
|------|-------------|
| `list_stencils` | List available stencil files on the system |
| `open_stencil` | Open a stencil and list its masters |
| `list_masters` | List master shapes in an open stencil |
| `drop_master` | Drop a master shape onto a page |

### Layers
| Tool | Description |
|------|-------------|
| `list_layers` | List layers on a page |
| `add_layer` | Create a new layer |
| `assign_shape_to_layer` | Assign a shape to a layer |
| `set_layer_visibility` | Show or hide a layer |

### Shape Data (Custom Properties)
| Tool | Description |
|------|-------------|
| `get_shape_data` | Read custom properties from a shape |
| `set_shape_data` | Set a property value |
| `add_shape_data_row` | Add a new custom property definition |

### Export
| Tool | Description |
|------|-------------|
| `export_page` | Export a page to PNG, SVG, or PDF |
| `export_document` | Export the entire document to PDF |
| `export_selection` | Export specific shapes to an image |

### Metadata
| Tool | Description |
|------|-------------|
| `get_document_info` | Read document properties (title, author, etc.) |
| `set_document_info` | Update document properties |

## Resources

MCP resources expose read-only data that Copilot can inspect:

| URI | Description |
|-----|-------------|
| `visio://documents` | List all open documents |
| `visio://documents/{path}` | Detail for a specific open document |
| `visio://templates` | Available Visio template files |
| `visio://stencils` | Available stencil files and masters |

## Prompts

Pre-built prompt templates for common diagram workflows:

| Prompt | Description |
|--------|-------------|
| `flowchart_builder` | Guided flowchart creation from a list of steps |
| `org_chart_builder` | Organization chart from indented hierarchy |
| `network_diagram` | Network topology from device/connection lists |

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Microsoft Visio is not installed" | Install Visio desktop (not web). The server checks `HKCR\Visio.Application` in the registry. |
| Server starts but Copilot doesn't see tools | Restart Copilot CLI after editing the MCP config. Configs are read at session start. |
| "Failed to initialize Visio" | Close any existing Visio instances and retry. COM can conflict with already-running copies. |
| File operations target wrong document | All tools accept `page_name` or `page_index` to target specific pages — use these when working with multiple pages. |
| `connect_shapes` fails | Ensure Visio's built-in stencils are present at the default install path. The connector tool needs `CONNEC_U.VSSX`. |
