# Test plan

Traceability chain: **UR → SR → TE-{SR-id}-nn**. Every SR of type F or Q maps to at least one happy-path test and, where the SR defines a rejection condition, at least one error test. SR of type C have no required TEs.

Test IDs follow the pattern `TE-{SR-id}-{nn}` where `{SR-id}` mirrors the SR identifier (e.g. `F001.F01`, `C007.F01`) and `{nn}` is a two-digit sequence within that SR.

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
| TE-F001.F01-01 | SR-F001.F01 | UR-F001 | IT | `BootstrapIT` | `bootstrapAdmin_firstLogin_grantsRootAdminAndRedirectsToGdprNotice` — BOOTSTRAP_ADMIN_EMAIL set, no existing admin, matching OIDC email → PENDING_GDPR session set, no DB row yet | — |
| TE-F001.F01-02 | SR-F001.F01 | UR-F001 | IT | `BootstrapIT` | — | `bootstrapAdmin_emailMismatch_redirectsToNotInvited` |
| TE-C006.C01-01 | SR-C006.C01 | UR-C006 | IT | `DataMinimizationIT` | `gdprRegistration_emailNotPersistedInAnyTable` — after SR-F060.F02 completes, no email string in any column across all tables | — |
| TE-F004.F01-01 | SR-F004.F01 | UR-F004 | IT | `UserManagementIT` | `listUsers_admin_returnsAllUsersWithCorrectStatus` — active, pending, and anonymised each appear with correct `status` and `name` | `listUsers_nonAdmin_returns403` |
| TE-F005.F01-01 | SR-F005.F01 | UR-F005 | IT | `InvitationIT` | `listInvitations_admin_returnsEmailInvitedByAndInvitedAt` | `listInvitations_nonAdmin_returns403` |
| TE-F006.F01-01 | SR-F006.F01 | UR-F006 | IT | `InvitationIT` | `createInvitation_newEmail_insertsUsersAndPendingRow_returnsMailtoLink` — both rows created; mailto body contains base URL and email | `createInvitation_duplicateEmail_returns409` |
| TE-F011.F01-01 | SR-F011.F01 | UR-F011 | IT | `InvitationIT` | `resendInvitation_updatesExpiresAt_returnsFreshMailtoLink` — `expires_at` updated, no new `users` row, UUID unchanged | — |
| TE-F060.F01-01 | SR-F060.F01 | UR-F060 | IT | `AuthFlowIT` | `oidcCallback_matchingPendingInvitation_storesSessionNoDatabaseWrite` — session contains `pending_invitations.id`, `user_id`, `provider`, `subject`, `name`; no new DB rows | `oidcCallback_expiredInvitation_redirectsToNotInvited` |
| TE-F007.F01-01 | SR-F007.F01 | UR-F007 | IT | `InvitationIT` | `withdrawInvitation_triggersScrubbingState` — `pending_invitations` row deleted, `node_authorizations` deleted, stub retained when time entries exist | `withdrawInvitation_nonAdmin_returns403` |
| TE-C010.C01-01 | SR-C010.C01 | UR-C010 | IT | `InvitationIT` | `expireInvitations_expiredRows_triggersScrubbingForEach` — rows with `expires_at` in the past transition to scrubbing state after `UserService.expireInvitations()` | — |
| TE-F070.F01-01 | SR-F070.F01 | UR-F070 | IT | `UserScrubbingIT` | `scrubUser_withTimeRecords_endsActiveRecord_deletesProfile_retainsStub` — active record gets `ended_at`; `node_authorizations` deleted; `user_profile` deleted; `users` row retained | — |
| TE-F070.F01-02 | SR-F070.F01 | UR-F070 | IT | `UserScrubbingIT` | `scrubUser_noTimeRecords_noRequests_deletesUsersRow` | — |
| TE-F070.F01-03 | SR-F070.F01 | UR-F070 | IT | `UserScrubbingIT` | `scrubUser_revokesMcpTokens_setsRevokedAt` | — |
| TE-F008.F01-01 | SR-F008.F01 | UR-F008 | IT | `UserManagementIT` | `removeUser_admin_triggersScrubbing` | `removeUser_nonAdmin_returns403` |
| TE-F009.F01-01 | SR-F009.F01 | UR-F009 | IT | `UserManagementIT` | `getUserAuthorizations_admin_returnsPathAnnotatedAssignments` — each row includes full ancestor path from root to granted node | `getUserAuthorizations_nonAdmin_returns403` |
| TE-F009.F02-01 | SR-F009.F02 | UR-F009 | IT | `UserManagementIT` | `manageUserAuthorizations_fromUserView_grantsAndRevokesWithinAdminScope` | `manageUserAuthorizations_fromUserView_outOfScopeNode_returns403` |
| TE-F010.F01-01 | SR-F010.F01 | UR-F010 | IT | `SettingsIT` | `getSettings_authenticatedUser_returnsResolvedConfig` — response includes `name`, `timezone`, `freezeOffsetYears`, `effectiveFreezeCutoff`, `retentionYears`, `nodeRetentionExtraYears`, `privacyNoticeUrl` | `getSettings_unauthenticated_returns401` |

