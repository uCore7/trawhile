# Test plan

Traceability chain: **UR → SR → TE-xxx-nn**. Every SR maps to at least one happy-path test and, where the SR defines a rejection condition, at least one error test.

Test IDs follow the pattern `TE-{SR}-{nn}` where `{SR}` mirrors the SR number (letters uppercased: `007A`, `009B`, `064A`, etc.) and `{nn}` is a two-digit sequence within that SR.

## Conventions

| Code | Meaning |
|---|---|
| IT | `@SpringBootTest` + Testcontainers PostgreSQL (real DB, real Flyway migration) |
| UT | Plain JUnit 5, no Spring context |
| SIT | Spring `@WebMvcTest` / MockMvc, no DB (HTTP contract tests) |
| CT | Angular component test (`*.spec.ts`, TestBed) |
| E2E | End-to-end test (Playwright, full browser + running app) |

All IT tests extend a shared `BaseIT` that starts the Postgres container once per suite via `@Testcontainers` + `@Container(reuse = true)`.

---

## Epic 1 — System administration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-001-01 | SR-001 | UR-001 | IT | `BootstrapIT` | `bootstrapAdmin_firstLogin_grantsRootAdminAndRedirectsToGdprNotice` — BOOTSTRAP_ADMIN_EMAIL set, no existing admin, matching OIDC email → PENDING_GDPR session set, no DB row yet | — |
| TE-001-02 | SR-001 | UR-001 | IT | `BootstrapIT` | — | `bootstrapAdmin_emailMismatch_redirectsToNotInvited` |
| TE-002-01 | SR-002 | C-2 | IT | `DataMinimizationIT` | `gdprRegistration_emailNotPersistedInAnyTable` — after SR-057a completes, no email string in any column across all tables | — |
| TE-005-01 | SR-005 | UR-004 | IT | `UserManagementIT` | `listUsers_admin_returnsAllUsersWithCorrectStatus` — active, pending, and anonymised each appear with correct `status` and `name` | `listUsers_nonAdmin_returns403` |
| TE-006-01 | SR-006 | UR-005 | IT | `InvitationIT` | `listInvitations_admin_returnsEmailInvitedByAndInvitedAt` | `listInvitations_nonAdmin_returns403` |
| TE-007-01 | SR-007 | UR-006 | IT | `InvitationIT` | `createInvitation_newEmail_insertsUsersAndPendingRow_returnsMailtoLink` — both rows created; mailto body contains base URL and email | `createInvitation_duplicateEmail_returns409` |
| TE-007A-01 | SR-007a | UR-006a | IT | `InvitationIT` | `resendInvitation_updatesExpiresAt_returnsFreshMailtoLink` — `expires_at` updated, no new `users` row, UUID unchanged | — |
| TE-008-01 | SR-008 | UR-060 | IT | `AuthFlowIT` | `oidcCallback_matchingPendingInvitation_storesSessionNoDatabaseWrite` — session contains `pending_invitations.id`, `user_id`, `provider`, `subject`, `name`; no new DB rows | `oidcCallback_expiredInvitation_redirectsToNotInvited` |
| TE-009-01 | SR-009 | UR-007 | IT | `InvitationIT` | `withdrawInvitation_triggersScrubbingState` — `pending_invitations` row deleted, `node_authorizations` deleted, stub retained when time entries exist | `withdrawInvitation_nonAdmin_returns403` |
| TE-009A-01 | SR-009a | GDPR | IT | `InvitationIT` | `expireInvitations_expiredRows_triggersScrubbingForEach` — rows with `expires_at` in the past transition to scrubbing state after `UserService.expireInvitations()` | — |
| TE-009B-01 | SR-009b | SR-009/009a/010/047 | IT | `UserScrubbingIT` | `scrubUser_withTimeEntries_endsActiveEntry_deletesProfile_retainsStub` — active entry gets `ended_at`; `node_authorizations` deleted; `user_profile` deleted; `users` row retained | — |
| TE-009B-02 | SR-009b | SR-009/009a/010/047 | IT | `UserScrubbingIT` | `scrubUser_noTimeEntries_noRequests_deletesUsersRow` | — |
| TE-009B-03 | SR-009b | SR-009/009a/010/047 | IT | `UserScrubbingIT` | `scrubUser_revokesMcpTokens_setsRevokedAt` | — |
| TE-010-01 | SR-010 | UR-008 | IT | `UserManagementIT` | `removeUser_admin_triggersScrubbing` | `removeUser_nonAdmin_returns403` |
| TE-011-01 | SR-011 | UR-009 | IT | `UserManagementIT` | `getUserAuthorizations_admin_returnsPathAnnotatedAssignments` — each row includes full ancestor path from root to granted node | `getUserAuthorizations_nonAdmin_returns403` |
| TE-012-01 | SR-012 | UR-010 | IT | `SettingsIT` | `getSettings_authenticatedUser_returnsResolvedConfig` — response includes `name`, `timezone`, `freezeOffsetYears`, `effectiveFreezeCutoff`, `retentionYears`, `nodeRetentionExtraYears`, `privacyNoticeUrl` | `getSettings_unauthenticated_returns401` |

