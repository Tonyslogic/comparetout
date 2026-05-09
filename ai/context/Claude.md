# ANDROID UI REPLACEMENT — SYSTEM RULES (SHORT VERSION)

You are assisting with a full UI replacement.
The backend, data layer, domain layer, and ViewModel contracts are STABLE and MUST NOT be modified.
Only UI code is being replaced. No functional changes.

------------------------------------------------------------
## 1. BACKEND & ARCHITECTURE RULES (STRICT)
- Do NOT modify repositories, DTOs, API calls, or domain logic.
- Do NOT change ViewModel public functions, state flows, or event handlers.
- Do NOT rename ViewModels or navigation routes.
- Do NOT introduce new backend calls or dependencies.
- Navigation arguments must remain compatible.

------------------------------------------------------------
## 2. UI RULES
- Use Jetpack Compose + Material 3.
- Follow unidirectional data flow (UDF).
- Hoist state to ViewModels; composables must be pure.
- Use MaterialTheme for colors/typography; avoid hardcoded styling.
- Provide contentDescription for interactive elements.

------------------------------------------------------------
## 3. SUMMARISATION RULES
When a large file is provided:
- Automatically generate a compact structural summary.
- Include: purpose, public functions, state, events, navigation, dependencies.
- Exclude: full code, imports, comments, styling.
- Reuse summaries for all future reasoning.
- Store summaries named for the file being summarized in ai/context/summaries
- Ask for missing context instead of guessing.

------------------------------------------------------------
## 4. DIFF-BASED OUTPUT RULES
When modifying existing code:
- Default to unified diff format.
- Do NOT rewrite entire files unless explicitly asked.
- Keep changes minimal and safe.
- Never alter backend logic or ViewModel contracts.

------------------------------------------------------------
## 5. WORKFLOW RULES
- Keep token usage low; avoid large inputs.
- Prefer summaries + diffs over full-file rewrites.
- Ask for missing files/summaries when needed.
- Follow all rules above even if user prompt is ambiguous.

------------------------------------------------------------
## 6. PRIMARY OBJECTIVE
Produce safe, architecture-preserving UI refactors and Compose implementations
that respect all constraints while minimising token usage.
