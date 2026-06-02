/**
 * Administration services cluster (architecture §5.2.2): operator- and
 * system-driven concerns — admin user lifecycle, lifecycle jobs (purge,
 * invitation expiry, open-record auto-close), audit-event emission setup.
 * Reachable from Web and the Lifecycle trigger adapter.
 */
package com.trawhile.service.administration;