---

## Epic 2 — Node administration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-016-01 | SR-016 | UR-014 | IT | `NodeIT` | `getNode_viewAuth_returnsNodeDetailsAndDirectChildren` | `getNode_noAuth_returns403` |
| TE-017-01 | SR-017 | UR-015 | IT | `NodeIT` | `createChild_adminAuth_insertsNodeWithSortOrderOneGreaterThanMax` | `createChild_nonAdmin_returns403` |
| TE-018-01 | SR-018 | UR-016 | IT | `NodeIT` | `updateNode_name_persistedInDatabase` | `uploadLogo_exceeds256KB_returns400` |
| TE-018-02 | SR-018 | UR-016 | IT | `NodeIT` | `uploadLogo_validPng_stored` — verify stored bytes and correct `Content-Type` on retrieval | `uploadLogo_imageGifMimeType_returns400` |
| TE-019-01 | SR-019 | UR-017 | IT | `NodeIT` | `reorderChildren_updatesAllSortOrderValues` — all sibling `sort_order` values match submitted order | `reorderChildren_nonAdmin_returns403` |
| TE-020-01 | SR-020 | UR-018 | IT | `NodeIT` | `deactivateNode_leafNode_setsIsActiveFalseAndDeactivatedAt` | `deactivateNode_withActiveChildren_returns409` |
| TE-020-02 | SR-020 | UR-018 | IT | `NodeIT` | `deactivateNode_withActiveTimeEntry_notBlocked` — running entry on the node itself does not block deactivation | — |
| TE-021-01 | SR-021 | UR-019 | IT | `NodeIT` | `reactivateNode_setsIsActiveTrueAndClearsDeactivatedAt` | `reactivateNode_nonAdmin_returns403` |
| TE-022-01 | SR-022 | UR-020 | IT | `NodeIT` | `moveNode_adminOnBothEnds_updatesParentIdAndAppendsSortOrder` | `moveNode_destinationIsOwnDescendant_returns409` |
| TE-022-02 | SR-022 | UR-020 | IT | `NodeIT` | — | `moveNode_noAdminOnDestination_returns403` |
| TE-023-01 | SR-023 | UR-021 | IT | `AuthorizationIT` | `grantAuth_admin_insertsNodeAuthorizationsRow` — upsert: second grant with different level updates existing row | `grantAuth_nonAdmin_returns403` |
| TE-024-01 | SR-024 | UR-022 | IT | `AuthorizationIT` | `revokeAuth_admin_deletesRow` | `revokeAuth_lastAdmin_returns409` |
| TE-024-02 | SR-024 | UR-022 | IT | `AuthorizationIT` | — | `revokeAuth_nonAdmin_returns403` |
| TE-025-01 | SR-025 | UR-023 | IT | `AuthorizationIT` | `listNodeAuthorizations_admin_distinguishesDirectFromInherited` — rows where `node_id` matches queried node marked `direct`; ancestor rows marked `inherited` | `listNodeAuthorizations_nonAdmin_returns403` |

---