---

## Epic 2 — Node administration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F014.F01-01 | SR-F014.F01 | UR-F014 | IT | `NodeIT` | `getNode_viewAuth_returnsNodeDetailsAndDirectChildren` | `getNode_noAuth_returns403` |
| TE-F015.F01-01 | SR-F015.F01 | UR-F015 | IT | `NodeIT` | `createChild_adminAuth_insertsNodeWithSortOrderOneGreaterThanMax` | `createChild_nonAdmin_returns403` |
| TE-F016.F01-01 | SR-F016.F01 | UR-F016 | IT | `NodeIT` | `updateNode_name_persistedInDatabase` | `uploadLogo_exceeds256KB_returns400` |
| TE-F016.F01-02 | SR-F016.F01 | UR-F016 | IT | `NodeIT` | `uploadLogo_validPng_stored` — verify stored bytes and correct `Content-Type` on retrieval | `uploadLogo_imageGifMimeType_returns400` |
| TE-F017.F01-01 | SR-F017.F01 | UR-F017 | IT | `NodeIT` | `reorderChildren_updatesAllSortOrderValues` — all sibling `sort_order` values match submitted order | `reorderChildren_nonAdmin_returns403` |
| TE-F018.F01-01 | SR-F018.F01 | UR-F018 | IT | `NodeIT` | `deactivateNode_leafNode_setsIsActiveFalseAndDeactivatedAt` | `deactivateNode_withActiveChildren_returns409` |
| TE-F018.F01-02 | SR-F018.F01 | UR-F018 | IT | `NodeIT` | `deactivateNode_withActiveTimeRecord_notBlocked` — running record on the node itself does not block deactivation | — |
| TE-F019.F01-01 | SR-F019.F01 | UR-F019 | IT | `NodeIT` | `reactivateNode_setsIsActiveTrueAndClearsDeactivatedAt` | `reactivateNode_nonAdmin_returns403` |
| TE-F020.F01-01 | SR-F020.F01 | UR-F020 | IT | `NodeIT` | `moveNode_adminOnBothEnds_updatesParentIdAndAppendsSortOrder` | `moveNode_destinationIsOwnDescendant_returns409` |
| TE-F020.F01-02 | SR-F020.F01 | UR-F020 | IT | `NodeIT` | — | `moveNode_noAdminOnDestination_returns403` |
| TE-F021.F01-01 | SR-F021.F01 | UR-F021 | IT | `AuthorizationIT` | `grantAuth_admin_insertsNodeAuthorizationsRow` — upsert: second grant with different level updates existing row | `grantAuth_nonAdmin_returns403` |
| TE-F022.F01-01 | SR-F022.F01 | UR-F022 | IT | `AuthorizationIT` | `revokeAuth_admin_deletesRow` | `revokeAuth_lastAdmin_returns409` |
| TE-F022.F01-02 | SR-F022.F01 | UR-F022 | IT | `AuthorizationIT` | — | `revokeAuth_nonAdmin_returns403` |
| TE-F023.F01-01 | SR-F023.F01 | UR-F023 | IT | `AuthorizationIT` | `listNodeAuthorizations_admin_distinguishesDirectFromInherited` — rows where `node_id` matches queried node marked `direct`; ancestor rows marked `inherited` | `listNodeAuthorizations_nonAdmin_returns403` |

---

