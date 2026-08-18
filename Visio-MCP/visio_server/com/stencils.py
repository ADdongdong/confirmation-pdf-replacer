"""Shared stencil helpers — open built-in stencils, retrieve master shapes."""

import os

from visio_server.com.lifecycle import get_visio_app
from visio_server.errors import StencilError


def _stencil_search_dirs() -> list[str]:
    """Return directories where Visio built-in stencils may be found."""
    dirs = []
    for env_var in ("ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(env_var, "")
        if base:
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16\Visio Content\1033"))
            dirs.append(os.path.join(base, r"Microsoft Office\root\Office16"))
    return [d for d in dirs if os.path.isdir(d)]


def ensure_stencil_open(stencil_filename: str):
    """Open a built-in stencil (docked) if not already open and return the stencil document.

    Args:
        stencil_filename: Stencil file name (e.g., ``"BASIC_U.VSSX"``).

    Returns:
        The Visio stencil Document COM object.

    Raises:
        StencilError: If the stencil cannot be found or opened.
    """
    app = get_visio_app()
    target = stencil_filename.upper()

    # Check if already open
    for doc in app.Documents:
        try:
            if doc.Name.upper() == target:
                return doc
        except Exception:
            continue

    # Try known directories
    for d in _stencil_search_dirs():
        path = os.path.join(d, stencil_filename)
        if os.path.exists(path):
            try:
                return app.Documents.OpenEx(path, 64)  # visOpenDocked
            except Exception:
                continue

    # Last resort: let Visio search its own paths
    try:
        return app.Documents.OpenEx(stencil_filename, 64)
    except Exception as exc:
        raise StencilError(
            f"Could not open stencil '{stencil_filename}'. "
            f"Ensure Visio is fully installed. Detail: {exc}"
        )


def get_master(stencil_filename: str, master_name: str):
    """Open a stencil (if needed) and return a master shape by universal name.

    Args:
        stencil_filename: Stencil file name (e.g., ``"BASIC_U.VSSX"``).
        master_name: Universal master name (e.g., ``"Diamond"``).

    Returns:
        The Visio Master COM object.

    Raises:
        StencilError: If the stencil or master cannot be found.
    """
    stencil_doc = ensure_stencil_open(stencil_filename)
    try:
        return stencil_doc.Masters.ItemU(master_name)
    except Exception as exc:
        raise StencilError(
            f"Master '{master_name}' not found in stencil '{stencil_filename}': {exc}"
        )
