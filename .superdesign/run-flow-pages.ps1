$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\Sampath\github\MindAnchor'

$pagesObj = @(
  [pscustomobject]@{
    title = 'Notes'
    prompt = 'Inherit the journal home style. Each note renders as a journal entry: small italic day-of-week and date above the body, no card chrome, generous line-height, faint paper texture behind. Search and filter become a single unobtrusive small-caps line at top. No badge counts, no streaks, no Material chips. Mental-health-first: no urgency, no productivity framing.'
  },
  [pscustomobject]@{
    title = 'Settings'
    prompt = 'Inherit the journal home style. Section headers become quiet chapter dividers in small caps with a thin rule below (SOURCES, MODES, STORAGE, ABOUT). Each row is a single line with no card background, just a thin separator. Toggles are small subtle switches. No grid of cards, no elevation. Slow-sky color tokens carry through. Mental-health-first: no streaks, no notifications, no urgency.'
  },
  [pscustomobject]@{
    title = 'Mood check-in'
    prompt = 'Inherit the journal home style. The five named mood states (Crushed, Heavy, Steady, Light, Bright) render as horizontal choices across the page, each on a faint sepia wash of its color, NOT circular buttons, NOT emoji-primary. After selection, a small italic journal line writes itself in response. No urgency copy, no score, no streak. Mental-health-first: a quiet ritual, not a metric.'
  },
  [pscustomobject]@{
    title = 'Quick note composer'
    prompt = 'Inherit the journal home style. The composer is writing on a page, not a Material modal. Faint vertical margin on the left, generous serif body type, no card chrome. Save is a small Save word in serif italic at the bottom right, not a filled button. The bang commands live in a tiny hint line at the bottom: try !ground !panic !breathe !mood !note !task !settings. No app bar, no close X, no button bar.'
  }
)

$pagesJson = $pagesObj | ConvertTo-Json -Compress -Depth 5
Write-Host "JSON: $pagesJson"
Write-Host ""

$env:SUPERDESIGN_PAGES = $pagesJson
& npx --yes @superdesign/cli@latest execute-flow-pages `
  --draft-id 'b35ee64d-c44d-4033-a574-f9545025a5e7' `
  --pages $pagesJson `
  --context-file 'C:\Users\Sampath\github\MindAnchor\.superdesign\init\theme.md' 'C:\Users\Sampath\github\MindAnchor\.superdesign\design-system.md'
