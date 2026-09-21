# Caicai Maid Live2D Accessory Lab

Android/Cubism test harness for a high-fidelity maid Live2D model with three selected accessories
from Sen: ahoge, paired fish ear fins, and fish tail.

## v0.1.0

- Imports one private model ZIP described by `accessory-lab.json`.
- Keeps the maid model, its original outfit, and its authored motion rig intact.
- Uses Sen's compiled rig only as an accessory dynamics donor; only `Part113`, `Part239`, and the
  ahoge hierarchy are rendered.
- Excludes `Part115` (rabbit-ear bow) from rendering and lists it in the exported dependency report.
- Layer order: tail → maid through twin tails → ear fins → maid front layers → ahoge.
- Mirrors compatible tracking parameters and applies per-frame anchor-delta correction.
- Calibrates X/Y/scale for every accessory, plus symmetric fin rotation, spacing, and pair-axis
  rotation with numeric feedback.
- Provides a fully static diagnostic baseline and repeatable head/body/automatic motion sweeps.
- Exposes the owner's approved expression, action, outfit, shrink, and click presets. Black socks
  are the default; the initial sock button therefore reads `白袜`.
- Forces multiply colour `#9E9EB2` on `ArtMesh122` and `ArtMesh149`.
- Exports calibration, runtime drawables, texture slots, masks, and `Part115` status as JSON.

Purchased models, textures, and generated test packages are intentionally not committed.

## Private ZIP layout

```text
accessory-lab.json
caicai/maid.model3.json
caicai/...
sen-accessory/SenAccessory.model3.json
sen-accessory/SenAccessory.moc3
sen-accessory/SenAccessory.physics3.json
sen-accessory/SenAccessory.2048/texture_06.png
sen-accessory/SenAccessory.2048/texture_16.png
sen-accessory/SenAccessory.2048/texture_19.png
```

The current donor uses the full compiled Sen `.moc3` but loads only texture slots required by the
three selected components and their masks. A later reduced `.moc3` exported from the editable
`.cmo3` can replace it without changing app code or the manifest paths.

## Build

The repository uses the official Live2D Cubism Java Framework submodule and redistributable Cubism
Core Android AAR. GitHub Actions applies the no-mipmap and drawable-filter patches and uploads a
debug APK artifact.
