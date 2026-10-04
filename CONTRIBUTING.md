# Contributing

How features are specified, implemented and verified is described in [AGENTS.md](AGENTS.md)
("Workflow") and the constitution in `.specify/memory/constitution.md`. This file covers commit
messages and pull requests.

## Commit messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Types used in this repo's history:

| Type | Use for |
| --- | --- |
| `feat` | A new user-facing capability (e.g. a screen, a control, a setting) |
| `fix` | A bug fix |
| `refactor` | A code change that neither fixes a bug nor adds a feature |
| `docs` | Documentation-only changes (AGENTS.md, constitution, Spec Kit artifacts, design copies) |
| `build` | Gradle build, dependencies, signing |
| `ci` | GitHub Actions workflows and Dependabot |
| `chore` | Routine maintenance with no user-facing or CI effect (version bump, scripts, tooling config) |

Scopes are optional. Common ones: the screen or area touched (`now-playing`, `settings`, `nav`,
`scripts`), and for Spec Kit artifacts under `specs/**` the artifact (`spec`, `plan`, `tasks`,
`specs`) or `design` for `design/**`.

Breaking changes: append `!` after the type/scope (`feat!:`) and/or add a
`BREAKING CHANGE:` footer, per the spec.

Keep the description short and in the imperative mood ("add", not "added"/"adds"). Use the body
to explain why, not just what. Do not add a `Co-Authored-By` trailer or other tool attribution.

## Pull requests

- Changes reach `main` only through pull requests. A feature is one pull request from its
  `NNN-short-name` branch.
- The merge gate is a green CI run plus the local checks in [AGENTS.md](AGENTS.md) ("Commands").
  Documentation-only changes (`**.md`, `specs/**`, `docs/**`, `design/**`) skip CI; their gate is
  review alone.
  For a feature, record the on-device results in `specs/NNN-*/verification.md` and link it from
  the pull request.
- Merge with **squash**. The squash commit title is conventional and ends with the feature number
  and the pull request number, e.g. `feat: Now Playing and Move to room (002) (#3)`; its body
  summarises the user-facing changes, then the infrastructure ones.
- Do not add "Generated with Claude Code" or similar tool-attribution text to pull request
  descriptions.
