"""Runtime configuration, all from environment so the same code runs locally and in Lambda."""
import os

REGION = os.environ.get("AWS_REGION", "us-east-1")
# Multimodal model for Scout / Judge / Keeper. Amazon Nova Pro is available on our account today;
# swap to a Claude model id here once Bedrock model access allows it.
MODEL_ID = os.environ.get("ROSETTA_MODEL_ID", "us.amazon.nova-pro-v1:0")
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
