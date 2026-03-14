# EUC App UI Competitive Analysis

Comparative analysis of 5 EUC companion apps conducted March 2026. Covers UI/UX strengths, weaknesses, and takeaways for FreeWheel.

## Apps Analyzed

| App | Platform | Primary Wheel Brands |
|-----|----------|---------------------|
| **FreeWheel** | Android + iOS (KMP) | All (multi-brand) |
| **Leaperkim** | Android | Veteran, Leaperkim |
| **InMotion** | Android + iOS (Flutter) | InMotion |
| **DarknessBot** | iOS | All (multi-brand) |
| **Kingsong** | Android + iOS | Kingsong |

---

## Per-App Assessment

### Leaperkim

**Strengths:**
- Battery health UI is the gold standard. 3-column cell voltage grid (36 cells, indexed) with max/min indicators and voltage delta at top -- you can spot a dying cell at a glance. 6-point temperature readout is thorough.
- Dashboard information density is well-balanced. Large speed circle dominates, battery/temp/current secondary. Doesn't try to show everything at once -- second page has the full 18-field telemetry grid.
- Action buttons (horn, light, lock) are right on the home screen. No navigation for mid-ride actions.
- Color thresholds for danger states (current > 60A red, temp > 80C red, power > 3000W red) give immediate visual warnings without a separate alarm system.

**Weaknesses:**
- Two-page ViewPager with no affordance that page 2 exists -- discoverable only by accident.
- Settings mostly hidden (`visibility="gone"` by default). If you don't know a feature exists, you'll never find it.
- No ride recording, no trip history, no charts. Purely real-time.

**Summary:** Nails the "riding companion" use case -- real-time telemetry, quick actions, battery health. Stops there.

### InMotion

**Strengths:**
- Full product ecosystem, not just a companion app. Social feed (Moments), marketplace, messaging, multi-vehicle management, GPS ride tracking with maps.
- Model-specific UI tailoring -- each vehicle family gets a customized settings/control interface rather than one-size-fits-all.
- Battery info is comprehensive -- individual cell monitoring, temperature, health percentage.
- GPS ride history with map visualization is a killer feature for route-tracking riders.

**Weaknesses:**
- Feature bloat is severe. Social feed, marketplace with coins, QR code profile sharing -- it's an EUC app that wants to be WeChat. Core telemetry is buried under engagement features.
- Flutter performance overhead on older devices. Dart AOT + GetX state management adds latency for a real-time BLE telemetry app.
- Phones home constantly via numerous API managers (vehicles, accounts, moments, messages, lighting, sounds, marketplace, support, cloud storage, files). Privacy-conscious riders won't like this.
- Complexity makes it fragile. Model-specific branching across 20+ vehicle types with remote API dependencies.

**Summary:** Nails the "product ecosystem" use case but overbuilds it. Serves InMotion's business goals more than the rider's needs.

### DarknessBot

**The one to beat.** Most thoughtfully designed EUC app -- not the most features, but every feature is well-integrated.

**Strengths:**
- Customizable tile dashboard -- information-dense and scannable at a glance. Tile-based layout is the primary view, not secondary.
- Video recorder with live telemetry overlay -- premium flagship feature. Riders love showing speed/lean angle overlaid on footage.
- Auto-torch: activates headlight automatically at sunset or above a speed threshold. A safety feature, not bloat.
- Flic button integration -- physical handlebar button mapped to horn, light, or recording toggle.
- ActiveLook smart glasses support for HUD telemetry display.
- GPS track recording with ElectroClub sharing.
- Range prediction based on current consumption patterns.
- Premium tier is honest: pay for cosmetic features (themes, splash) and power features (recorder, sharing, prediction), not for basic telemetry.
- Clean, consistent design language throughout.

### Kingsong

**Most ambitious and worst-designed.**

**Strengths:**
- 65sp bold speed text is enormous and effective -- readable from 3 feet on handlebars.
- Draggable 4-column quick-action grid lets users arrange control buttons.
- Full-screen camera view with recording overlay including chronometer, flash toggle, camera switch.

**Weaknesses:**
- 200+ activities, social feeds, clubs, coin gamification system, marketplace -- trying to be a social platform rather than a riding companion.
- Dashboard, BMS, and settings are decent but buried under engagement-farming features.
- Dark theme with yellow accents looks good in screenshots but UI is inconsistent -- some screens dark, some light, some Material Design, some not.

---

## FreeWheel Self-Assessment

**Strengths:**
- Gauge system is the best of all five. Hero arc gauge with color zones (green/orange/red), sparklines on tile gauges, and customizable dashboard layout. Neither manufacturer app offers hero metric selection or tile rearrangement.
- Cross-platform parity between Android and iOS is real and rare.
- Capability-driven settings architecture (`WheelSettingsConfig` + `ControlSpec`) is architecturally superior to InMotion's per-model branching and Leaperkim's `visibility="gone"` approach.
- Ride recording with CSV export is more useful than Leaperkim (nothing) and more private than InMotion (cloud required).
- Alarm system with PWM-based thresholds is more sophisticated than any manufacturer app.
- No account / no cloud / no social / no telemetry phoning home -- this is a feature, not a gap.

**Weaknesses (pre-Phase 1):**
- Record button shared a row with Chart/BMS as equal-weight buttons, but recording is an *action* while Chart/BMS are *navigation* -- different verbs.
- BMS display was a flat list -- functional but uninspiring compared to Leaperkim's cell grid.
- Horn and light toggle buried in a controls row below the fold.
- Dashboard was vertically long -- ride-critical items spread across multiple scroll positions.
- No GPS ride visualization despite logging GPS coordinates in CSV.

---

## Actionable Takeaways

### What to adopt (by source app)

| # | Feature | Inspired by | Status |
|---|---------|-------------|--------|
| 1 | Quick actions (horn, light, record) in app bar | DarknessBot, Leaperkim | Done (Phase 1) |
| 2 | Color thresholds on stat row values | Leaperkim | Done (Phase 1) |
| 3 | BMS cell voltage grid with min/max highlighting | Leaperkim | Done (Phase 1) |
| 4 | Tiles-only default (hero gauge optional) | DarknessBot | Planned |
| 5 | Auto-torch (headlight at sunset / above speed) | DarknessBot | Planned |
| 6 | GPS ride history with map visualization | InMotion, DarknessBot, Kingsong | Planned |
| 7 | Video recorder with telemetry overlay | DarknessBot | Future |
| 8 | Flic / hardware button integration | DarknessBot | Future |
| 9 | Draggable quick-action grid | Kingsong | Future |
| 10 | Enormous speed number option | Kingsong | Future |
| 11 | ActiveLook smart glasses support | DarknessBot | Future |
| 12 | Range prediction | DarknessBot | Future |

### What to explicitly reject

- Social feeds, community features, clubs, friend lists (Kingsong, InMotion)
- Marketplace / coin gamification (Kingsong, InMotion)
- Account requirements (InMotion)
- Cloud dependency (InMotion)
- Premium paywalls for basic telemetry

### Design principles confirmed

1. **"Above the fold" rule:** Everything a rider needs mid-ride (speed, battery, horn, light, record) should be visible without scrolling.
2. **Information density via tiles:** Tile grids are denser and more scannable than hero gauges. The hero gauge should be optional, not mandatory.
3. **Focused, rider-first design:** No bloat. Every feature should serve the person currently riding.
4. **Privacy by default:** No accounts, no cloud, no telemetry phoning home. This differentiates FreeWheel from manufacturer apps.