## Epic 3 — Time tracking

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F024.F01-01 | SR-F024.F01 | UR-F024 | IT | `TrackingIT` | `getStatus_activeEntry_returnsNodePathAndElapsedTime` | — |
| TE-F024.F01-02 | SR-F024.F01 | UR-F024 | IT | `TrackingIT` | `getStatus_noActiveEntry_returnsEmptyTrackingState` | — |
| TE-F025.F01-01 | SR-F025.F01 | UR-F025 | IT | `TrackingIT` | `recentEntries_overlappingPair_bothFlaggedWithOverlap` | — |
| TE-F025.F01-02 | SR-F025.F01 | UR-F025 | IT | `TrackingIT` | `recentEntries_gapBetweenConsecutiveEntries_flaggedWithGap` | — |
| TE-F026.F01-01 | SR-F026.F01 | UR-F026 | IT | `TrackingIT` | `startTracking_trackAuthOnActiveLeafNode_insertsOpenEntry` — `ended_at IS NULL`, correct `timezone` stored | `startTracking_inactiveNode_returns409` |
| TE-F026.F01-02 | SR-F026.F01 | UR-F026 | IT | `TrackingIT` | — | `startTracking_nodeWithActiveChildren_returns409` |
| TE-F026.F01-03 | SR-F026.F01 | UR-F026 | IT | `TrackingIT` | — | `startTracking_noTrackAuth_returns403` |
| TE-F028.F01-01 | SR-F028.F01 | UR-F028 | IT | `TrackingIT` | `switchTracking_endsExistingEntryAndInsertsNew_withinSingleTransaction` — old entry gets `ended_at`, new entry open; atomicity verified | — |
| TE-F029.F01-01 | SR-F029.F01 | UR-F029 | IT | `TrackingIT` | `stopTracking_setsEndedAtOnActiveEntry` | `stopTracking_noActiveEntry_returns400` |
| TE-F027.F01-01 | SR-F027.F01 | UR-F027/UR-F030 | IT | `QuickAccessIT` | `listEntries_deactivatedNode_annotatedWithNonTrackableFlag` — entry not auto-removed, just flagged | — |
| TE-F030.F01-01 | SR-F030.F01 | UR-F030 | IT | `QuickAccessIT` | `addEntry_insertsRowWithCorrectSortOrder` | `addEntry_tenthEntry_returns409` |
| TE-F030.F01-02 | SR-F030.F01 | UR-F030 | IT | `QuickAccessIT` | `removeEntry_deletesRow` | — |
| TE-F030.F01-03 | SR-F030.F01 | UR-F030 | IT | `QuickAccessIT` | `reorderEntries_updatesSortOrderForAllRows` | — |
| TE-F031.F01-01 | SR-F031.F01 | UR-F031 | IT | `TimeRecordIT` | `createRetroactive_validInput_insertsRecordWithTimezone` | `createRetroactive_startedAtAfterEndedAt_returns400` |
| TE-F031.F01-02 | SR-F031.F01 | UR-F031 | IT | `TimeRecordIT` | — | `createRetroactive_noTrackAuth_returns403` |
| TE-F031.F01-03 | SR-F031.F01 | UR-F031 | IT | `TimeRecordIT` | — | `createRetroactive_inactiveNode_returns409` |
| TE-F032.F01-01 | SR-F032.F01 | UR-F032 | IT | `TimeRecordIT` | `editRecord_withinFreezeCutoff_updatesFields` | `editRecord_startedAtBeforeFreezeCutoff_returns409` |
| TE-F032.F01-02 | SR-F032.F01 | UR-F032 | IT | `TimeRecordIT` | — | `editRecord_startedAtAfterEndedAt_returns400` |
| TE-F032.F01-03 | SR-F032.F01 | UR-F032 | IT | `TimeRecordIT` | — | `editRecord_reassignToInactiveNode_returns409` |
| TE-F032.F01-04 | SR-F032.F01 | UR-F032 | IT | `TimeRecordIT` | — | `editRecord_reassignToNodeWithActiveChildren_returns409` |
| TE-F032.F01-05 | SR-F032.F01 | UR-F032 | IT | `TimeRecordIT` | — | `editRecord_reassignWithoutTrackAuth_returns403` |
| TE-F033.F01-01 | SR-F033.F01 | UR-F033 | IT | `TimeRecordIT` | `deleteRecord_notFrozen_deletesRow` | `deleteRecord_frozen_returns409` |
| TE-F034.F01-01 | SR-F034.F01 | UR-F034 | IT | `TimeRecordIT` | `duplicateRecord_insertsNewRowWithNewTimesAndCopiedDescription` | `duplicateRecord_invalidTimeRange_returns400` |

