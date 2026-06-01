# 0011. Shape API error responses

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are application errors mapped to HTTP responses for API clients?

The REST API needs consistent error shapes for clients. Controllers and services can throw domain-specific exceptions, but HTTP mapping and response formatting should be centralized.

The OpenAPI contract defines a `Problem` response shape with a stable `code` field.

## Decision

Use a single global `@ControllerAdvice` to map exceptions to `Problem` responses.

Map access denial, authentication failures, missing entities, validation errors, and business-rule violations to stable HTTP statuses.

Require exceptions to carry a stable `code` string matching the OpenAPI `Problem.code` contract.

## Consequences

API clients can handle errors consistently.

Controller code stays focused on request handling.

New domain errors require a stable code and mapping review.
