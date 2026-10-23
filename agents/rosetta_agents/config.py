"""Runtime configuration, all from environment so the same code runs locally and in Lambda."""
import os

REGION = os.environ.get("AWS_REGION", "us-east-1")
# Every agent runs on Amazon Bedrock. Amazon Nova 2 Lite: small, fast and low-cost, reads photos, and calls tools. A
# little reasoning ("low") is on for marking homework, tracing code and judging before/after photos; quick lines
# (the Keeper, summaries) skip it.
MODEL_ID = os.environ.get("ROSETTA_MODEL_ID", "us.amazon.nova-2-lite-v1:0")
REASONING = os.environ.get("ROSETTA_REASONING", "low")
# Quest Master runs on the global inference profile, so it draws on its own request quota, not the specialists'.
MASTER_MODEL_ID = os.environ.get("ROSETTA_MASTER_MODEL_ID", MODEL_ID.replace("us.", "global.", 1))
# When a busy minute uses up the request quota, a specialist call overflows to Amazon Nova Lite (higher quota).
OVERFLOW_MODEL_ID = os.environ.get("ROSETTA_OVERFLOW_MODEL_ID", "us.amazon.nova-lite-v1:0")
# The Keeper's voice: Amazon Polly (generative engine), warm Indian English.
KEEPER_VOICE = os.environ.get("ROSETTA_KEEPER_VOICE", "Kajal")
KEEPER_LANGUAGE = os.environ.get("ROSETTA_KEEPER_LANGUAGE", "en-IN")
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
