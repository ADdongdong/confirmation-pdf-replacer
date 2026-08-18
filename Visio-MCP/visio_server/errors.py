"""Custom exceptions for the Visio MCP Server."""


class VisioError(Exception):
    """Base exception for all Visio MCP errors."""


class COMError(VisioError):
    """COM automation failure — Visio process crashed, unavailable, or connection lost."""


class DocumentNotFoundError(VisioError):
    """Requested document is not open or does not exist on disk."""


class ShapeNotFoundError(VisioError):
    """Shape with the given ID was not found on the page."""


class PageNotFoundError(VisioError):
    """Page with the given name or index was not found in the document."""


class StencilError(VisioError):
    """Stencil could not be opened or master shape not found."""
