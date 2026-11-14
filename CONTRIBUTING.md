# Contributing to ArchX

Thanks for your interest in contributing to ArchX. The project is under active development, so please coordinate changes through branches and pull requests.

## Before you start

- Read the [README](README.md) and relevant documentation under [`docs/`](docs/), especially the [architecture overview](docs/architecture.md) and applicable architecture decision records (ADRs).
- For a substantial change or a new extension, open an issue or discussion first so the design can be coordinated.
- Do not commit directly to `main` or another shared branch.

## Branches and extensions

- Create your own branch for your work. Extension development should happen on a contributor-owned branch, not directly on a shared branch.
- Use a descriptive branch name, for example `extension/console-driver-name` or `feature/autosave-recovery`.
- Keep changes focused. If an extension depends on changes to core interfaces, discuss that dependency before starting implementation.
- Push your branch and open a pull request against the appropriate target branch when the work is ready.

## Review is required

Every contribution must be previewed and reviewed by the project owner or a designated team member before it is merged or otherwise incorporated into `main` or any other shared branch. Contributors should not merge their own pull requests or copy unreviewed changes into shared branches.

Review may request changes, tests, documentation, or clarification. Approval is not automatic, and submitting a pull request does not guarantee that a contribution will be accepted.

## AI-assisted development

AI tools may be used to assist with development. The project's ideas and implementation direction are provided by its owner; AI assistance does not replace contributor responsibility for their changes.

Before submitting AI-assisted code, contributors must:

- Review and understand the generated code, including its behavior and dependencies.
- Preview and test it where practical; do not submit code that has not been checked.
- Verify that generated code does not introduce security, licensing, privacy, or attribution issues.
- Disclose substantial AI assistance in the pull request description and identify which parts were AI-assisted.

The project owner or a designated team member will also preview and review the contribution before it enters any shared branch.

## Pull request checklist

- [ ] The change is focused and its purpose is explained.
- [ ] Relevant tests pass, or the pull request explains why tests could not be run.
- [ ] Documentation and schemas are updated when needed.
- [ ] Generated or AI-assisted code has been reviewed and previewed by the contributor.
- [ ] Third-party code, assets, and dependencies have compatible licenses and appropriate attribution.
- [ ] The pull request describes testing performed and any limitations.

## Licensing

By submitting a contribution, you agree that it may be distributed under the project's [GNU GPL v3.0 license](LICENSE.md). Please do not submit code or other material that you do not have the right to contribute. The repository owner may ask for additional contributor documentation if the project's licensing approach changes.

## Questions

If you are unsure about an extension interface, branch target, or design decision, open an issue or discussion before investing in a large implementation.
