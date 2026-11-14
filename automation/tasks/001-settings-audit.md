---
id: 001-settings-audit
status: pending
iteration: 0
iteration_cap: unlimited   # objective: a setting either does what it says or not
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Every toggle/setting shown to the user in the app actually does what its
label/description says, on the real device, with no dead settings and no
crashes when changed.

## Acceptance criteria

- Walk every settings screen (Appearance, Player, Player Advanced, Content,
  Content Advanced, Theme, Glass Effect, EQ, DIY, etc.) reachable from the
  Settings root.
- For each toggle/slider/picker: read its label + description string
  (`exhale_strings.xml`), flip it, and confirm the described behavior actually
  changes on-device (not just that the preference value changed in
  DataStore — the *visible/audible effect* it promises must occur).
  Reference the [[user memory]] on this preference if one names known-stale
  toggles.
- Any setting found dead (no effect) or crashing gets its own follow-up task
  file generated (`automation/tasks/NNN-fix-<setting>.md`), not fixed inline
  here — this task's job is the audit + report, not blind fixes.
- Report lists every setting checked, pass/fail per one, and for each fail:
  what was expected vs. observed.

## Context

Settings screens live under `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/`.
Preference keys in `constants/PreferenceKeys.kt`. This is the user's stated
#1 priority — "everything should work from settings, and its intended
behaviour."
