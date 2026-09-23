# Caicai Maid Live2D Accessory Lab

Android/Cubism test harness for a high-fidelity maid Live2D model with three selected accessories
from Sen: ahoge, paired fish ear fins, and fish tail.

## v0.1.22

- Uses the continuous signed maid head turn for the ear pair's shared screen shift. The
  previous switch to face-mesh parallax at a hard threshold could jump during head sweeps.
  Keeps the proven 7% shift, outer-span constraint, native ear motion, and stage gestures.
- Leaves the manually picked hair anchor and ahoge secondary bend intact. The v0.1.20
  neutral-pose diagnostic cannot distinguish the visual hair drift reported on v0.1.21.

## v0.1.21

- Keeps the manually selected ahoge root on the maid's top hair unchanged.
- Reverses only the pair's shared horizontal shift in response to device feedback that the
  previous version moved both fins farther in the wrong direction. The conspicuous 7% test
  amplitude and existing actual outer-span cap remain, and the diagnostic now records the
  rendered screen X before and after correction for both ears.
- Follows Sen's tested pinch and drag behavior: zoom about the actual two-finger focus, clamp
  stage translation, and reset the one-finger drag baseline after either finger is lifted.
  Both models share a screen-centered stage scale rather than a pose-dependent maid pivot.

## v0.1.20

- On the phone, tap “点选呆毛接入的头发位置”, then tap the actual hair connection in the neutral pose.
  The entire ahoge moves to that point immediately. The pick is saved as a triangle and
  barycentric weights on the maid top-hair mesh, so it follows that same point each frame.
  A missed hair tap is rejected, and the pick can be repeated. Importing another model clears it.
- Starts from the v0.1.16 face-following motion, translates the complete six-mesh Sen ahoge so
  its root sits exactly on the picked hair point, and bends the meshes smoothly away from the
  root using the maid head turn and bounded velocity. Sen’s direction anchor is kept local and
  is no longer mapped onto the maid head. Before the first pick, v0.1.16 ahoge motion is shown.
- Makes the ear pair's same-direction test shift 7% of neutral pair width at full turn (previously
  1.8%), while retaining the measured outer-span cap and comparison toggle. The full rendered
  shift is recorded in the exported diagnostic. Visual accuracy still requires a device test.

## v0.1.19

- Removes v0.1.18's captured near-root mesh overwrite, which froze the lower ahoge and could
  expose a cut. Uses Sen's hand-picked root and upward direction points to map all six native
  animated meshes into one maid hair/head frame. The native bend and tip motion remain live.
- Preserves the measured ear outer-span cap, then adds a small shared horizontal shift in the
  opposite screen direction of the head turn. A screen-right turn moves both fins slightly left;
  the reverse turn moves both fins slightly right. The shift reaches 1.8% of neutral ear span at
  full turn, leaving the span and the existing small-pose/zoom behaviour intact.
- Sets the confirmed layer offset to `-1` for both ears on fresh imports and reset. Existing
  user-saved calibration values are respected. The comparison switch still selects v0.1.16.
- Adds a signed screen-turn probe, pair shift and two-point direction error to the diagnostic so
  the next phone report can verify the applied sign and tune the small amount if necessary.

## v0.1.18

- Adds a one-tap comparison between the accepted v0.1.16 head path and an experimental actual-mesh
  correction, while keeping the accepted neutral calibration, independent layers, small pose and
  tail untouched. The comparison can run during one continuous `左右大幅` motion.
