# Feasibility results

| # | Test | Result | Date |
|---|---|---|---|
| 1 | Compose-for-TV app on Android TV API 30 emulator, D-pad focus | ✅ Pass | 2026-10-18 |
| 2 | Media3: video + separate dub audio (MergingMediaSource) + 3 side-loaded WebVTT, D-pad switching | ✅ Pass | 2026-10-18 |
| 3 | Transcribe batch with automatic language ID + WebVTT output | ✅ Pass (synthetic speech) | 2026-10-19 |
| 4 | Bedrock: timed cues → translations + Rosetta Notes + episode metadata (structured) | ⚠️ Pipeline passes; **Claude blocked by account billing**; Nova Pro quality not good enough for Tamil | 2026-10-19 |

## Test 3: Transcribe (`pipeline/spikes/test3_transcribe.py`)
- Input: ~35 s Hindi narration synthesized by **Polly (Kajal, neural, hi-IN)**, uploaded to a private dev bucket.
- `IdentifyLanguage` over `ta-IN, hi-IN, ml-IN, en-IN` → **hi-IN, score 1.00**. Job completed in **18 s**.
- Transcript is near-verbatim. Two slips: one dropped word, and "तभी" heard as "कभी".
- `Subtitles: vtt` returns **WebVTT with timings directly**, so no separate segmentation step is needed.
- **Caveat:** synthetic, studio-clean speech. We still need one real elder recording (Tamil, phone mic) before the demo.

## Test 4: Bedrock (`pipeline/spikes/test4_bedrock.py [model-id]`)
- Converse API with a forced tool (`publish_episode`) JSON schema gives deterministic structure: title, synopsis, chapters, per-cue `{en, ta}`, and notes `{cue_id, term, explanation}`.
- Writes `subs_en.vtt` / `subs_ta.vtt` that keep Transcribe's cue timings, ready for the TV player.

### Model access on this account (us-east-1)
| Model | Status |
|---|---|
| Claude Sonnet 4.5/4.6, Haiku 4.5, Opus 4.5/4.6 | ❌ `INVALID_PAYMENT_INSTRUMENT`: Marketplace subscription can't complete. (Opus answered one probe, then was refused minutes later.) |
| Claude 5.x family (Sonnet 5.5, Haiku 5.5, Opus 5/5.5, Fable) | ❌ "not available for this account" (access not granted) |
| Amazon Nova Pro / Lite | ✅ Works (first-party, no Marketplace) |

### Nova Pro output quality (Hindi → English / Tamil)
- Runtime: **8.6 s**, 1,069 in / 871 out tokens for 11 cues.
- English: good, natural, keeps "Amma"/"Thatha"/"kolam" per instructions.
- **Tamil: meaning errors that would embarrass the product in front of a grandparent:**
  - "ants" → தேனீ (*bees*)
  - "Maami" → மாமியார் (*mother-in-law*), with the note "sister-in-law". In Tamil homes "Maami" is an aunt / respected older woman.
  - "soaked (by rain)" → ஊறவைத்து (*steeped, like lentils*)
  - didn't fix the ASR slip कभी→तभी ("Remember when…")
- Rosetta Notes: correct but thin (4 notes, generic definitions).

### Decision
The pipeline is model-agnostic (the model ID is one argument). **Translation and Rosetta Notes need Claude.** Action: fix the AWS account's payment method (Billing → Payment preferences), then re-run test 4 with `us.anthropic.claude-sonnet-4-6`, or a 5.x model once access is granted. Nova Lite stays a cheap option for non-linguistic steps (titles, chapter names).
