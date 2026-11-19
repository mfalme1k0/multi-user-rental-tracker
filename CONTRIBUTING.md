# Contributing

The assessment reads the git history: reviewed PRs, small focused commits, clear messages, work from all three members.

## Branches
- `main` is protected: no direct pushes, merge only through a PR with at least one approval from someone other than the author.
- One branch per task, named `type/short-topic`: `feat/db-schema`, `feat/rental-service`, `test/repository-constraints`, `docs/er-diagram`.

## Commits
- Small and focused: one logical change per commit.
- Message format: `type(scope): imperative summary`, e.g. `feat(repo): translate unique violations`.
  Types: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.
- Never one big "add everything" commit.

## Pull requests
- Keep them small (a reviewer can read ~200 lines carefully). Fill in the PR template.
- Review the others' code: everyone must have review comments in the history, not only the lead.
- Only the lead merges, after the review conversation is resolved.
- Contract files (repository interfaces, service signatures, `ItemStatus`, exceptions) need their owner as reviewer.

## Before you push
```bash
mvn test        # all tests + JaCoCo report (target/site/jacoco/index.html)
```

## Coverage report deliverable
`target/` is ignored, so before the final submission run `mvn verify -Pcoverage-gate` and copy
`target/site/jacoco/` to `docs/coverage/` and commit it.
