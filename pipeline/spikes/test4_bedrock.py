"""Feasibility test 4: timed transcript cues -> Bedrock (Claude) -> translations + Rosetta Notes + episode metadata.

Reads out/subtitles.vtt from test 3, forces structured output through a tool schema, and writes one
WebVTT file per target language that keeps the original cue timings.

Usage: python pipeline/spikes/test4_bedrock.py [model-id]
"""
import json, re, sys, time
from pathlib import Path
import boto3

REGION = "us-east-1"
MODEL = sys.argv[1] if len(sys.argv) > 1 else "us.anthropic.claude-opus-4-6-v1"
SOURCE_LANG = "Hindi"
TARGETS = {"en": "English", "ta": "Tamil"}
OUT = Path(__file__).parent / "out"

def parse_vtt(text: str):
    cues = []
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = [l for l in block.splitlines() if l.strip()]
        timing = next((l for l in lines if "-->" in l), None)
        if not timing:
            continue
        start, end = [t.strip() for t in timing.split("-->")]
        body = " ".join(lines[lines.index(timing) + 1:])
        cues.append({"id": len(cues) + 1, "start": start, "end": end, "text": body})
    return cues

def write_vtt(path: Path, cues, texts):
    out = ["WEBVTT", ""]
    for cue in cues:
        out += [f"{cue['start']} --> {cue['end']}", texts[cue["id"]], ""]
    path.write_text("\n".join(out), encoding="utf-8")

TOOL = {
    "toolSpec": {
        "name": "publish_episode",
        "description": "Publish a translated family-story episode.",
        "inputSchema": {"json": {
            "type": "object",
            "required": ["title", "synopsis", "chapters", "cues", "notes"],
            "properties": {
                "title": {"type": "string", "description": "Warm episode title in English, max 6 words."},
                "synopsis": {"type": "string", "description": "One or two sentences in English for the TV card."},
                "chapters": {"type": "array", "items": {
                    "type": "object", "required": ["start_cue", "title"],
                    "properties": {"start_cue": {"type": "integer"}, "title": {"type": "string"}}}},
                "cues": {"type": "array", "items": {
                    "type": "object", "required": ["id"] + list(TARGETS),
                    "properties": {"id": {"type": "integer"},
                                   **{code: {"type": "string"} for code in TARGETS}}}},
                "notes": {"type": "array", "description": "Rosetta Notes: culture/kinship/idiom explanations.",
                          "items": {"type": "object", "required": ["cue_id", "term", "explanation"],
                                    "properties": {"cue_id": {"type": "integer"},
                                                   "term": {"type": "string", "description": "As it appears in the original."},
                                                   "explanation": {"type": "string", "description": "Max 25 words, English, for a grandchild abroad."}}}},
            },
        }},
    }
}

SYSTEM = (
    "You translate elders' spoken family stories for grandchildren who don't speak the language. "
    f"Translate each cue from {SOURCE_LANG} into: " + ", ".join(TARGETS.values()) + ". "
    "Keep the speaker's warmth and spoken register; keep each translation about as long as the original so it fits "
    "the same on-screen time. Keep kinship terms and cultural words (e.g. Amma, Thatha, kolam) untranslated in the "
    "English text and explain them in notes instead. Fix obvious speech-recognition slips silently using context. "
    "Only add notes a grandchild raised abroad would genuinely need."
)

def main():
    cues = parse_vtt((OUT / "subtitles.vtt").read_text(encoding="utf-8"))
    print(f"model={MODEL} cues={len(cues)}")
    rt = boto3.client("bedrock-runtime", region_name=REGION)
    t0 = time.time()
    r = rt.converse(
        modelId=MODEL,
        system=[{"text": SYSTEM}],
        messages=[{"role": "user", "content": [{"text": json.dumps(
            [{"id": c["id"], "text": c["text"]} for c in cues], ensure_ascii=False)}]}],
        toolConfig={"tools": [TOOL], "toolChoice": {"tool": {"name": "publish_episode"}}},
        inferenceConfig={"maxTokens": 4000},
    )
    elapsed = time.time() - t0
    episode = next(b["toolUse"]["input"] for b in r["output"]["message"]["content"] if "toolUse" in b)
    usage = r["usage"]
    print(f"bedrock: {elapsed:.1f}s  tokens in={usage['inputTokens']} out={usage['outputTokens']}")

    (OUT / "episode.json").write_text(json.dumps(episode, ensure_ascii=False, indent=2), encoding="utf-8")
    by_id = {c["id"]: c for c in episode["cues"]}
    missing = [c["id"] for c in cues if c["id"] not in by_id]
    print("all cues translated:", not missing, missing or "")
    for code in TARGETS:
        write_vtt(OUT / f"subs_{code}.vtt", cues, {i: by_id[i][code] for i in by_id})

    print("\nTITLE:", episode["title"]); print("SYNOPSIS:", episode["synopsis"])
    print("CHAPTERS:", [(c["start_cue"], c["title"]) for c in episode["chapters"]])
    print("\nCUES (hi -> en | ta):")
    for c in cues:
        print(f"  [{c['id']}] {c['text']}\n       en: {by_id[c['id']]['en']}\n       ta: {by_id[c['id']]['ta']}")
    print("\nROSETTA NOTES:")
    for n in episode["notes"]:
        print(f"  cue {n['cue_id']}: {n['term']} — {n['explanation']}")

if __name__ == "__main__":
    main()
