---
quick_id: 261024-nf0
description: Fix the builder error while run the App! make it no mistakes
status: complete
date: 2026-10-24
commit: 329fd91
---

# Quick Task 261024-nf0: Fix the builder error — Summary

## Outcome

`:androidApp:assembleDebug` builds again. The failure came from the in-progress repository-pattern
refactor, which had moved repository interfaces, implementations and mappers into new packages but
left several call sites pointing at the old `com.vehicare.data` locations — plus one mapper import
that was dropped entirely.

## Root causes fixed

| # | File | Problem | Fix |
|---|------|---------|-----|
| 1 | `data/repository/ServiceEntryRepositoryImpl.kt` | `toServiceType()` unresolved — the mapper extension moved to `data.mapper` and was never imported | Added `import com.vehicare.data.mapper.toServiceType` |
| 2 | `notifications/ReminderDelivery.kt` | Imported `ReminderRepository`, `ReminderRepositoryImpl`, `VehicleRepository`, `VehicleRepositoryImpl` from the deleted `com.vehicare.data` package | Repointed interfaces to `com.vehicare.domain.repository`, implementations to `com.vehicare.data.repository` |
| 3 | `commonTest/notifications/ReminderDeliveryTest.kt` | Same stale interface imports | Repointed to `domain.repository` |
| 4 | `commonTest/data/{ServiceEntry,Vehicle}EntityMapperTest.kt` | Tests in package `com.vehicare.data` relied on same-package lookup of mapper extensions | Added explicit `data.mapper` imports |
| 5 | `iosTest/data/{Reminder,Vehicle,ServiceEntry}RepositoryTest.kt` | Same-package lookup of the moved `*RepositoryImpl` classes | Added explicit `data.repository` imports |
| 6 | `iosTest/data/ServiceEntryDeletionTest.kt`, `iosTest/ui/vehicles/VehicleDeletionTest.kt` | Impl classes imported from `domain.repository` (they live in `data.repository`) | Corrected imports |
| 7 | `domain/Vehicle.kt`, `domain/ServiceEntry.kt` | KDoc still pointed at the old mapper package | Updated to `com.vehicare.data.mapper` |

## Verification

| Gate | Result |
|------|--------|
| `:androidApp:assembleDebug` | **BUILD SUCCESSFUL** |
| `:shared:testAndroidHostTest` | **256 tests, 0 failures, 0 errors, 4 skipped** (the 4 target-scoped `ReminderDeliveryTest` cases that are expected to skip on the JVM host) |
| `:shared:iosSimulatorArm64Test` | **318 tests, 0 failures, 0 errors, 0 skipped** |

No production behaviour changed — package moves and imports only. The `shared/build.gradle.kts`
generated-resource task wiring from the working tree was kept as-is and the configuration cache
still builds cleanly.

## Notes

- Worktree isolation was intentionally not used: the build break only exists in the uncommitted
  working tree, so a worktree forked from `HEAD` would have had nothing to fix.
- Commit: `329fd91` — `refactor(shared): finish repository/mapper package move`.
- Not committed here: Phase 3 UAT/verification work, which remains the next GSD step.
