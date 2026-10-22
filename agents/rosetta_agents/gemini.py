"""Single entry point to Gemini on Vertex AI (the default AI provider; Bedrock stays behind ROSETTA_AI_PROVIDER).

Same rules as Project Tesselle's gemini.py:
  * the client is always the Vertex AI backend (aiplatform.googleapis.com), never AI Studio, and never an API key;
  * every request carries labels (app=rosetta, purpose=<agent>) so usage is visible in the project's billing;
  * identity is Application Default Credentials. In Lambda that is a Workload Identity Federation config file
    (GOOGLE_APPLICATION_CREDENTIALS, no secrets in it): Google trusts the function's AWS role, so no key exists anywhere.
"""
from __future__ import annotations

import functools
import io
import threading
import time
import wave
from typing import Any

from google import genai
from google.genai import types

from . import config


def client_args() -> dict[str, Any]:
    return {
        "vertexai": True, "project": config.GCP_PROJECT, "location": config.GEMINI_LOCATION,
        "http_options": types.HttpOptions(
            timeout=60_000,
            retry_options=types.HttpRetryOptions(attempts=3, initial_delay=1.0, max_delay=8.0, http_status_codes=[429, 500, 503, 504]),
        ),
    }


_lock = threading.Lock()


def client() -> genai.Client:
    # One shared client, made under a lock: a second, uncached client made in a race is garbage-collected (and its
    # HTTP connection closed) mid-request. Strands can run tools on several threads at once.
    with _lock:
        return _client()


@functools.lru_cache(maxsize=1)
def _client() -> genai.Client:
    c = genai.Client(**client_args())
    api = c._api_client
    base_url = (api._http_options.base_url or "") if api._http_options else ""
    if not api.vertexai or "aiplatform.googleapis.com" not in base_url:
        raise RuntimeError(f"Gemini client is not on Vertex AI (base_url={base_url!r}); refusing to start.")
    return c


def thinking() -> types.ThinkingConfig:
    if config.GEMINI_THINKING == "off":
        return types.ThinkingConfig(thinking_budget=0)
    return types.ThinkingConfig(thinking_level=config.GEMINI_THINKING)


def labels(purpose: str) -> dict[str, str]:
    return {"app": "rosetta", "purpose": purpose.replace("_", "-").lower()[:63]}


# ---------------------------------------------------------------- structured output (the crew's tool schemas)
# The prompts and the rest of the code use boxes as [x1, y1, x2, y2] on a 0-1000 grid. Gemini is trained to detect in
# [ymin, xmin, ymax, xmax] on the same grid, so ask for that and convert back here.
GEMINI_BOX = ("[ymin, xmin, ymax, xmax] in 0-1000 coordinates of the whole photo. Tight: only the printed or written "
              "content itself, never the empty margin, the table or the background around it")


def _gemini_schema(node: Any) -> Any:
    if isinstance(node, dict):
        out = {k: _gemini_schema(v) for k, v in node.items()}
        if isinstance(out.get("properties"), dict) and "box" in out["properties"]:
            box = dict(out["properties"]["box"])
            box["description"] = GEMINI_BOX + box.get("description", "").split("coordinates", 1)[-1]
            out["properties"] = {**out["properties"], "box": box}
        return out
    if isinstance(node, list):
        return [_gemini_schema(v) for v in node]
    return node


def _boxes_back(node: Any) -> Any:
    if isinstance(node, dict):
        return {k: ([v[1], v[0], v[3], v[2]] if k == "box" and isinstance(v, list) and len(v) == 4 else _boxes_back(v))
                for k, v in node.items()}
    if isinstance(node, list):
        return [_boxes_back(v) for v in node]
    return node


def _part(block: dict) -> types.Part:
    """One Bedrock Converse content block ({"text"} or {"image"}) as a Gemini part."""
    if "text" in block:
        return types.Part.from_text(text=block["text"])
    img = block["image"]
    return types.Part.from_bytes(data=img["source"]["bytes"], mime_type=f"image/{img['format']}")


def structured(content: list[dict], tool: dict, system: str, *, purpose: str) -> dict[str, Any]:
    """Gemini with a JSON response schema taken from a Bedrock tool spec, so every agent prompt works on both providers."""
    spec = tool["toolSpec"]
    resp = client().models.generate_content(
        model=config.GEMINI_MODEL,
        contents=[types.Content(role="user", parts=[_part(b) for b in content])],
        config=types.GenerateContentConfig(
            # The bare agent prompt: adding the tool description here made Gemini anchor every box at the page margin.
            system_instruction=system,
            response_mime_type="application/json",
            response_json_schema=_gemini_schema(spec["inputSchema"]["json"]),
            temperature=0.2,
            thinking_config=thinking(),
            labels=labels(purpose),
        ),
    )
    if resp.parsed is not None and isinstance(resp.parsed, dict):
        return _boxes_back(resp.parsed)
    import json
    return _boxes_back(json.loads(resp.text))


# ---------------------------------------------------------------- the Keeper's voice
def speak(text: str, *, purpose: str = "keeper-voice") -> bytes:
    """Gemini TTS -> WAV bytes (24 kHz mono 16-bit), in the Keeper's warm storyteller voice."""
    started = time.perf_counter()
    resp = client().models.generate_content(
        model=config.GEMINI_TTS_MODEL,
        contents=f"{config.KEEPER_STYLE}: {text}",
        config=types.GenerateContentConfig(
            response_modalities=["AUDIO"],
            labels=labels(purpose),
            speech_config=types.SpeechConfig(voice_config=types.VoiceConfig(
                prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=config.KEEPER_VOICE))),
        ),
    )
    pcm = resp.candidates[0].content.parts[0].inline_data.data
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(24000)
        w.writeframes(pcm)
    print(f"keeper voice: {len(pcm) // 48000}s of audio in {time.perf_counter() - started:.1f}s")
    return buf.getvalue()
