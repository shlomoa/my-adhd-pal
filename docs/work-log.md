# Work log

One log per lane, so parallel sessions never edit the same file. Add entries at the end of your own file only.

| Log | Written by |
|---|---|
| [`work-log/shared.md`](work-log/shared.md) | Shared and data lane (S) |
| [`work-log/android.md`](work-log/android.md) | Android lane (A) |
| [`work-log/watch.md`](work-log/watch.md) | Watch lane (W) |
| [`work-log/planning.md`](work-log/planning.md) | planning threads and the orchestrator |

## Entry format

One entry per task, newest last:

```markdown
## YYYY-MM-DD - <step id> <title>

- Issue and PR: <links>
- Changed: <what>
- Validation: <plan, red baseline reference, final results, commands>
- Open items: <questions, follow-ups, manual steps waiting for the user>
```

A step that cannot start because a gate is not merged ends with `BLOCKED: <step> waits for <gate>` as the last line of
its entry (see [`plans/README.md`](plans/README.md), stop rule). Remove nothing; when the thread resumes, add a new entry.
