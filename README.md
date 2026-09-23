# Caicai Maid Live2D Accessory Lab

Android/Cubism test harness for a high-fidelity maid Live2D model with three selected accessories
from Sen: ahoge, paired fish ear fins, and fish tail.

## v0.1.9

- Rolls back the unsuccessful v0.1.8 horizontal reflection and the v0.1.7 rigid correction for
  both head accessories. Inspection of the original model metadata confirmed that their direction
  anchors were Ruby's head ornament and Sen's maid headband, not stable rigid head landmarks.
- Restores the v0.1.6 hybrid attachment strategy: ahoge and native paired ear fins follow Ruby via
  the compatible Ruby-to-Sen parameter drive, while the confirmed tail keeps its independent
  two-point body attachment.
- Preserves all confirmed scale, position and ear-pair rotation values, the three-item selector,
  separate drawable filters, native ear twitch, ahoge dynamics and tail swing.

## v0.1.8

- Superseded by v0.1.9 after on-device testing showed that reflecting the invalid head frame did
  not correct its direction.
- Leaves the tail on the direct body-frame path. Its origin/direction parts, matrix order, local
  swing, scale `1.00`, and confirmed position are unchanged.
- Keeps the three filters and attachment calls independent; no combined ahoge/ear-fin group and no
  manual ear mirror are reintroduced.

## v0.1.7

- Uses the confirmed v0.1.6 device calibration as the neutral bind pose: ahoge
  `0.88/-0.03/-0.05`, native ear pair `1.24/-0.01/-0.29/+5°`, and tail
  `1.00/+0.02/-0.24`, with global Y `+0.20`.
- Attaches ahoge and ear fins through two independent filtered draw passes and two independent
  projection corrections. They follow the maid head frame without creating a combined drawable
  group; Sen's ahoge bend and native left/right ear twitch remain local to their own meshes.
- Keeps the already confirmed tail body attachment unchanged.
- Removes `GLOBAL` from the previous/next accessory selector. The cycle now contains exactly
  tail, ahoge, and ear fins, so the former apparent "ahoge + ear fins" fourth item is gone.

## v0.1.6

- Removes the App-created right-ear mirror and the model-X left/right split. The complete authored
  Sen ear rig is now discovered from `ParamL_angle`, `ParamR_angle`, and `ParamR_angle2`, then drawn
  once so both sides keep their native independent keyforms.
- Keeps the ahoge on its own six confirmed drawables and lets it follow the maid through the shared
  head parameters instead of applying the ear attachment matrix to it.
- Resets only the obsolete ear mirror rotation/spacing calibration; the confirmed ahoge, ear-fin,
  and tail scale/position values remain the defaults.
- Applies whole-stage transforms after projection with the same matrix order for the maid and every
  filtered accessory pass, preventing zoom/pan from changing their relative spacing.

## v0.1.5

- Applies stage, attachment, mirrored-ear and ear-rotation transforms after each model's own
  Cubism layout matrix, preventing head accessories from amplifying translation while zooming.
- Scales the complete composition around the maid model's visual center instead of the Live2D
  canvas origin, so repeated whole-stage zoom no longer walks the character down and right.
- Keeps the confirmed bind pose and the successful v0.1.4 tail attachment unchanged at 1.0x.

## v0.1.4

- Attaches ahoge and ear fins to a two-point head frame, and the tail to a separate two-point
  waist/body frame.
- Replaces the donor model's incompatible rigid movement with the maid model's translation,
  rotation and scale delta while preserving accessory-local mesh physics.
- Uses the confirmed v0.1.2 calibration as the neutral bind pose; stage zoom and translation are
  still shared by the maid and all accessories.
- Keeps the v0.1.3 manual and low-frequency autonomous ear-fin double twitch.

## v0.1.0

- Imports one private model ZIP described by `accessory-lab.json`.
- Keeps the maid model, its original outfit, and its authored motion rig intact.
- Uses Sen's compiled rig only as an accessory dynamics donor; only `Part113`, `Part239`, and the
  ahoge hierarchy are rendered.
- Excludes `Part115` (rabbit-ear bow) from rendering and lists it in the exported dependency report.
- Layer order: tail → maid through twin tails → ear fins → maid front layers → ahoge.
- Mirrors compatible tracking parameters; only the tail retains per-frame two-point anchor
  correction because the two head accessories use Sen's native head rig.
- Calibrates X/Y/scale for every accessory, plus whole-pair ear-fin rotation with numeric feedback.
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
