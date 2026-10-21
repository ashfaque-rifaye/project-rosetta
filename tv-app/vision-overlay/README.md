# vision-overlay

**Draw AI vision results on Android TV and Fire TV with Jetpack Compose.**

Multimodal models (Amazon Nova, Claude, Gemini…) can now return bounding boxes for what they see. Showing those
results on a TV, readable from the sofa, animated and friendly to D-pad UIs, is boilerplate every app rewrites.
`vision-overlay` is one composable:

- boxes pop in **one by one** (staggered spring) so viewers follow what the model found
- **unresolved** detections can **pulse** for attention; **resolved** ones animate out and their badge floats away
- an optional **badge** (icon, character, avatar) perches above each box
- normalized `0..1` coordinates (divide 0–1000 grounding by 1000)
- no focus stealing: it's purely visual, so your D-pad focus stays on your controls

Extracted from [Project Rosetta](../../README.md), a Fire TV app where an AI crew turns a photo of a child's room
into "Lost Things" that need to go home.

```kotlin
Box(Modifier.size(588.dp, 328.dp)) {
    Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    VisionOverlay(
        detections = items.map { Detection(it.id, it.l, it.t, it.r, it.b, resolved = it.putAway) },
        modifier = Modifier.fillMaxSize(),
        pulseUnresolved = true,
    ) { d -> Image(iconFor(d.id), null, contentScale = ContentScale.Crop) }
}
```

## Install
Copy the `vision-overlay` module into your project (`include(":vision-overlay")`) and add
`implementation(project(":vision-overlay"))`. Requires Compose (BOM 2025.06+), minSdk 21.

## License
MIT. See [LICENSE](LICENSE).
