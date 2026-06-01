# 0008. Process purge jobs

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are retention and deactivated-node purge jobs executed so they are restartable and avoid long-running transactions?

Activity retention and deactivated-node deletion can affect many rows. Purge work must avoid long-running transactions, survive application restarts, and respect stored cutoff dates.

Node deletion also has domain constraints: deletion proceeds bottom-up and only after retained activity and request data no longer remain in the candidate subtree.

## Decision

Run purge jobs through Spring `@Scheduled` job classes.

Store purge job state in `purge_jobs`.

Resume active purge jobs on startup using the stored `cutoff_date`; do not recompute the cutoff while resuming.

Process deletions in chunks. Each chunk runs in its own `REQUIRES_NEW` transaction.

Delete deactivated nodes iteratively from leaves upward and verify that no retained `time_records` or `requests` remain in the candidate subtree before deletion.

## Consequences

Purge jobs avoid holding one transaction for the full retention cleanup.

Interrupted purge jobs can resume safely.

Progress can be recorded between chunks.

The implementation must keep the coordinator loop outside the chunk transaction and must make each chunk idempotent.