---

## Epic 4 — Reporting & export

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F036.F01-01 | SR-F036.F01 | UR-F036 | IT | `ReportIT` | `getReport_summaryMode_returnsAggregatedTotalsPerNode` | — |
| TE-F036.F01-02 | SR-F036.F01 | UR-F036 | IT | `ReportIT` | `getReport_detailedMode_returnsIndividualRows` | — |
| TE-F036.F01-03 | SR-F036.F01 | UR-F036 | IT | `ReportIT` | `getReport_restrictedToNodesVisibleToRequestingUser` — nodes without view auth absent from results | — |
| TE-F036.F01-04 | SR-F036.F01 | UR-F036 | UT | `ReportServiceTest` | `getReport_timestampsConvertedToCompanyTimezone` — UTC entry rendered in configured timezone | — |
| TE-F036.F02-01 | SR-F036.F02 | UR-F036 | IT | `ReportIT` | `getReport_detailedMode_overlappingEntries_overlapFlagSet` | — |
| TE-F036.F02-02 | SR-F036.F02 | UR-F036 | IT | `ReportIT` | `getReport_detailedMode_gapBetweenConsecutiveEntries_gapFlagSet` | — |
| TE-F037.F01-01 | SR-F037.F01 | UR-F037 | CT | `report-view.component.spec.ts` | `reportView_toggleSwitchesBetweenSummaryDetailedAndChart_withoutChangingFilters` | — |
| TE-F038.F01-01 | SR-F038.F01 | UR-F038 | IT | `ReportIT` | `exportCsv_returnsFileDownloadWithCsvContentType` — `Content-Type: text/csv`, `Content-Disposition: attachment` | — |
| TE-F052.F01-01 | SR-F052.F01 | UR-F052 | IT | `ReportIT` | `getMemberSummaries_returnsAggregatedPerMemberPerBucket` — totals per member per day bucket, no individual rows | — |
| TE-F052.F01-02 | SR-F052.F01 | UR-F052 | IT | `ReportIT` | `getMemberSummaries_hasDataQualityIssues_trueWhenOverlapExists` | — |
| TE-F052.F01-03 | SR-F052.F01 | UR-F052 | IT | `ReportIT` | `getMemberSummaries_doesNotExposeIndividualEntryDetails` — response body contains no per-entry `started_at`/`ended_at` fields | — |
| TE-F052.F01-04 | SR-F052.F01 | UR-F052 | IT | `ReportIT` | `getMemberSummaries_filterByDataQualityFlag_returnsOnlyMatchingBuckets` | — |

---

## Epic 5 — Requests

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F039.F01-01 | SR-F039.F01 | UR-F039 | IT | `RequestIT` | `submitRequest_viewAuth_insertsRow` | `submitRequest_noViewAuth_returns403` |
| TE-F041.F01-01 | SR-F041.F01 | UR-F041 | IT | `RequestIT` | `listRequests_viewAuth_returnsOpenAndClosedRequests` | `listRequests_noViewAuth_returns403` |
| TE-F042.F01-01 | SR-F042.F01 | UR-F042 | IT | `RequestIT` | `closeRequest_adminAuth_setsStatusClosedAndTimestamp` — `status = 'closed'`, `resolved_at` non-null, `resolved_by` set | `closeRequest_nonAdmin_returns403` |
| TE-F042.F01-02 | SR-F042.F01 | UR-F042 | IT | `RequestIT` | — | `closeRequest_alreadyClosed_returns409` |

---

