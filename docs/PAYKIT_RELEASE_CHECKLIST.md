# Paykit Release Checklist - Android

Use this checklist before releasing a version with Paykit features.

## Pre-Release Verification

### Build & Compilation

- [ ] Clean build succeeds (`./gradlew clean assembleRelease`)
- [ ] All targets compile without errors
- [ ] Detekt lint passes (`./gradlew detekt`)
- [ ] No new baseline issues added

### Unit Tests

- [ ] All unit tests pass (`./gradlew testDevDebugUnitTest`)
- [ ] Test coverage is acceptable (>70%)
- [ ] No flaky tests

### Integration Tests

- [ ] Payment execution works end-to-end
- [ ] Directory queries succeed
- [ ] Subscription processing works
- [ ] Auto-pay evaluates correctly

### E2E Tests

- [ ] Session request flow works
- [ ] Payment request creation works
- [ ] Payment execution works
- [ ] Spending limits are enforced
- [ ] Cross-device auth works (with Pubky-ring)

## Functional Verification

### Core Payment Features

- [ ] Lightning payments execute successfully
- [ ] Onchain payments broadcast correctly
- [ ] Payment receipts are generated
- [ ] Receipts persist across app restart

### Directory Features

- [ ] Payment method discovery works
- [ ] Profile import works
- [ ] Contact discovery works
- [ ] Endpoint publication works

### Subscription Features

- [ ] Subscription creation works
- [ ] Next payment dates are calculated correctly
- [ ] WorkManager triggers on schedule
- [ ] Auto-pay processes approved payments
- [ ] Notifications are sent

### Session Management

- [ ] Session request works with Pubky-ring installed
- [ ] QR code generation works for cross-device
- [ ] Manual entry fallback works
- [ ] Session expiration is detected
- [ ] Session refresh works

### Spending Limits

- [ ] Limits can be set per-peer
- [ ] Global limits work
- [ ] Atomic reservation works
- [ ] Rollback on failure works
- [ ] UI displays remaining limits

## Storage Verification

- [ ] All data persists across app restart
- [ ] Contacts persist
- [ ] Subscriptions persist
- [ ] Auto-pay rules persist
- [ ] Spending limits persist
- [ ] Receipts persist

## Error Handling

- [ ] Invalid recipient shows clear error
- [ ] Network errors are handled gracefully
- [ ] Timeout errors show appropriate message
- [ ] Spending limit exceeded shows remaining
- [ ] Node not ready shows retry option

## UI/UX Verification

### Paykit Dashboard

- [ ] All sections display correctly
- [ ] Stats update in real-time
- [ ] Quick actions work
- [ ] Navigation flows correctly

### Payment Flows

- [ ] Amount input works
- [ ] QR scanner works
- [ ] Payment confirmation displays correctly
- [ ] Success/failure states display

### Settings

- [ ] Auto-pay toggle works
- [ ] Spending limits UI works
- [ ] Session status displays
- [ ] Connect Pubky-ring option works

## Performance

- [ ] No UI jank during operations
- [ ] Payment execution completes in reasonable time
- [ ] Directory queries don't block UI
- [ ] WorkManager tasks complete within time budget

## Security Considerations

- [ ] Sensitive data stored in EncryptedSharedPreferences
- [ ] No hardcoded secrets
- [ ] Preimages are not logged
- [ ] Session secrets are handled securely
- [ ] ProGuard rules preserve Paykit classes

## Known Issues

Document any known issues that won't be fixed for this release:

1. _List known issues here_
2. _Include workarounds if available_

## Configuration Verification

### Production Settings

- [ ] Pubky homeserver is production URL
- [ ] Electrum/Esplora servers are production
- [ ] Debug logging is disabled
- [ ] Test data is removed

### Build Configuration

- [ ] Release keystore configured
- [ ] `minifyEnabled = true` for release
- [ ] ProGuard rules reviewed
- [ ] Version code incremented

## Documentation

- [ ] README updated if needed
- [ ] CHANGELOG updated
- [ ] API documentation current
- [ ] Setup guide verified

## Final Steps

### Pre-Submit

1. [ ] Run full test suite one more time
2. [ ] Test on physical device (not just emulator)
3. [ ] Test with real funds (small amounts)
4. [ ] Verify Play Store screenshots are current

### Post-Submit

1. [ ] Monitor crash reports (Firebase Crashlytics)
2. [ ] Review user feedback
3. [ ] Check analytics for errors
4. [ ] Plan hotfix if critical issues found

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA | | | |
| Product | | | |

## Notes

_Add any release-specific notes here_

---

## Related Documentation

- [Setup Guide](PAYKIT_SETUP.md)
- [Architecture Overview](PAYKIT_ARCHITECTURE.md)
- [Testing Guide](PAYKIT_TESTING.md)

