# COPY EDIT - Real Video Editing & Reusable Template Engine

Edit Once. Copy Forever. COPY EDIT is a native Android video-editing and reusable editing-template application built with Kotlin, Jetpack Compose, Material 3, AndroidX Media3 (ExoPlayer & Transformer), and Room local database.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on user feedback:
> - **Media Strategy**: Built-in sample video clips (e.g. geometric animation & high-motion samples) will be included for immediate template preview and testing, alongside full Android Storage Access Framework (SAF) system picker support for local phone videos.
> - **Default Export**: 1080p Full HD at 30fps will be the primary export profile, with on-the-fly toggles for 720p HD for fast mobile rendering.
> - **External & VN Project Compatibility**: External project archives and VN editor formats will be parsed for recognized cuts, clip boundaries, transforms, and keyframes, paired with a transparent diagnostic import report detailing supported vs unsupported features.
> - **Design Aesthetic**: Premium white interface (#FFFFFF / #F8F9FC) with purple (#8B35F0) and blue (#536DFF) accents, subtle card elevation, crisp typography, and high-contrast timeline controls. The preview viewport remains black only when displaying video.

---

## 1. Overview & Core Concept

COPY EDIT enables mobile creators to inspect video cuts, keyframes, transforms, and audio from existing edits, convert them into reusable templates, and apply those templates to new footage with accurate preview and real MP4 export.

- **What It Does**:
  1. Imports local videos via SAF and supported project archives (ZIP/7z).
  2. Plays back video with real-time controls (play, pause, scrub, seek, aspect ratio, audio volume).
  3. Provides a visual multi-track timeline with cuts, trim handles, playhead, and keyframe markers.
  4. Runs a keyframe engine calculating position, scale, rotation, and opacity with linear and easing interpolation.
  5. Stores projects and templates persistently in Room DB without fake states.
  6. Renders real MP4 video via AndroidX Media3 Transformer and saves to `Movies/COPY EDIT` with MediaStore registration.
  7. Includes a guided 5-step template application workflow mapping source videos to template cut points.

- **Target Audience**: Content creators, short-form video editors, and mobile producers who want to standardize and replicate complex editing styles across projects.
- **Key Value**: Eliminates manual re-cutting and re-keyframing by turning any completed video edit into a transferable template.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme Tokens
- **Background (Primary)**: `#FFFFFF`
- **Background (Secondary/Surface)**: `#F8F9FC`
- **Primary Text**: `#151522`
- **Secondary / Muted Text**: `#777787`
- **Accent Purple**: `#8B35F0`
- **Accent Blue**: `#536DFF`
- **Gradient Action**: `Brush.horizontalGradient(listOf(Color(0xFF8B35F0), Color(0xFF536DFF)))`
- **Card Border**: `#ECECF3`
- **Video Preview Canvas**: `#0D0E15` (enclosed video player only)
- **Timeline Track Neutral**: `#F0F2F8` with `#8B35F0` active track headers and `#536DFF` clip pills

### Screen Layouts & User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│ TOP BAR: [Logo: COPY EDIT]       [Search]  [Notifications]  [PRO Badge]│
├────────────────────────────────────────────────────────────────────────┤
│ "Your Canvas Awaits."                                                  │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │  [+] CREATE NEW PROJECT  (Purple-Blue Gradient Card)              │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│ Quick Actions:  [📷 Camera]   [🎬 Video Picker]   [📦 Import Project]  │
├────────────────────────────────────────────────────────────────────────┤
│ RECENT PROJECTS          MY TEMPLATES          POPULAR TEMPLATES       │
│ ┌─────────┐ ┌─────────┐  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │Project A│ │Project B│  │BeatSync │ │ZoomPop  │ │VlogFast │ │Cinematic│ │
│ └─────────┘ └─────────┘  └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│ BOTTOM NAV:  [Home]   [Templates]   [(+) Create]   [Library]   [Profile]│
└────────────────────────────────────────────────────────────────────────┘
```

1. **Home Screen**:
   - Welcome banner with tagline "Edit Once. Copy Forever."
   - Large gradient "Create Project" action.
   - Quick action pill row: Camera capture, Video selection, and Archive Import.
   - Horizontal carousels for Recent Projects, Custom Templates, and Ready-to-use Popular Templates.
   - Recently exported videos section with direct play/share actions.

2. **Video & Project Editor Screen**:
   - Top Bar: Project title, Undo/Redo icons, Save indicator, and "Export" button.
   - Video Viewport: AndroidX Media3 ExoPlayer surface with responsive aspect ratios (16:9, 9:16, 1:1, 4:5), overlay transform preview, and timecode display (`00:03.24 / 00:15.00`).
   - Transport Controls: Play/Pause toggle, Frame-step back/forward, Mute, and Current Speed pill (0.5x, 1x, 2x).
   - Visual Timeline: Multi-track ruler, draggable playhead with millisecond precision, video track with clip blocks and trim handles, audio track, and keyframe diamond markers.
   - Action Toolbar: Split/Cut, Add Keyframe, Transform, Speed, Audio, Delete Clip, and Create Template from Project.

3. **Template Applicator Workflow**:
   - Step 1: Select or preview template structure (cut durations, keyframe curves).
   - Step 2: Select replacement source video(s) from phone or sample media.
   - Step 3: Media validation check (duration check, aspect ratio mismatch warnings, audio track detection).
   - Step 4: Clip slot mapping (auto-distribute or assign custom start in/out points).
   - Step 5: Real-time preview with applied keyframe transforms & 1-tap export.

4. **Library & Export Manager**:
   - Tabbed view: Projects, Templates, and Rendered MP4s.
   - Project cards: Rename, Duplicate, Open in Editor, Delete with confirmation dialog.
   - Exported videos: Native Android video playback, file size, duration, "Share via Intent", and "Show in Gallery".

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Alternatives Considered |
| :--- | :--- | :--- | :--- |
| **Video Playback** | AndroidX Media3 ExoPlayer (`PlayerView` / Compose `AndroidView`) | High-performance, hardware-accelerated playback with frame-accurate seeking and lifecycle management. | Standard Android `VideoView` (inflexible, poor seeking accuracy). |
| **Video Export** | AndroidX Media3 Transformer (`androidx.media3:media3-transformer`) | Official Android pipeline for hardware-encoded MP4 rendering, audio mixing, sequencing, and visual effects. | FFmpeg CLI (heavy native binaries, architecture licensing overhead, slow builds). |
| **Data Storage** | AndroidX Room Database with TypeConverters | Type-safe, reactive `Flow` queries, transactional reliability for project/clip/keyframe entities. | Raw SQLite or JSON files (fragile across process death and migrations). |
| **Archive Handling** | Java `ZipInputStream` & safe 7z/Zip byte stream parser with path traversal protection | Prevents Zip Slip security vulnerabilities while extracting metadata and assets into app cache. | Unchecked native extractors (security risks, missing error diagnostics). |
| **Keyframe Engine** | Normalized timeline keyframe model with Linear & Cubic Bezier easing | Evaluates properties $(x, y, scale, rotation, opacity)$ at any arbitrary playhead timestamp $t$. | Discrete keyframes only (causes choppy preview and unrendered jumps). |

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  [HomeScreen]   [EditorScreen]   [TemplateApplyScreen]  [LibraryScreen]│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ State / User Actions
┌───────────────────────────────────▼────────────────────────────────────┐
│                  ViewModel & State Orchestration                       │
│  - EditorViewModel (Timeline, Keyframes, Playhead, Undo/Redo)          │
│  - TemplateViewModel (Slot Mapping, Diagnostics, Preset Presets)       │
│  - ExportViewModel (Transformer Pipeline, MediaStore, Progress)        │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼──────────────┐   ┌─────────────▼───────────────────┐
│     Media3 Engine Subsystem      │   │     Storage & Data Subsystem    │
│  ┌─────────────────────────────┐ │   │  ┌────────────────────────────┐ │
│  │ ExoPlayer (Real-time View)  │ │   │  │ Room Database              │ │
│  │ - Frame seeking & playback  │ │   │  │ - Projects, Clips, Frames  │ │
│  │ - Aspect ratio & canvas FX  │ │   │  │ - Templates & Export Log   │ │
│  └─────────────────────────────┘ │   │  └────────────────────────────┘ │
│  ┌─────────────────────────────┐ │   │  ┌────────────────────────────┐ │
│  │ Transformer (MP4 Render)    │ │   │  │ Storage Access Framework   │ │
│  │ - Multi-clip sequencing     │ │   │  │ - ContentResolver URI read │ │
│  │ - MediaStore: Movies/COPY   │ │   │  │ - MediaStore video write   │ │
│  └─────────────────────────────┘ │   │  └────────────────────────────┘ │
│  ┌─────────────────────────────┐ │   │  ┌────────────────────────────┐ │
│  │ Keyframe Interpolation Core │ │   │  │ Archive / VN Project Parser│ │
│  │ - Pos, Scale, Rot, Opacity  │ │   │  │ - Zip/7z Safe Extractor    │ │
│  │ - Linear & Easing math      │ │   │  │ - Diagnostic Report Gen    │ │
│  └─────────────────────────────┘ │   │  └────────────────────────────┘ │
└──────────────────────────────────┘   └─────────────────────────────────┘
```

### Data Schema (Room Entities)

1. **`ProjectEntity`**:
   - `id: String` (UUID)
   - `title: String`
   - `createdAt: Long`, `updatedAt: Long`
   - `aspectRatio: String` ("16:9", "9:16", "1:1")
   - `durationMs: Long`
   - `exportPreset: String` ("1080p", "720p")

2. **`ClipEntity`**:
   - `id: String`, `projectId: String`
   - `sourceUri: String` (content URI or bundled sample resource)
   - `sourceDurationMs: Long`
   - `trimStartMs: Long`, `trimEndMs: Long`
   - `timelineStartMs: Long`
   - `trackIndex: Int`
   - `volume: Float`
   - `speed: Float`

3. **`KeyframeEntity`**:
   - `id: String`, `clipId: String`
   - `timestampMs: Long` (relative to clip start)
   - `posX: Float`, `posY: Float`
   - `scaleX: Float`, `scaleY: Float`
   - `rotationDegrees: Float`
   - `opacity: Float`
   - `interpolation: String` ("LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT")

4. **`TemplateEntity`**:
   - `id: String`
   - `name: String`, `description: String`
   - `category: String` ("Beat Sync", "Cinematic", "Vlog", "Transition")
   - `targetAspectRatio: String`
   - `slotCount: Int`
   - `templateJson: String` (full serialized clip & keyframe structure)

5. **`ExportedVideoEntity`**:
   - `id: String`, `projectId: String`, `title: String`
   - `outputUri: String`, `filePath: String`
   - `resolution: String`, `fileSizeBytes: Long`, `durationMs: Long`
   - `exportedAt: Long`

---

## 5. Phased Implementation Plan

- **Phase 1: Foundation & Dependencies**
  - Configure `gradle/libs.versions.toml` and `app/build.gradle.kts` with compatible AndroidX Media3 (`media3-exoplayer`, `media3-ui`, `media3-transformer`, `media3-effect`), Room KSP, and Material Icons.
  - Setup Room database (`CopyEditDatabase`), DAOs, and repository layer.
  - Setup bundled sample video assets in `res/raw` for immediate verification without requiring device media.
  - Update `metadata.json` and `strings.xml` for platform sync.

- **Phase 2: Premium White Theme & Navigation**
  - Implement the M3 color scheme and typography matching the exact color specifications (#FFFFFF, #F8F9FC, #151522, #8B35F0, #536DFF).
  - Build the top bar with branding, PRO badge, search, notifications.
  - Implement bottom navigation with 5 destinations (Home, Templates, Create, Library, Profile) and `BackHandler` safety.

- **Phase 3: Real Video Playback & Editor Viewport**
  - Build `ExoPlayerController` handling play, pause, seek, volume, speed, duration, and error retry states.
  - Implement `VideoPlayerSurface` with correct aspect ratio preservation and transform overlay.
  - Implement real SAF system video picker (`ActivityResultContracts.OpenDocument` / `PickVisualMedia`).

- **Phase 4: Timeline Editor & Keyframe Engine**
  - Multi-track timeline UI: time ruler, draggable playhead, clip strips with trim handles, split/cut markers.
  - Implement mathematical keyframe interpolation (`interpolateKeyframe(keyframes, currentTime)`).
  - Undo/redo state stack for all timeline operations.

- **Phase 5: Template Engine & Guided Applicator**
  - Build native template serialization/deserialization.
  - 5-step template applicator dialog/screen: slot mapping, duration validation, asset replacement, keyframe re-targeting.
  - Preset template library with bundled creative templates.

- **Phase 6: Real MP4 Rendering & MediaStore Export**
  - Integrate AndroidX Media3 `Transformer` with composition sequencing.
  - Export progress listener updating percentage and time remaining.
  - Save output to `Movies/COPY EDIT` via MediaStore `ContentValues`.
  - Share via Android Intent and Gallery open actions.

- **Phase 7: Project Archive & VN Compatibility Inspector**
  - Implement secure archive extractor (Zip/7z) with Zip-Slip path sanitization.
  - External format analyzer extracting recognized cuts and keyframe points.
  - Diagnostic import report UI showing recognized clips, cuts, keyframes, missing media, and unsupported properties.

- **Phase 8: Automated Verification & Robolectric Tests**
  - Write Robolectric and Unit tests for:
    - Keyframe interpolation math (exact match, between keyframes, clip boundaries).
    - Cut timestamp sequencing and timeline duration calculations.
    - Template serialization, cloning, and slot mapping.
    - Archive zip extraction safety and invalid file detection.
  - Run `compile_applet` and test execution to ensure pristine build.