## Epic 6 — Account

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F043.F01-01 | SR-F043.F01 | UR-F043 | IT | `AccountIT` | `getProfile_returnsNameLinkedProvidersAndLastReportSettings` | `getProfile_unauthenticated_returns401` |
| TE-F046.F01-01 | SR-F046.F01 | UR-F046 | IT | `AccountIT` | `getOwnAuthorizations_returnsPathAnnotatedList` | — |
| TE-F066.F01-01 | SR-F066.F01 | UR-F066 | IT | `AccountIT` | `saveReportSettings_persistedAsJsonbInUserProfile` — subsequent `getProfile` returns saved settings | — |
| TE-F044.F01-01 | SR-F044.F01 | UR-F044 | IT | `AccountIT` | `linkProvider_insertsOauthRow` | `linkProvider_providerSubjectAlreadyLinkedToAnotherUser_returns409` |
| TE-F045.F01-01 | SR-F045.F01 | UR-F045 | IT | `AccountIT` | `unlinkProvider_multipleProviders_deletesOneRow` | `unlinkProvider_lastProvider_returns409` |
| TE-F047.F01-01 | SR-F047.F01 | UR-F047 | IT | `AccountIT` | `anonymizeAccount_withTimeEntries_profileDeletedStubRetained` | — |
| TE-F047.F01-02 | SR-F047.F01 | UR-F047 | IT | `AccountIT` | `anonymizeAccount_noTimeEntries_usersRowDeleted` | — |
| TE-F048.F01-01 | SR-F048.F01 | UR-F048 | IT | `AboutIT` | `getAbout_unauthenticated_returnsVersionSbomOpenApiAndGdprSummary` | — |
| TE-F048.F01-02 | SR-F048.F01 | UR-F048 | IT | `AboutIT` | `getAbout_authenticatedWithViewAuth_includesPrivacyNoticeUrlWhenConfigured` | `getAbout_authenticatedNoAuth_omitsPrivacyNoticeUrl` |

---

## Epic 7 — Security & audit

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F049.F01-01 | SR-F049.F01 | UR-F049 | IT | `SecurityEventIT` | `successfulLogin_insertsSecurityEventRow` | — |
| TE-F049.F01-02 | SR-F049.F01 | UR-F049 | IT | `SecurityEventIT` | `grantAuth_insertsSecurityEventRow` | — |
| TE-F049.F01-03 | SR-F049.F01 | UR-F049 | IT | `SecurityEventIT` | `mcpTokenGeneration_insertsSecurityEventRow` | — |
| TE-F049.F01-04 | SR-F049.F01 | UR-F049 | IT | `SecurityEventIT` | `mcpTokenRevocation_insertsSecurityEventRow` | — |
| TE-F049.F02-01 | SR-F049.F02 | UR-F049 | IT | `SecurityEventIT` | `listSecurityEvents_admin_supportsFilterByTypeUserAndDateRange` | `listSecurityEvents_nonAdmin_returns403` |
| TE-C007.F01-01 | SR-C007.F01 | UR-C007 | IT | `SecurityEventIT` | `scheduledCleanup_deletesEventsOlderThan90Days_retainsRecentEvents` — event at 91 days deleted; event at 89 days retained | — |

---

## Epic 8 — Data lifecycle

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F050.F01-01 | SR-F050.F01 | UR-F050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_setsStatusActiveAndCutoffDate` — `purge_jobs` row transitions to `active` with correct `cutoff_date` | — |
| TE-F050.F01-02 | SR-F050.F01 | UR-F050 | UT | `SchedulingConfigTest` | `activityPurgeJob_usesConfiguredPurgeCronInCompanyTimezone` | — |
| TE-F050.F02-01 | SR-F050.F02 | UR-F050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_deletesEntriesAndRequestsOlderThanCutoff` — rows before cutoff deleted; rows after retained | — |
| TE-F050.F02-02 | SR-F050.F02 | UR-F050 | IT | `ActivityPurgeJobIT` | `activityPurgeJob_idempotent_resumesFromStoredCutoffDateOnRestart` — set job to `active` with stored `cutoff_date`, simulate restart, verify same cutoff used | — |
| TE-F050.F03-01 | SR-F050.F03 | UR-F050 | IT | `NodePurgeJobIT` | `nodePurgeJob_triggeredAfterActivityPurge_setsStatusActiveWithCombinedCutoff` | — |
| TE-F050.F04-01 | SR-F050.F04 | UR-F050 | IT | `NodePurgeJobIT` | `nodePurgeJob_deletesDeactivatedLeafNodesOlderThanCutoffWithNoReferences` | `nodePurgeJob_retainsNodeWithRemainingTimeEntries` |
| TE-F050.F04-02 | SR-F050.F04 | UR-F050 | IT | `NodePurgeJobIT` | `nodePurgeJob_idempotent_resumesFromStoredCutoffOnRestart` | — |

---

