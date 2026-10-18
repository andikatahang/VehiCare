# Features Research: Car Service Record App

**Domain:** Personal vehicle maintenance tracker (multi-car, service log, reminders)
**Researched:** 2026-10-18
**Sources:** App Store listings, Google Play reviews, competitor feature pages, Reddit threads, UX case studies

---

## Table Stakes

Features users expect. Missing = product feels incomplete or users leave.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Add multiple vehicles | Single-car apps feel limiting for any household with >1 vehicle | Low | Name, make, model, year, photo, odometer, VIN optional |
| Log service entries | The core action — date, mileage, service type, cost, notes | Low–Med | Quick entry is critical; >30s friction = users stop logging |
| View service history per vehicle | Users need a timeline to answer "when did I last…" | Low | Chronological list is baseline; filter by service type is bonus |
| Mileage-based reminders | Primary reason users install a maintenance app over a notes app | Med | "Every X miles" trigger on oil changes, tire rotation, etc. |
| Date-based reminders | Many services are time-based (annual inspection, insurance) | Med | Date + mileage combined is the gold standard |
| Push notifications when service due | Reminders that live only inside the app are ignored | Med | Platform-specific: iOS UNUserNotificationCenter, Android AlarmManager |
| Preset service types | Oil change, tire rotation, brake inspection — users expect these out of box | Low | Reduces friction for new entries; custom types add flexibility |
| Custom service types | Power users have vehicle-specific needs (e.g., timing belt, differential fluid) | Low | Freeform label + icon |
| Odometer tracking | Mileage-based reminders require knowing current mileage | Low | Entry on service log; optionally update separately |
| Works fully offline | Core use-case: logging at a shop with no signal | Low | Local DB (SQLDelight per PROJECT.md) — non-negotiable for VehiCare |
| Search/filter history | Users look up specific past services across multiple cars | Low | Filter by type, date range |
| Vehicle identification fields | Year, make, model, license plate, color, nickname | Low | VIN optional; nickname helps distinguish "my truck" vs "wife's car" |

---

## Differentiators

Features that set VehiCare apart. Not expected, but meaningfully valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Dual trigger reminders (mileage AND date, whichever comes first) | Most apps do one or the other; real intervals are whichever-comes-first | Med | User sets e.g. "every 5000 miles OR 6 months" — fires on first hit |
| Predicted due-date from mileage rate | Tells user "your oil change is due around March 15" even without exact mileage | Med | Calculate avg miles/day from historical entries; project forward |
| PDF / shareable service report | Resale value: buyers pay more for documented history; insurance claims | Med | PDF of one vehicle's full history; clean layout, brandable |
| Cost tracking per service | Users want to know what each car costs per year | Low | Sum of service entry costs; per-vehicle total; no analytics needed in v1 |
| Service history backdating | Log services that happened before app install | Low | Critical for onboarding — users need to import existing history |
| Minimal, clean UI with no ads | Most competitors are cluttered or ad-supported; users praise clean apps | Design | Users explicitly cite "clean UI" as reason to switch from competitors |
| No account required (truly local-first) | Users distrust cloud-first apps after seeing data disappear | Low | No auth = instant onboarding; project already commits to this |
| Reminder snooze / acknowledge flow | Reminders that require acknowledgment reduce "alarm blindness" | Low–Med | Mark as done with one tap from notification |
| Customizable reminder advance notice | "Notify me 200 miles before due" vs "notify me when exactly due" | Low | Per-reminder lead time setting |
| Multi-vehicle dashboard | At-a-glance view of all cars + what's coming due next | Med | Single screen: each car + next due service + urgency indicator |

---

## Anti-Features

Features to deliberately NOT build in v1. Based on competitor bloat analysis and PROJECT.md explicit exclusions.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Fuel log / MPG tracking | Separate workflow that doubles data entry complexity; deferred in PROJECT.md | Scope to v2 if validated |
| Cloud sync / multi-device sync | Adds auth, server costs, privacy risk, data-loss risk (top complaint in reviews); deferred in PROJECT.md | Local-first; export as safety net |
| Cost analytics / spending charts | Vanity feature for most users; deferred in PROJECT.md; adds significant UI complexity | Show cost per entry; let users do math |
| OBD-II / Bluetooth diagnostics | Requires hardware dongle; pairing friction; data users don't need for manual logs | Explicitly out of scope — not a diagnostics app |
| Community features / social feeds | "Bolted-on and distracting" — top complaint in ARI reviews; "social network of strangers' service intervals is noise" (Maintory) | Never build |
| Shop finder / appointment booking | Third-party integrations add dependencies, break, and shift app's identity | Not core to a log app |
| AI vehicle assistant / chatbot | Nice differentiator but high complexity; needs reliable data corpus | Defer to v2 if history data is mature enough |
| Document storage (insurance, registration) | Useful but separate mental model; PROJECT.md explicitly deferred | Future milestone |
| Fleet management | B2B pivot risks product-market fit; different UX entirely | Personal-use focus only |
| Web app | PROJECT.md: mobile-only for v1 | KMP already covers iOS + Android |
| Recall notifications | Requires VIN + NHTSA API integration; not core to service logging | Out of scope |
| In-app purchase upsells / ads | Top complaint across all competitors; kills trust in a record-keeping tool | Decide monetization model upfront; one-time or subscription, no ads ever |
| Excessive notifications / badges | "Wear out their welcome by week two" (Maintory); "aggressive notifications" flagged across reviews | Quiet by default; users opt in to urgency |

---

## Feature Complexity Matrix

