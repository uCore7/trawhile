/**
 * Outbound port interfaces (architecture §5.2.4):
 * <ul>
 *   <li>{@code persistence} — service-specific persistence contracts (jOOQ-backed adapters).</li>
 *   <li>{@code event} — event emission contract (SSE + webhook outbox channels).</li>
 *   <li>{@code metrics} — metrics emission contract (Micrometer-backed adapter).</li>
 * </ul>
 */
package com.trawhile.port.outbound;