## Epic 3 — Time tracking

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-026-01 | SR-026 | UR-024 | IT | `TrackingIT` | `getStatus_activeEntry_returnsNodePathAndElapsedTime` | — |
| TE-026-02 | SR-026 | UR-024 | IT | `TrackingIT` | `getStatus_noActiveEntry_returnsEmptyTrackingState` | — |
| TE-027-01 | SR-027 | UR-025 | IT | `TrackingIT` | `recentEntries_overlappingPair_bothFlaggedWithOverlap` | — |
| TE-027-02 | SR-027 | UR-025 | IT | `TrackingIT` | `recentEntries_gapBetweenConsecutiveEntries_flaggedWithGap` | — |
| TE-028-01 | SR-028 | UR-026 | IT | `TrackingIT` | `startTracking_trackAuthOnActiveLeafNode_insertsOpenEntry` — `ended_at IS NULL`, correct `timezone` stored | `startTracking_inactiveNode_returns409` |
| TE-028-02 | SR-028 | UR-026 | IT | `TrackingIT` | — | `startTracking_nodeWithActiveChildren_returns409` |
| TE-028-03 | SR-028 | UR-026 | IT | `TrackingIT` | — | `startTracking_noTrackAuth_returns403` |
| TE-029-01 | SR-029 | UR-028 | IT | `TrackingIT` | `switchTracking_endsExistingEntryAndInsertsNew_withinSingleTransaction` — old entry gets `ended_at`, new entry open; atomicity verified | — |
| TE-030-01 | SR-030 | UR-029 | IT | `TrackingIT` | `stopTracking_setsEndedAtOnActiveEntry` | `stopTracking_noActiveEntry_returns400` |
| TE-031-01 | SR-031 | UR-027/030 | IT | `QuickAccessIT` | `addEntry_insertsRowWithCorrectSortOrder` | `addEntry_tenthEntry_returns409` |
| TE-031-02 | SR-031 | UR-027/030 | IT | `QuickAccessIT` | `removeEntry_deletesRow` | — |
| TE-031-03 | SR-031 | UR-027/030 | IT | `QuickAccessIT` | `reorderEntries_updatesSortOrderForAllRows` | — |
| TE-031-04 | SR-031 | UR-027/030 | IT | `QuickAccessIT` | `listEntries_deactivatedNode_annotatedWithNonTrackableFlag` — entry not auto-removed, just flagged | — |
| TE-032-01 | SR-032 | UR-031 | IT | `TimeEntryIT` | `createRetroactive_validInput_insertsEntryWithTimezone` | `createRetroactive_startedAtAfterEndedAt_returns400` |
| TE-032-02 | SR-032 | UR-031 | IT | `TimeEntryIT` | — | `createRetroactive_noTrackAuth_returns403` |
| TE-032-03 | SR-032 | UR-031 | IT | `TimeEntryIT` | — | `createRetroactive_inactiveNode_returns409` |
| TE-033-01 | SR-033 | UR-032 | IT | `TimeEntryIT` | `editEntry_withinFreezeCutoff_updatesFields` | `editEntry_startedAtBeforeFreezeCutoff_returns409` |
| TE-033-02 | SR-033 | UR-032 | IT | `TimeEntryIT` | — | `editEntry_startedAtAfterEndedAt_returns400` |
| TE-034-01 | SR-034 | UR-033 | IT | `TimeEntryIT` | `deleteEntry_notFrozen_deletesRow` | `deleteEntry_frozen_returns409` |
| TE-035-01 | SR-035 | UR-034 | IT | `TimeEntryIT` | `duplicateEntry_insertsNewRowWithNewTimesAndCopiedDescription` | `duplicateEntry_invalidTimeRange_returns400` |

---

