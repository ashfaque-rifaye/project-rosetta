"""The Keeper's voice on the server: every line the TV speaks gets an Amazon Polly clip (see speech.speak).

Lines that repeat (quest openers, "Tutor is reading your work…", practice results) are voiced once and kept under
keeper-voice/ by a hash of the text, so after the first time they cost nothing and add no delay. A clip is stored with
the exact line it speaks ({"line", "key"} in quest.keeperVoice), so the TV never plays an old clip for a new line.
"""
from __future__ import annotations

import hashlib
import logging

from . import config, speech, store

log = logging.getLogger(__name__)


def fixed(line: str) -> str | None:
    """S3 key of the cached clip for a repeating line (made on first use), or None if voice is off or failed."""
    if not line:
        return None
    key = f"keeper-voice/{config.KEEPER_VOICE.lower()}-{hashlib.sha1(line.encode()).hexdigest()[:16]}.wav"
    try:
        if not store.voice_exists(key):
            store.put_voice(key, speech.speak(line))
        return key
    except Exception:  # the TV falls back to its own voice
        log.exception("keeper voice failed")
        return None


def set_line(q: dict, line: str, key: str | None = None) -> None:
    """Give the quest a new Keeper line, with its clip (a cached one unless [key] is passed)."""
    key = key or fixed(line)
    q["keeperLine"] = line
    if key:
        q["keeperVoice"] = {"line": line, "key": key}
    else:
        q.pop("keeperVoice", None)