## Epic 9 — MCP integration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F053.F01-01 | SR-F053.F01 | UR-F053 | IT | `McpTokenIT` | `generateToken_insertsRowWithHash_returnsRawTokenOnce` — raw token in response; `token_hash = SHA-256(rawToken)` in DB; raw token not stored | — |
| TE-F054.F01-01 | SR-F054.F01 | UR-F054 | IT | `McpTokenIT` | `listOwnTokens_returnsNonRevokedTokensOnly` — revoked token absent from list | — |
| TE-F053.F02-01 | SR-F053.F02 | UR-F053 | IT | `McpTokenIT` | `mcpRequest_validToken_authenticates_updatesLastUsedAt` | `mcpRequest_invalidToken_returns401` |
| TE-F053.F02-02 | SR-F053.F02 | UR-F053 | IT | `McpTokenIT` | — | `mcpRequest_revokedToken_returns401` |
| TE-F053.F02-03 | SR-F053.F02 | UR-F053 | IT | `McpTokenIT` | — | `mcpRequest_expiredToken_returns401` |
| TE-F055.F01-01 | SR-F055.F01 | UR-F055 | IT | `McpTokenIT` | `revokeOwnToken_setsRevokedAt_logsSecurityEvent` | — |
| TE-F056.F01-01 | SR-F056.F01 | UR-F056 | IT | `McpTokenIT` | `adminListTokens_admin_returnsAllActiveWithOwnerName` | `adminListTokens_nonAdmin_returns403` |
| TE-F057.F01-01 | SR-F057.F01 | UR-F057 | IT | `McpTokenIT` | `adminRevokeToken_anyUsersToken_setsRevokedAt_logsSecurityEvent` | `adminRevokeToken_nonAdmin_returns403` |
| TE-F069.F01-01 | SR-F069.F01 | UR-F069 | IT | `McpToolIT` | `getNodeTree_returnsSubtreeVisibleToTokenOwner` | `mcpTool_expiredToken_returns401` |
| TE-F069.F01-02 | SR-F069.F01 | UR-F069 | IT | `McpToolIT` | `getTimeRecords_ownRecords_returned` | — |
| TE-F069.F01-03 | SR-F069.F01 | UR-F069 | IT | `McpToolIT` | `getTimeRecords_otherUserWithViewAuth_returnsAggregatedDailyTotalsOnly` — no per-record detail in response | — |
| TE-F069.F01-04 | SR-F069.F01 | UR-F069 | IT | `McpToolIT` | `getTrackingStatus_returnsCurrentState` | — |
| TE-F069.F01-05 | SR-F069.F01 | UR-F069 | IT | `McpToolIT` | `getMemberSummaries_returnsBucketedTotalsPerMember` | — |
| TE-F058.F01-01 | SR-F058.F01 | UR-F058 | E2E | `mcp-onboarding.e2e.ts` | `afterTokenGeneration_onboardingWizardShown_rawTokenVisibleOnce` | — |

---