| Feature | Complexity | Depends On | Priority |
|---------|------------|------------|----------|
| Add/manage vehicles | Low | Local DB | P0 — MVP |
| Log service entry (date, mileage, type, cost, notes) | Low | Vehicle exists | P0 — MVP |
| View service history per vehicle | Low | Service entries | P0 — MVP |
| Preset service types | Low | None | P0 — MVP |
| Custom service types | Low | Preset types UX | P0 — MVP |
| Offline-first data persistence | Low | SQLDelight | P0 — foundation |
| Date-based reminders + push notifications | Med | Service entries, platform notification APIs | P0 — MVP |
| Mileage-based reminders + push notifications | Med | Service entries, odometer tracking | P0 — MVP |
| Dual trigger (mileage AND date, whichever first) | Med | Both reminder types | P1 — high value |
| Multi-vehicle dashboard (next due per car) | Med | Reminders + vehicle list | P1 — high value |
| Cost tracking per service entry | Low | Service entry form | P1 — included in log |
| Service history backdating | Low | Service entry form (date picker) | P1 — onboarding critical |
| Customizable reminder advance notice | Low | Reminder system | P1 |
| Reminder acknowledge from notification | Med | Push notification + local DB | P1 |
| Predicted due-date from mileage rate | Med | Historical entries (need ≥2) | P2 — nice to have |
| PDF export / service report | Med | Service history | P2 — resale feature |
| Search / filter history | Low | Service history view | P2 |
| Vehicle photo / customization | Low | UI polish | P2 |

---

## Competitor Observations

### MyAutoLog
- Strong: dual mileage+date reminders, clean design, PDF export, AI assistant (v2 potential)
- Weak: subscription-only for >1 vehicle; iOS/Android with cloud sync required
- Lesson: parts inventory and AI are differentiators, but core log + reminders is the moat

### Car Cave (iOS)
- Strong: one-time purchase (no subscription), truly no login, iCloud sync optional, AI service interval suggestions
- Weak: iOS-only, no Android parity
- Lesson: "pay once, own it forever" resonates strongly with car-tracker users; no-login is a real differentiator

### Caliper
- Strong: comprehensive feature matrix published openly, CarPlay support, mileage-adaptive reminders, wash log
- Weak: feature overload; many features are Pro-only; heavy for casual users
- Lesson: don't build Caliper — users appreciate restraint

### CARFAX Car Care
- Strong: auto-imports service history from CARFAX network partners; recall alerts; trusted brand
- Weak: data privacy concerns (vehicle data sold), bugs after every update, forced odometer extrapolation causes wrong reminders
- Lesson: data trust is everything — local-first positioning is a real advantage vs CARFAX

### Simply Auto
- Strong: mature, cross-platform, mileage tracking with business reporting
- Weak: complex (does too much), subscription for multiple vehicles
- Lesson: multi-vehicle without paywall gating is a real differentiator

### The Pit Log
- Strong: enthusiast-focused, parts cabinet, iCloud optional, no subscriptions, no ads, no accounts
- Weak: iOS-only, mod/build tracking (out of scope for VehiCare)
- Lesson: "your garage, your data" positioning works — it's what enthusiast users want

### Common Failure Modes Across Competitors
1. **Data loss on phone transfer** — #1 complaint across every app. Local-first + export solves this.
2. **Aggressive notifications** — Users tune them out and eventually delete the app.
3. **Hidden paywalls after data entry** — Users feel trapped; deep anger in reviews.
4. **Odometer extrapolation errors** — Apps that guess mileage from driving patterns send wrong reminders.
5. **No account, no data recovery** — Tension between "no-login" and "what if I lose my phone." Export (PDF) is the solution.
6. **Subscription creep** — Price increases without new features lose long-time users (MileIQ $60 → $140).

### What Users Consistently Praise
- Fast, low-friction log entry
- Mileage + date dual triggers for reminders
- Clean, uncluttered interface
- No ads, no data selling
- PDF export for resale
- Responsive developer

---

## Feature Dependency Graph

```
Vehicle (add/manage)
  └── Service Entry (date, mileage, type, cost, notes)
        ├── Service History View (list, filter)
        ├── Cost per Vehicle (sum)
        ├── Mileage-based Reminder (trigger: next mileage threshold)
        │     └── Dual Trigger (mileage AND date, whichever first)
        │           └── Predicted Due Date (avg miles/day projection)
        ├── Date-based Reminder (trigger: date)
        │     └── Dual Trigger (above)
        └── PDF Export (full vehicle history)

Push Notifications
  └── Reminder Acknowledge Flow (mark done from notification)
```

## MVP Recommendation

**Build in v1 (table stakes + selected differentiators):**
1. Multi-vehicle management (add, edit, delete, photo, nickname)
2. Service entry log with: date, mileage, service type (preset + custom), cost, notes
3. Full service history per vehicle (chronological list)
4. Mileage-based AND date-based reminders — dual trigger (whichever first)
5. Push notifications when service due
6. Reminder acknowledge from notification (one-tap "done")
7. Multi-vehicle dashboard: all cars + what's due next
8. Offline-first (local SQLDelight DB)

**Defer explicitly:**
- Fuel log → v2 (already in PROJECT.md)
- Cost analytics / charts → v2
- Cloud sync → v2
- PDF export → v2 (add after history data is meaningful)
- Predicted due-date → v2 (needs sufficient history data first)
- AI features → v2+

**Never build:**
- Social/community features
- Shop finder / appointment booking
- OBD-II integration
- Fleet management

---

*Confidence: HIGH — based on multiple live app reviews, competitor feature pages, Reddit user feedback, and UX analyses.*