## Epic 4 — Reporting & export

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-037-01 | SR-037 | UR-036/037 | IT | `ReportIT` | `getReport_summaryMode_returnsAggregatedTotalsPerNode` | — |
| TE-037-02 | SR-037 | UR-036/037 | IT | `ReportIT` | `getReport_detailedMode_returnsIndividualRows` | — |
| TE-037-03 | SR-037 | UR-036/037 | IT | `ReportIT` | `getReport_restrictedToNodesVisibleToRequestingUser` — nodes without view auth absent from results | — |
| TE-037-04 | SR-037 | UR-036/037 | UT | `ReportServiceTest` | `getReport_timestampsConvertedToCompanyTimezone` — UTC entry rendered in configured timezone | — |
| TE-038-01 | SR-038 | F4.3 | IT | `ReportIT` | `getReport_detailedMode_overlappingEntries_overlapFlagSet` | — |
| TE-038-02 | SR-038 | F4.3 | IT | `ReportIT` | `getReport_detailedMode_gapBetweenConsecutiveEntries_gapFlagSet` | — |
| TE-039-01 | SR-039 | UR-038 | IT | `ReportIT` | `exportCsv_returnsFileDownloadWithCsvContentType` — `Content-Type: text/csv`, `Content-Disposition: attachment` | — |
| TE-063-01 | SR-063 | UR-052 | IT | `ReportIT` | `getMemberSummaries_returnsAggregatedPerMemberPerBucket` — totals per member per day bucket, no individual rows | — |
| TE-063-02 | SR-063 | UR-052 | IT | `ReportIT` | `getMemberSummaries_hasDataQualityIssues_trueWhenOverlapExists` | — |
| TE-063-03 | SR-063 | UR-052 | IT | `ReportIT` | `getMemberSummaries_doesNotExposeIndividualEntryDetails` — response body contains no per-entry `started_at`/`ended_at` fields | — |

---

## Epic 5 — Requests

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-040-01 | SR-040 | UR-039 | IT | `RequestIT` | `submitRequest_viewAuth_insertsRow` | `submitRequest_noViewAuth_returns403` |
| TE-041-01 | SR-041 | UR-041 | IT | `RequestIT` | `listRequests_viewAuth_returnsOpenAndClosedRequests` | `listRequests_noViewAuth_returns403` |
| TE-042-01 | SR-042 | UR-042 | IT | `RequestIT` | `closeRequest_adminAuth_setsStatusClosedAndTimestamp` — `status = 'closed'`, `resolved_at` non-null, `resolved_by` set | `closeRequest_nonAdmin_returns403` |
| TE-042-02 | SR-042 | UR-042 | IT | `RequestIT` | — | `closeRequest_alreadyClosed_returns409` |

---

## Epic 6 — Account

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-043-01 | SR-043 | UR-043 | IT | `AccountIT` | `getProfile_returnsNameLinkedProvidersAndLastReportSettings` | `getProfile_unauthenticated_returns401` |
| TE-043B-01 | SR-043b | UR-036 | IT | `AccountIT` | `saveReportSettings_persistedAsJsonbInUserProfile` — subsequent `getProfile` returns saved settings | — |
| TE-044-01 | SR-044 | UR-044 | IT | `AccountIT` | `linkProvider_insertsOauthRow` | `linkProvider_providerSubjectAlreadyLinkedToAnotherUser_returns409` |
| TE-045-01 | SR-045 | UR-045 | IT | `AccountIT` | `unlinkProvider_multipleProviders_deletesOneRow` | `unlinkProvider_lastProvider_returns409` |
| TE-046-01 | SR-046 | UR-046 | IT | `AccountIT` | `getOwnAuthorizations_returnsPathAnnotatedList` | — |
| TE-047-01 | SR-047 | UR-047 | IT | `AccountIT` | `anonymizeAccount_withTimeEntries_profileDeletedStubRetained` | — |
| TE-047-02 | SR-047 | UR-047 | IT | `AccountIT` | `anonymizeAccount_noTimeEntries_usersRowDeleted` | — |
| TE-048-01 | SR-048 | UR-048 | IT | `AboutIT` | `getAbout_unauthenticated_returnsVersionSbomOpenApiAndGdprSummary` | — |
| TE-048-02 | SR-048 | UR-048 | IT | `AboutIT` | `getAbout_authenticatedWithViewAuth_includesPrivacyNoticeUrlWhenConfigured` | `getAbout_authenticatedNoAuth_omitsPrivacyNoticeUrl` |

---