## Cross-cutting — authentication & security

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F067.F01-01 | SR-F067.F01 | — | IT | `AuthFlowIT` | `oidcCallback_existingUser_createsSessionAndRedirectsToRoot` | — |
| TE-F060.F02-01 | SR-F060.F02 | UR-F060 | IT | `AuthFlowIT` | `acknowledgeGdpr_withPendingSession_insertsUserProfileOauthAndDeletesInvitation` — all three DB ops in one transaction | `acknowledgeGdpr_noPendingSession_returns400` |
| TE-F060.F02-02 | SR-F060.F02 | UR-F060 | IT | `AuthFlowIT` | `acknowledgeGdpr_invitationWithdrawnBetweenCallbackAndAck_redirectsToNotInvited` | — |
| TE-F060.F02-03 | SR-F060.F02 | UR-F060 | IT | `AuthFlowIT` | `acknowledgeGdpr_privacyNoticeUrlReturnedOnlyWhenUserHasEffectiveAuthorization` | — |
| TE-C002.F01-01 | SR-C002.F01 | UR-C002 | IT | `AuthFlowIT` | `oidcCallback_noMatchingInvitation_redirectsToLoginError` — response does not distinguish "not found" from "expired" | — |
| TE-C011.C01-01 | SR-C011.C01 | UR-C011 | IT | `RateLimitIT` | `requestsWithinLimit_return200` | `requestsExceedingLimit_return429` |
| TE-C011.C01-02 | SR-C011.C01 | UR-C011 | IT | `RateLimitIT` | `rateLimitBreach_insertsSecurityEventRow` | — |
| TE-C012.C01-01 | SR-C012.C01 | UR-C012 | SIT | `SecurityHeadersIT` | `allResponses_haveContentSecurityPolicyHeader` | — |
| TE-C012.C01-02 | SR-C012.C01 | UR-C012 | SIT | `SecurityHeadersIT` | `allResponses_haveStrictTransportSecurityHeader` | — |
| TE-C012.C01-03 | SR-C012.C01 | UR-C012 | SIT | `SecurityHeadersIT` | `allResponses_haveXFrameOptionsDeny` | — |
| TE-C012.C01-04 | SR-C012.C01 | UR-C012 | SIT | `SecurityHeadersIT` | `allResponses_haveXContentTypeOptionsNosniff` | — |
| TE-C012.C01-05 | SR-C012.C01 | UR-C012 | SIT | `SecurityHeadersIT` | `allResponses_haveReferrerPolicyNoReferrer` | — |
| TE-C013.C01-01 | SR-C013.C01 | UR-C013 | SIT | `SecurityHeadersIT` | `postWithValidCsrfToken_proceeds` | `postWithoutCsrfToken_returns403` |
| TE-F068.F01-01 | SR-F068.F01 | UR-F068 | IT | `SseIT` | `trackingChange_dispatchedToAllSessionsOfSameUser` | — |
| TE-F068.F01-02 | SR-F068.F01 | UR-F068 | IT | `SseIT` | `nodeUpdate_dispatchedToUsersWithAtLeastViewOnNode` | — |
| TE-F068.F01-03 | SR-F068.F01 | UR-F068 | IT | `SseIT` | `authorizationChange_dispatchedToAffectedUser` | — |
| TE-F067.F02-01 | SR-F067.F02 | UR-F067 | IT | `AuthControllerIT` | `getProviders_noAuth_returnsConfiguredProviderIds` | — |

---

## Cross-cutting — observability

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F059.F01-01 | SR-F059.F01 | UR-F059 | IT | `MetricsIT` | `prometheusEndpoint_onManagementPort_returnsJvmAndHttpMetrics` — accessible on management port, `text/plain` prometheus format, JVM heap metric present | `prometheusEndpoint_onMainPort_notFound` |
| TE-F059.F02-01 | SR-F059.F02 | UR-F059 | IT | `MetricsIT` | `customMetrics_allTenMetricsPresent_inPrometheusOutput` — each metric from SR-F059.F02 appears by name | — |
| TE-F059.F02-02 | SR-F059.F02 | UR-F059 | IT | `MetricsIT` | `purgeJobCompletionMetric_updatedAfterSuccessfulRun` — `trawhile_purge_job_last_completed_seconds{job_type="activity"}` increases after job completes | — |
| TE-F059.F03-01 | SR-F059.F03 | UR-F059 | UT | `MonitoringArtifactsTest` | `prometheusConfigFile_exists_andIsValidYaml` | — |
| TE-F059.F03-02 | SR-F059.F03 | UR-F059 | UT | `MonitoringArtifactsTest` | `alertingRulesFile_exists_andContainsRequiredAlerts` — alert names for purge-stale, db-errors, high-5xx, instance-down present | — |
| TE-F059.F03-03 | SR-F059.F03 | UR-F059 | UT | `MonitoringArtifactsTest` | `grafanaDashboard_exists_andIsValidJson_andReferencesAllCustomMetrics` — all 10 metric names from SR-F059.F02 appear in dashboard JSON | — |

---

## Cross-cutting — configuration

