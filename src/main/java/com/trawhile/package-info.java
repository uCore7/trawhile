/**
 * trawhile — small-company self-hosted time-tracking system.
 *
 * <p>Hexagonal architecture (ADR 0001) with three service clusters
 * (architecture §5.2.2): Work, Identity and access, Administration.</p>
 *
 * <p>Top-level layout (architecture §5.2.4):</p>
 * <ul>
 *   <li>{@code adapter.inbound} — REST, MCP, SSE, security, lifecycle entry points.</li>
 *   <li>{@code adapter.outbound} — persistence (jOOQ), event dispatch, metrics.</li>
 *   <li>{@code port.inbound} — use-case interfaces, grouped by cluster.</li>
 *   <li>{@code port.outbound} — persistence and event port interfaces.</li>
 *   <li>{@code service} — use-case implementations, grouped by cluster.</li>
 * </ul>
 */
package com.trawhile;