## Epic 7 — Security & audit

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-049-01 | SR-049 | UR-049 | IT | `SecurityEventIT` | `successfulLogin_insertsSecurityEventRow` | — |
| TE-049-02 | SR-049 | UR-049 | IT | `SecurityEventIT` | `grantAuth_insertsSecurityEventRow` | — |
| TE-049-03 | SR-049 | UR-049 | IT | `SecurityEventIT` | `mcpTokenGeneration_insertsSecurityEventRow` | — |
| TE-049-04 | SR-049 | UR-049 | IT | `SecurityEventIT` | `mcpTokenRevocation_insertsSecurityEventRow` | — |
| TE-050-01 | SR-050 | UR-049 | IT | `SecurityEventIT` | `listSecurityEvents_admin_supportsFilterByTypeUserAndDateRange` | `listSecurityEvents_nonAdmin_returns403` |
| TE-051-01 | SR-051 | C-3 | IT | `SecurityEventIT` | `scheduledCleanup_deletesEventsOlderThan90Days_retainsRecentEvents` — event at 91 days deleted; event at 89 days retained | — |

---

## Epic 8 — Data lifecycle

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-052-01 | SR-052 | UR-050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_setsStatusActiveAndCutoffDate` — `purge_jobs` row transitions to `active` with correct `cutoff_date` | — |
| TE-053-01 | SR-053 | UR-050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_deletesEntriesAndRequestsOlderThanCutoff` — rows before cutoff deleted; rows after retained | — |
| TE-053-02 | SR-053 | UR-050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_idempotent_resumesFromStoredCutoffDateOnRestart` — set job to `active` with stored `cutoff_date`, simulate restart, verify same cutoff used | — |
| TE-054-01 | SR-054 | UR-050 | IT | `NodePurgeJobIT` | `nodePurgeJob_triggeredAfterActivityPurge_setsStatusActiveWithCombinedCutoff` | — |
| TE-055-01 | SR-055 | UR-050 | IT | `NodePurgeJobIT` | `nodePurgeJob_deletesDeactivatedLeafNodesOlderThanCutoffWithNoReferences` | `nodePurgeJob_retainsNodeWithRemainingTimeEntries` |
| TE-055-02 | SR-055 | UR-050 | IT | `NodePurgeJobIT` | `nodePurgeJob_idempotent_resumesFromStoredCutoffOnRestart` | — |

---

## Epic 9 — MCP integration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-065-01 | SR-065 | UR-053 | IT | `McpTokenIT` | `generateToken_insertsRowWithHash_returnsRawTokenOnce` — raw token in response; `token_hash = SHA-256(rawToken)` in DB; raw token not stored | — |
| TE-065A-01 | SR-065a | UR-054 | IT | `McpTokenIT` | `listOwnTokens_returnsNonRevokedTokensOnly` — revoked token absent from list | — |
| TE-066-01 | SR-066 | UR-053 | IT | `McpTokenIT` | `mcpRequest_validToken_authenticates_updatesLastUsedAt` | `mcpRequest_invalidToken_returns401` |
| TE-066-02 | SR-066 | UR-053 | IT | `McpTokenIT` | — | `mcpRequest_revokedToken_returns401` |
| TE-066-03 | SR-066 | UR-053 | IT | `McpTokenIT` | — | `mcpRequest_expiredToken_returns401` |
| TE-067-01 | SR-067 | UR-055 | IT | `McpTokenIT` | `revokeOwnToken_setsRevokedAt_logsSecurityEvent` | — |
| TE-068-01 | SR-068 | UR-056 | IT | `McpTokenIT` | `adminListTokens_admin_returnsAllActiveWithOwnerName` | `adminListTokens_nonAdmin_returns403` |
| TE-069-01 | SR-069 | UR-057 | IT | `McpTokenIT` | `adminRevokeToken_anyUsersToken_setsRevokedAt_logsSecurityEvent` | `adminRevokeToken_nonAdmin_returns403` |
| TE-070-01 | SR-070 | F9.6 | IT | `McpToolIT` | `getNodeTree_returnsSubtreeVisibleToTokenOwner` | `mcpTool_expiredToken_returns401` |
| TE-070-02 | SR-070 | F9.6 | IT | `McpToolIT` | `getTimeEntries_ownEntries_returned` | — |
| TE-070-03 | SR-070 | F9.6 | IT | `McpToolIT` | `getTimeEntries_otherUserWithViewAuth_returnsAggregatedDailyTotalsOnly` — no per-entry detail in response | — |
| TE-070-04 | SR-070 | F9.6 | IT | `McpToolIT` | `getTrackingStatus_returnsCurrentState` | — |
| TE-070-05 | SR-070 | F9.6 | IT | `McpToolIT` | `getMemberSummaries_returnsBucketedTotalsPerMember` | — |
| TE-071-01 | SR-071 | UR-058 | E2E | `mcp-onboarding.e2e.ts` | `afterTokenGeneration_onboardingWizardShown_rawTokenVisibleOnce` | — |

---

## Cross-cutting — authentication & security

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-057-01 | SR-057 | — | IT | `AuthFlowIT` | `oidcCallback_existingUser_createsSessionAndRedirectsToRoot` | — |
| TE-057A-01 | SR-057a | UR-060 | IT | `AuthFlowIT` | `acknowledgeGdpr_withPendingSession_insertsUserProfileOauthAndDeletesInvitation` — all three DB ops in one transaction | `acknowledgeGdpr_noPendingSession_returns400` |
| TE-057A-02 | SR-057a | UR-060 | IT | `AuthFlowIT` | `acknowledgeGdpr_invitationWithdrawnBetweenCallbackAndAck_redirectsToNotInvited` | — |
| TE-058-01 | SR-058 | C-2 | IT | `AuthFlowIT` | `oidcCallback_noMatchingInvitation_redirectsToLoginError` — response does not distinguish "not found" from "expired" | — |
| TE-059-01 | SR-059 | CRA | IT | `RateLimitIT` | `requestsWithinLimit_return200` | `requestsExceedingLimit_return429` |
| TE-059-02 | SR-059 | CRA | IT | `RateLimitIT` | `rateLimitBreach_insertsSecurityEventRow` | — |
| TE-060-01 | SR-060 | CRA | SIT | `SecurityHeadersIT` | `allResponses_haveContentSecurityPolicyHeader` | — |
| TE-060-02 | SR-060 | CRA | SIT | `SecurityHeadersIT` | `allResponses_haveStrictTransportSecurityHeader` | — |
| TE-060-03 | SR-060 | CRA | SIT | `SecurityHeadersIT` | `allResponses_haveXFrameOptionsDeny` | — |
| TE-060-04 | SR-060 | CRA | SIT | `SecurityHeadersIT` | `allResponses_haveXContentTypeOptionsNosniff` | — |
| TE-060-05 | SR-060 | CRA | SIT | `SecurityHeadersIT` | `allResponses_haveReferrerPolicyNoReferrer` | — |
| TE-061-01 | SR-061 | OWASP | SIT | `SecurityHeadersIT` | `postWithValidCsrfToken_proceeds` | `postWithoutCsrfToken_returns403` |
| TE-062-01 | SR-062 | — | IT | `SseIT` | `trackingChange_dispatchedToAllSessionsOfSameUser` | — |
| TE-062-02 | SR-062 | — | IT | `SseIT` | `nodeUpdate_dispatchedToUsersWithAtLeastViewOnNode` | — |
| TE-062-03 | SR-062 | — | IT | `SseIT` | `authorizationChange_dispatchedToAffectedUser` | — |
| TE-085-01 | SR-085 | SR-084 | IT | `AuthControllerIT` | `getProviders_noAuth_returnsConfiguredProviderIds` | — |

---

## Cross-cutting — observability

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-064-01 | SR-064 | UR-059 | IT | `MetricsIT` | `prometheusEndpoint_onManagementPort_returnsJvmAndHttpMetrics` — accessible on management port, `text/plain` prometheus format, JVM heap metric present | `prometheusEndpoint_onMainPort_notFound` |
| TE-064A-01 | SR-064a | UR-059 | IT | `MetricsIT` | `customMetrics_allTenMetricsPresent_inPrometheusOutput` — each metric from SR-064a appears by name | — |
| TE-064A-02 | SR-064a | UR-059 | IT | `MetricsIT` | `purgeJobCompletionMetric_updatedAfterSuccessfulRun` — `trawhile_purge_job_last_completed_seconds{job_type="activity"}` increases after job completes | — |
| TE-064B-01 | SR-064b | F1.12 | UT | `MonitoringArtifactsTest` | `prometheusConfigFile_exists_andIsValidYaml` | — |
| TE-064B-02 | SR-064b | F1.12 | UT | `MonitoringArtifactsTest` | `alertingRulesFile_exists_andContainsRequiredAlerts` — alert names for purge-stale, db-errors, high-5xx, instance-down present | — |
| TE-064B-03 | SR-064b | F1.12 | UT | `MonitoringArtifactsTest` | `grafanaDashboard_exists_andIsValidJson_andReferencesAllCustomMetrics` — all 10 metric names from SR-064a appear in dashboard JSON | — |

---

## Cross-cutting — configuration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-088-01 | SR-088 | UR-050 | UT | `TrawhileConfigTest` | `validConfig_passesAllConstraints` | `retentionYearsLessThan2_failsValidation` |
| TE-088-02 | SR-088 | UR-050 | UT | `TrawhileConfigTest` | — | `freezeOffsetExceedsRetentionYears_failsValidation` |
| TE-088-03 | SR-088 | UR-050 | UT | `TrawhileConfigTest` | — | `nodeRetentionExtraYearsNegative_failsValidation` |
| TE-088-04 | SR-088 | UR-050 | UT | `TrawhileConfigTest` | — | `privacyNoticeUrlHttpNotHttps_failsValidation` |
| TE-089-01 | SR-089 | UR-065 | UT | `StartupValidatorTest` | `atLeastOneProvider_validatesSuccessfully` | `noProvidersConfigured_throwsIllegalStateException` |
| TE-089-02 | SR-089 | UR-065 | UT | `TrawhileConfigTest` | — | `invalidIanaTimezone_failsValidation` |
| TE-089-03 | SR-089 | UR-065 | UT | `TrawhileConfigTest` | — | `privacyNoticeUrlMalformed_failsValidation` |

---

## Frontend-only (Angular)

Tackled separately. IDs reserved for completeness.

| TE | SR | UR | Type | File | Happy test | Error / edge test |
|---|---|---|---|---|---|---|
| TE-073-01 | SR-073 | UR-061 | CT | `app.component.spec.ts` | `translationKeys_en_allPresent` | `translationKeys_de_allPresent` (repeated for de, fr, es) |
| TE-074-01 | SR-074 | UR-061 | CT | `app.component.spec.ts` | `acceptLanguageDe_rendersGerman` | `noMatchingLanguage_fallsBackToEnglish` |
| TE-075-01 | SR-075 | UR-061 | E2E | `gdpr-notice.e2e.ts` | `gdprNoticeScreen_renderedInUserLanguage` | — |
| TE-076-01 | SR-076 | UR-047 | E2E | `anonymize.e2e.ts` | `anonymizeWizard_confirmationPhrase_enablesSubmit` | `anonymizeWizard_wrongPhrase_submitDisabled` |
| TE-077-01 | SR-077 | UR-008 | E2E | `remove-user.e2e.ts` | `removalWizard_showsUserAndConsequences_requiresConfirmation` | — |
| TE-079-01 | SR-079 | UR-062 | E2E | `first-run.e2e.ts` | `bootstrapAdminFirstLogin_firstRunPromptShown_canBeDismissed` | — |
| TE-080-01 | SR-080 | UR-026 | CT | `node-picker.component.spec.ts` | `nodePicker_search_filtersTreeByName` | `nodePicker_swipeLeft_returnsToParent` |
| TE-081-01 | SR-081 | UR-010 | CT | `app.component.spec.ts` | `afterSettingsLoaded_tabTitleIsInstanceNameDashTrawhile` | `beforeSettingsLoaded_tabTitleIsTrawhile` |
| TE-083-01 | SR-083 | UR-021/022 | CT | `authorization.component.spec.ts` | `grantDialog_showsExplanatoryTextForEachLevel` | — |
| TE-084-01 | SR-084 | UR-060 | E2E | `login.e2e.ts` | `loginPage_unauthenticated_showsSignInButtonsForConfiguredProviders` | `loginPage_errorNotInvited_showsHelpMessage` |
| TE-086-01 | SR-086 | UR-063 | CT | `report-chart.component.spec.ts` | `chartView_rendersThreeChartTypes_fromInMemoryData_noAdditionalBackendCall` | — |
| TE-087-01 | SR-087 | UR-064 | CT | `report-export.component.spec.ts` | `pdfExport_rendersIn1280pxContainer_generatesFile` | — |
