# PcViewer: notes for agents

- Never run `m`/`lunch`/soong here to test: use the host checks (README, "Tests"). `tools/typecheck.sh`
  compiles the Compose UI without a build; keep JVMs at -Xmx1.5g or less.
- TDD: logic goes in `core/` (plain Kotlin), test first in `core/tests/*Test.kt`, see it fail, then code. The
  Android layer (`src/`) only wires the core to Compose, the file picker and content URIs.
- Tables over if/else: shortcut maps, loader/command maps in the view model, rule lists. Names start with an
  English verb or prefix (is/has/on/with/build/read/save...).
- UI text: pt-BR in `res/values-pt`, English in `res/values`; what happened and what to do, one or two
  sentences, no em dash, no design explanations (`tests/test_strings.py` checks parity and dashes).
- Googlebook's app is a layout reference only: never copy, translate or adapt its code or assets.
- README in English, ending with the Credits section. Commits: no AI attribution lines.
