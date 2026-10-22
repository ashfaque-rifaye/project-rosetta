"""Runtime configuration, all from environment so the same code runs locally and in Lambda."""
import os

REGION = os.environ.get("AWS_REGION", "us-east-1")
# Multimodal model for Scout / Judge / Keeper. Amazon Nova Pro is available on our account today;
# swap to a Claude model id here once Bedrock model access allows it.
MODEL_ID = os.environ.get("ROSETTA_MODEL_ID", "us.amazon.nova-pro-v1:0")

# Which models the crew uses: "vertex" (Gemini on Vertex AI, today's default to save cost) or "bedrock" (the ids
# above). Only the AI calls move; storage, API, events and alerts stay on AWS either way. See gemini.py.
AI_PROVIDER = os.environ.get("ROSETTA_AI_PROVIDER", "vertex")
GCP_PROJECT = os.environ.get("GOOGLE_CLOUD_PROJECT", "tesselle-demandfoundry")
GEMINI_LOCATION = os.environ.get("ROSETTA_GEMINI_LOCATION", "global")
# Tesselle's fast model with thinking off: the Tutor marks a sheet in ~14 s (gemini-3.8-flash took ~60 s, mostly
# writing out its working). "off" = thinking budget 0; otherwise a thinking level for models that support one.
GEMINI_MODEL = os.environ.get("ROSETTA_GEMINI_MODEL", "gemini-2.5-flash")
GEMINI_THINKING = os.environ.get("ROSETTA_GEMINI_THINKING", "off")
GEMINI_TTS_MODEL = os.environ.get("ROSETTA_TTS_MODEL", "gemini-2.5-flash-tts")
KEEPER_VOICE = os.environ.get("ROSETTA_KEEPER_VOICE", "Sulafat")
KEEPER_STYLE = "Say this like a warm, gently magical storyteller talking to a child, unhurried and kind"
BUCKET = os.environ.get("ROSETTA_BUCKET", "")
TABLE = os.environ.get("ROSETTA_TABLE", "")
# Judge confidence below this asks the parent instead of auto-approving.
AUTO_APPROVE_CONFIDENCE = float(os.environ.get("ROSETTA_AUTO_APPROVE", "0.75"))

# The Lost Things the TV can draw (character portraits bundled in the TV app).
CHARACTERS = {
    "sock": "socks, laundry, small clothes",
    "jacket": "jackets, hoodies, shirts, big clothes",
    "sneakers": "shoes, sneakers, slippers",
    "books": "books, notebooks, papers, magazines",
    "dino": "toy figures, action figures, toy animals, cars",
    "bricks": "building bricks, blocks, small toy pieces, puzzles",
    "teddy": "plush and soft toys, pillows, blankets",
    "mug": "cups, mugs, plates, bowls, bottles, food wrappers",
}
