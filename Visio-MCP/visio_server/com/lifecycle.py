"""Visio COM application lifecycle — singleton init, cleanup, health checks."""

import gc
import sys
import time
import atexit
import logging
import winreg
import pythoncom
import win32com.client

from visio_server.errors import COMError

logger = logging.getLogger("visio_server.com")

# Singleton state
_visio_app = None
_open_documents: dict = {}


def check_visio_installed() -> bool:
    """Check if Visio is installed by probing the COM registry key."""
    try:
        winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, r"Visio.Application")
        return True
    except OSError:
        return False


def _is_app_alive() -> bool:
    """Return True if the cached _visio_app COM reference is still usable."""
    global _visio_app
    if _visio_app is None:
        return False
    try:
        _ = _visio_app.Version
        return True
    except Exception:
        return False


def _reset_com_state():
    """Release all COM references and force garbage collection.

    This is the nuclear option: it drops every cached COM proxy so that
    a subsequent ``get_visio_app()`` call can create a fresh connection
    even if the previous Visio process was killed externally.
    """
    global _visio_app, _open_documents

    # Release document references first
    for path in list(_open_documents):
        try:
            doc = _open_documents.pop(path)
            del doc
        except Exception:
            pass
    _open_documents.clear()

    # Release the application reference
    if _visio_app is not None:
        try:
            _visio_app.Quit()
        except Exception:
            pass
        try:
            del _visio_app
        except Exception:
            pass
        _visio_app = None

    # Force-release lingering COM proxies
    gc.collect()

    # Re-initialize COM on this thread to clear any stale apartment state
    try:
        pythoncom.CoUninitialize()
    except Exception:
        pass
    pythoncom.CoInitialize()

    logger.info("COM state fully reset")


def get_visio_app():
    """Return the singleton Visio Application COM object.

    On first call, initializes a new Visio instance.  On subsequent calls,
    probes the cached reference; if the Visio process has died, performs a
    full COM reset and re-initializes transparently.

    Tries Dispatch → dynamic.Dispatch → DispatchEx as fallbacks.
    """
    global _visio_app

    if _visio_app is not None:
        if _is_app_alive():
            return _visio_app
        # Visio died externally — full cleanup before re-init
        logger.warning("Visio COM reference is stale — resetting and reconnecting")
        _reset_com_state()

    errors = []
    for factory in (
        win32com.client.Dispatch,
        win32com.client.dynamic.Dispatch,
        win32com.client.DispatchEx,
    ):
        try:
            logger.info("Trying %s to launch Visio...", factory.__qualname__)
            _visio_app = factory("Visio.Application")
            time.sleep(1)  # Allow COM process to stabilize
            _visio_app.Visible = True
            logger.info("Visio initialized via %s", factory.__qualname__)
            return _visio_app
        except Exception as exc:
            errors.append(f"{factory.__qualname__}: {exc}")
            logger.warning("Factory %s failed: %s", factory.__qualname__, exc)

    raise COMError(
        "Failed to initialize Visio after multiple attempts:\n"
        + "\n".join(errors)
    )


def get_open_documents() -> dict:
    """Return the mutable open-documents registry (path → COM doc)."""
    return _open_documents


def close_visio_app():
    """Shut down Visio cleanly — release all COM references."""
    _reset_com_state()


atexit.register(close_visio_app)
