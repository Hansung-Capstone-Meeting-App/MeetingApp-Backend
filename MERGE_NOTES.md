# Merge Notes

## Branch

- Target branch: `feat2/api연동`
- Remote repository: `https://github.com/Hansung-Capstone-Meeting-App/MeetingApp-Backend.git`

## What Was Merged

- Updated local `feat2/api연동` to the latest remote `origin/feat2/api연동`.
- Merged the latest `origin/main` into `feat2/api연동`.
- This brought the recent `main` changes into the feature branch without force-pushing or overwriting remote history.

## Included Main Changes

- Meeting export / Notion export related backend code.
- Recording pipeline status response code.
- Workspace invitation count changes.
- Frontend integration guide updates.
- Main branch transcript correction revert history.

## Local Changes Added After Merge

- Added H2 runtime dependency for local execution.
- Added `run-local.ps1` to run the backend locally with dummy/local runtime settings.
- Replaced `meeting-1-transcript.json` with `meeting-1-transcript.md`.
- Added `codex-feat2.patch` as a patch record of previous feature work.

## Safety Notes

- No `git push --force` was used.
- A backup branch was created before merging.
- A safety stash was left in the local repository:
  - `stash@{0}: codex-safe-before-merge-20260521-030407`

## Verification

- `compileJava` completed successfully during `gradlew test`.
- Full `gradlew test` did not complete because OneDrive marked `src/test/java/com/capston/demo/DemoApplicationTests.java` as a reparse point instead of a regular file.