- Measures both rendered ear-fin meshes after their individual pose transforms. When the actual
  outer span grows beyond the calibrated neutral span (narrowed by the maid's horizontal head turn), moves
  the two complete ear meshes inward by equal amounts. The native double twitch remains intact.
- Saves the ahoge's six near-root meshes at their bind pose. The new mode restores the first 12%
  to that shape at the moving root, blends into native motion by 45%, and maps the real Sen root
  onto the maid top-hair carrier with the already calibrated bind offset. The tip remains free.
- Exports live measurements and mode-specific maxima: before/allowed/after ear span, applied ear
  correction, root-to-hair gap, proximal vertex correction and missing-anchor counts. A zero
  correction on device can therefore be distinguished from a correction that had no visible effect.

## v0.1.17

- Preserves the accepted v0.1.16 head carriers, chibi scaling, layer positions and all saved
  calibration values. The verified tail path is unchanged.
- Dampens face-surface parallax at each ear root and caps only excessive pair expansion at 103% of
  the stable rigid-head spacing. Perspective narrowing remains allowed, so side turns can still
  move the far fin behind the head instead of turning the pair into one rigid ornament.
- Maps Sen's confirmed `ArtMesh151` root anchor to the maid head every frame, preserving the user's
  neutral bind offset and cancelling root movement produced inside the donor model.
- Keeps the nearest 10% of the six-mesh ahoge fully rigid, then smoothly raises secondary-motion
  weight toward the tip. Head velocity and angular velocity drive a bounded damped spring only on
  the flexible region; stage zoom/drag is explicitly excluded as a physics input.

## v0.1.16

- Keeps the user-confirmed ahoge and ear-fin draw layers, but completely separates those occlusion
  choices from motion attachment. Changing a layer can no longer silently change the carrier.
- Pins the ahoge and the two ear fins to three distinct points on the maid's original large face
  mesh. Local pin translation supplies side-aware perspective movement; one stable head triangle
  supplies rotation, avoiding the nearly static crown-hair carrier and the duplicated left-hair
  carrier diagnosed in v0.1.15.
- Limits inherited ear-fin scale and softens scale response so the maid's chibi/small action does
  not shrink the fins excessively. Sen's native two-pulse fin dynamics remain local and intact.
- Leaves the already verified tail body carrier and all saved scale/X/Y calibration untouched.

## v0.1.15

- Separates draw order from motion attachment for the ahoge. It is always drawn immediately in
  front of the maid headwear while its root follows the dedicated crown/top-hair skinning section.
- Replaces the overly broad recursive front-hair/back-hair groups with authored skinning/material
  sections: ponytails, side locks, loose strands, crown hair, and numbered source layers can be
  stepped independently. Equivalent colour/style branches of the same section remain merged.
- Starts a new calibration preference generation. Confirmed scale/X/Y values are unchanged; the
  old v0.1.14 ahoge offset `-1` is discarded and the correct headwear-front layer becomes zero.
- Extends diagnostics with each section's direct parent Part, render order, texture-atlas index and
  UV bounds so a remaining mismatch can be tied to one source material section precisely.

## v0.1.14

- Adds coarse logical-Part layer calibration for the ahoge and each ear fin. The controls say
  explicitly whether an accessory moves forward (less occlusion) or backward (more occlusion),
  and screen-left/right ears can occupy different slots.
- Groups the maid's hair colour variants into the same step instead of exposing individual
  ArtMeshes. Slots cover back hair, twin-tail/side hair, crown hair, face, front hair, hair shadow,
  side bows, and headwear.
- Splits the maid draw into runtime render-order ranges so accessories can be inserted at the
  selected Part boundary without reordering or duplicating the maid's own meshes.
- When a non-default slot is selected, the ahoge follows the adjacent Part behind the slot while
  each ear follows the adjacent Part in front of it. The zero slot deliberately preserves the
  accepted v0.1.13 headwear/bow carriers and all static scale/X/Y values.
- Exports every logical group, slot, resolved threshold, adjacent carrier Part, and fixed triangle
  in the position diagnostic. The tail remains on its verified body carrier and backmost layer.

## v0.1.13

- Splits Sen's authored ear-fin rig into screen-left and screen-right filtered draw passes without
  duplicating or mirroring meshes. The donor model still updates once, so its native two-pulse ear
  animation remains intact.
- Uses the maid metadata's `Part30` and `Part31` side bows as separate perspective-aware carriers.
  Their neutral model-space X positions decide screen side; editor-side left/right names are not
  trusted. Each ear inherits only its bow's geometry motion, never the bow's opacity.
- Preserves the confirmed v0.1.12 neutral ear placement exactly. Both side adjustments default to
  identity and inherit the existing pair scale/position/rotation; only motion after leaving the
  neutral pose differs.
- Adds numeric per-side X/Y/scale/rotation fine tuning. The accessory selector remains exactly
  tail, ahoge, and ear fins; a separate ear target button cycles pair, screen-left, and screen-right.
- Inserts the ear passes immediately before the two maid side-bow drawables, allowing the bows and
  later front layers to occlude the fins while hiding the maid headwear leaves the fins visible.
- Moves the ahoge carrier from the whole face to the maid headwear/top local mesh; the verified tail
  carrier is unchanged.

## v0.1.12

- Removes the maid-to-Sen rigid parameter copy completely. Identically named Live2D parameters in
  the two compiled rigs are not assumed to mean the same motion anymore.
- Uses the maid's rendered face/skirt carrier triangles as a one-way source: their observed
  neutral-to-current translation, rotation, and scale are applied directly to each calibrated
  accessory bind pose. Sen head/body motion can no longer reverse, lag, or amplify the roots.
- Keeps only accessory-local Sen behaviour, including the native paired-ear double twitch. Ahoge,
  ear fins, and tail still use separate filters and draw passes.
- Maps the temporary horizontal binding test to the maid's visible `ParamAngleX3` and the vertical
  test to `ParamAngleY2`. This is intentionally only enough to validate attachment; the complete
  maid action map will be labelled later from on-device observation.
- The diagnostic report now states `sen_rigid_parameter_drive=false` and identifies the one-way
  mesh-carrier path, so a report can distinguish this build from the failed v0.1.11 strategy.

## v0.1.11 (superseded test history)

- Resets the runtime architecture to the correct source models: the original high-fidelity maid
  `.moc3` is the only primary model; Sen is only the donor for the six-mesh ahoge, the native
  `Part113` paired ear-fin rig, and the active `Part239` tail meshes. Ruby is not loaded, referenced,
  or used as an attachment baseline anywhere in the current code.
- Replaces the failed cross-model bounding-box/one-point corrections with fixed drawable-triangle
  carriers. The maid face and Sen face provide the head carrier for two independent ahoge/ear-fin
  passes; the maid skirt and Sen lower-body mesh provide the tail carrier.
- Transfers the maid carrier's neutral-to-current similarity transform, then removes the donor
  carrier's incompatible movement. Sen's local ahoge bend, native paired-ear twitch, and tail swing
  remain intact because every group receives one uniform final clip-space correction.
- Keeps the confirmed neutral calibration, including tail scale `1.00` after the two requested
  enlargement steps. The diagnostic report now records the exact drawable and three fixed vertex
  IDs selected for every carrier.
- Prunes dormant hidden `Part239` tail variants from the draw filter, so the private package only
  needs the recoloured Sen texture slots 06, 16, and 19.

Versions v0.1.11 and earlier are retained below only as failed-test history. Their donor-rig or
Ruby-oriented attachment assumptions are superseded and must not be reused.

## v0.1.10 (superseded test history)

- Leaves the now-confirmed ear-fin path unchanged: the complete native Sen ear rig continues to
  follow Ruby through shared compatible head parameters, with no rigid frame or App-side mirror.
- Corrects the ahoge independently with one root-point translation. Ruby `Part25` (face centre)
  drives the confirmed Sen ahoge root, while all six ahoge meshes receive one identical delta so
  tip bend and local physics remain intact.
- Adds three full-range, single-axis head tests for left/right (`AngleX`), up/down (`AngleY`) and
  tilt (`AngleZ`), while retaining the mixed head sweep. Auto inspection now visits all three axes.
- Keeps every confirmed calibration value and the tail's verified body two-point binding unchanged.

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
