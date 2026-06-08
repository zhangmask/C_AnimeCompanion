# Design

## Theme

Anime Companion uses the existing Jetpack Compose Material 3 system as its base. The physical scene is a user returning to a private companion app on a phone, often in low-friction daily moments, sometimes with voice, sometimes in public where discretion matters. The UI should therefore be calm, compact, and privacy-forward rather than theatrical.

## Color

Use the app's current Material 3 color scheme and semantic roles first. Accent color is reserved for primary actions, selected navigation, active filters, and generation states. Avoid one-note purple, blue-slate, beige, or neon palettes. Discovery surfaces may use small cover-image color fields, but controls remain restrained and legible.

## Typography

Use the existing Android system/Material typography scale. Product labels stay compact and functional. Reserve larger type for screen titles and role names only; role cards, chips, and settings panels should use dense hierarchy with clear weight contrast.

## Components

- Bottom navigation remains the primary mobile app shell.
- Search, filter chips, segmented controls, switches, menus, icon buttons, and Material buttons should use familiar Material affordances.
- Chat input is a single bottom panel: image, voice output, text entry, and the current primary action live inside one surface. Empty input shows voice input as the primary action; text or selected images switch the primary action to send.
- Memory and settings actions should prefer compact icon affordances when large text buttons would crowd mobile or accessibility-scaled layouts.
- Cards are used for individual roles or repeated gallery items only. Avoid cards nested inside cards.
- Empty, loading, unavailable, and fallback states should explain the product state in short, actionable copy.

## Layout

Use mobile-first structure: top search/filter controls, horizontal category chips, two-column role discovery cards where width allows, and single-column detail sections. Keep stable dimensions for role covers and icon controls to avoid layout shift.

Chat surfaces should stay quiet and task-first: the top model status is secondary, message timestamps are subdued, assistant surfaces use neutral containers, and the input panel should remain stable when images are attached or the IME is visible.

## Motion

Use simple 150-250 ms state transitions where Compose defaults already provide them. Do not add decorative page-load choreography.