| TE | SR | UR | Type | Class | Happy test | Error test |
|---|---|---|---|---|---|---|
| TE-F050.F05-01 | SR-F050.F05 | UR-F050 | UT | `TrawhileConfigTest` | `validConfig_passesAllConstraints` | `retentionYearsLessThan2_failsValidation` |
| TE-F050.F05-02 | SR-F050.F05 | UR-F050 | UT | `TrawhileConfigTest` | — | `freezeOffsetExceedsRetentionYears_failsValidation` |
| TE-F050.F05-03 | SR-F050.F05 | UR-F050 | UT | `TrawhileConfigTest` | — | `nodeRetentionExtraYearsNegative_failsValidation` |
| TE-F050.F05-04 | SR-F050.F05 | UR-F050 | UT | `TrawhileConfigTest` | — | `privacyNoticeUrlHttpNotHttps_failsValidation` |
| TE-F050.F05-05 | SR-F050.F05 | UR-F050 | UT | `TrawhileConfigTest` | — | `purgeCronInvalid_failsValidation` |
| TE-F065.F01-01 | SR-F065.F01 | UR-F065 | UT | `StartupValidatorTest` | `atLeastOneProvider_validatesSuccessfully` | `noProvidersConfigured_throwsIllegalStateException` |
| TE-F065.F01-02 | SR-F065.F01 | UR-F065 | UT | `TrawhileConfigTest` | — | `invalidIanaTimezone_failsValidation` |
| TE-F065.F01-03 | SR-F065.F01 | UR-F065 | UT | `TrawhileConfigTest` | — | `privacyNoticeUrlMalformed_failsValidation` |
| TE-F065.F01-04 | SR-F065.F01 | UR-F065 | UT | `StartupValidatorTest` | — | `startupValidation_errorMessageIdentifiesInvalidPropertyOrConstraint` |

---

## Frontend-only (Angular)

Tackled separately. IDs reserved for completeness.

| TE | SR | UR | Type | File | Happy test | Error / edge test |
|---|---|---|---|---|---|---|
| TE-F061.F01-01 | SR-F061.F01 | UR-F061 | CT | `app.component.spec.ts` | `translationKeys_en_allPresent` | `translationKeys_de_allPresent` (repeated for de, fr, es) |
| TE-F061.F02-01 | SR-F061.F02 | UR-F061 | CT | `app.component.spec.ts` | `acceptLanguageDe_rendersGerman` | `noMatchingLanguage_fallsBackToEnglish` |
| TE-F061.F03-01 | SR-F061.F03 | UR-F061 | E2E | `gdpr-notice.e2e.ts` | `gdprNoticeScreen_renderedInUserLanguage` | — |
| TE-F047.F03-01 | SR-F047.F03 | UR-F047 | E2E | `anonymize.e2e.ts` | `anonymizeWizard_confirmationPhrase_enablesSubmit` | `anonymizeWizard_wrongPhrase_submitDisabled` |
| TE-F008.F02-01 | SR-F008.F02 | UR-F008 | E2E | `remove-user.e2e.ts` | `removalWizard_showsUserAndConsequences_requiresConfirmation` | — |
| TE-F062.F01-01 | SR-F062.F01 | UR-F062 | E2E | `first-run.e2e.ts` | `bootstrapAdminFirstLogin_noOtherMembers_invitePromptShown` | — |
| TE-F062.F01-02 | SR-F062.F01 | UR-F062 | E2E | `first-run.e2e.ts` | `systemAdminLogin_noOtherMembers_invitePromptShownAgain` | `systemAdminLogin_otherMembersExist_invitePromptNotShown` |
| TE-F026.F02-01 | SR-F026.F02 | UR-F026 | CT | `node-picker.component.spec.ts` | `nodePicker_search_filtersTreeByName` | `nodePicker_swipeLeft_returnsToParent` |
| TE-F010.F02-01 | SR-F010.F02 | UR-F010 | CT | `app.component.spec.ts` | `afterSettingsLoaded_tabTitleIsInstanceNameDashTrawhile` | `beforeSettingsLoaded_tabTitleIsTrawhile` |
| TE-F021.F02-01 | SR-F021.F02 | UR-F021/UR-F022 | CT | `authorization.component.spec.ts` | `grantDialog_showsExplanatoryTextForEachLevel` | — |
| TE-F060.F03-01 | SR-F060.F03 | UR-F060 | E2E | `login.e2e.ts` | `loginPage_unauthenticated_showsSignInButtonsForConfiguredProviders` | `loginPage_errorNotInvited_showsHelpMessage` |
| TE-F063.F01-01 | SR-F063.F01 | UR-F063 | CT | `report-chart.component.spec.ts` | `chartView_rendersThreeChartTypes_fromInMemoryData_noAdditionalBackendCall` | — |

| TE-F064.F01-01 | SR-F064.F01 | UR-F064 | CT | `report-export.component.spec.ts` | `pdfExport_rendersIn1280pxContainer_generatesFile` | — |
