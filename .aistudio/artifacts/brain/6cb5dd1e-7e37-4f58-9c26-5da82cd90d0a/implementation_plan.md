# Smart 30-Working-Day Attendance-Driven Billing & Holiday Engine

Transition CoachMaster from calendar-based monthly billing to an attendance-driven working-day billing engine where student fee cycles advance only on days when tuition is open (`present` or `absent`), while freezing fees automatically during institute holidays (`holiday`) and approved student medical leaves (`leave`), complete with smart alerts, 1-tap WhatsApp holiday broadcasts, and full working-day proofs on PDF receipts.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core preferences and smart upgrades have been confirmed from your responses:

- **Classes per Fee Cycle Configuration**: Default `30` working classes globally in Institute Settings, with an optional per-batch override (`classesPerCycle`) in Batch Management (e.g., `26` days for regular batches excluding Sundays, or `12` days for Mon-Wed-Fri batches).
- **Sunday & Unmarked Past Days Handling**: Every unmarked past day (including Sundays) triggers a prominent red/amber alert banner on the Dashboard and Attendance screen asking whether to **Take Roll Call (Count Day)**, **Mark as Paid Weekly Off (+1 Billable Day)**, or **Mark as Tuition Holiday (0 Billable Days / Freeze Fee)**.
- **All 5 Smart Upgrades Included**:
  1. **Configurable Cycle Length (Global & Batch-level)** + **Sunday Choice Prompt**.
  2. **Today's Tuition Status Tracker & Unmarked Past Days Alert Banner** on the Dashboard.
  3. **Holiday Declaration Dialog** (1-Day or Date Range + Reason) with **1-Tap WhatsApp Parent Broadcast**.
  4. **Approved Leave (`leave` / Fee Pause)** option in daily roll call so pre-informed medical/long leaves freeze that student's billing counter without penalizing them.
  5. **Working Days, Holidays & Cycle Progress Proof** on Student Cards, Student Dossier (`FinanceHistoryModal`), and PDF Receipts (`PdfReceiptGenerator`).

---

## 1. Overview & Core Concept

- **What It Does**: Replaces rigid calendar-month fee calculation with a fair, transparent **Working-Day Billing Counter**. Every day has a clear status for each student:
  - **Normal Class (`present` 🟢)**: Tuition Open + Student Present → **+1 Billable Day**
  - **Student Bunk / Absent (`absent` 🔴)**: Tuition Open + Student Absent without approved pause → **+1 Billable Day**
  - **Tuition Holiday (`holiday` ⚪)**: Institute Closed → **+0 Billable Days (Fee Frozen for All)**
  - **Approved Leave (`leave` ⏸️)**: Tuition Open + Pre-approved Medical/Long Leave → **+0 Billable Days (Fee Paused for Student)**
- **Target Audience / Persona**: Coaching institute owners, teachers, and parents who want transparent, dispute-free fee billing backed by exact attendance and holiday proof.
- **Key Value**: Eliminates parent disputes over festival/rain holidays, prevents revenue loss from forgotten roll calls via proactive unmarked-day alerts, and automates parent communication via 1-tap WhatsApp notices.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Dashboard "Today's Tuition Status" & Unmarked Day Alerts**:
     - At the top of the Dashboard, a dedicated **Tuition Status Card** shows:
       - Today's status badge: `OPEN 🟢`, `CLOSED (Holiday) 🏖️`, or `PENDING ⚠️`.
       - Monthly summary pills: `X Days Open | Y Days Tuition Closed (Holidays)`.
       - Quick actions: **[🟢 Take Roll Call (Open)]** and **[🏖️ Mark Tuition Holiday]**.
     - If yesterday or any recent date (including Sunday) has no attendance or holiday record, a high-visibility warning banner appears:
       - *"⚠️ Kal ([Date]) ka status pending hai: Roll Call lein ya Holiday mark karein!"* with quick-resolve buttons (**Take Roll Call**, **Mark Holiday**, or **Paid Sunday (+1 Day)** if Sunday).
  2. **Declaring a Tuition Holiday & 1-Tap WhatsApp Broadcast**:
     - Tapping **Mark Tuition Holiday** (from Dashboard or the **"Tuition Closed Today (Holiday)"** switch on the Attendance Screen) opens the **Declare Holiday Dialog**:
       - Select **Single Day** or **Date Range** (`From Date` → `To Date`).
       - Enter **Holiday Reason** (e.g., *Diwali Holiday*, *Heavy Rain*, *Sunday Weekly Off*, *Urgent Work*).
       - Choose **[Save Holiday]** or **[Save & Broadcast on WhatsApp 📢]**, which saves `holiday` status across active students for those dates and launches WhatsApp with the pre-formatted Hindi/English notice:
         > *"📢 Notice: [Institute Name] mein [Date/Range] ko [Reason] ke karan chutti rahegi. Note: Chutti ke dino ki fee count nahi ki jayegi."*
  3. **Attendance Screen Roll Call with 4 Statuses & Holiday Toggle**:
     - Below the Date Switcher, a **"Tuition Closed Today (Holiday) 🏖️"** toggle card shows if the selected date is a holiday (with its saved reason) and hides individual Present/Absent buttons when active so no student is billed.
     - When Tuition is Open, each student row offers **[Present 🟢]**, **[Absent 🔴]**, and **[Leave (Pause) ⏸️]** so teachers can grant an approved medical/long-leave fee pause in one tap.
  4. **Student Card Progress Bar & Dossier / PDF Proof**:
     - Every Student Card displays a visual progress bar and counter below the student's details:
       - `24 / 30 Classes Completed (6 classes left for next fee)` (using the student's batch `classesPerCycle` or global default).
     - Opening the Student Dossier (`FinanceHistoryModal`) or generating a **PDF Fee Receipt / Full History Report** shows the complete breakdown box:
       - **Total Tuition Open Days (Billable)**: `X Days (Present: P, Absent/Bunk: A)`
       - **Tuition Holidays (Not Charged)**: `H Days (Free / Extended)`
       - **Approved Leave (Fee Paused)**: `L Days`
       - **Current Cycle Progress**: `C / 30 Days Completed` (`Completed Cycles: N` → `Fee Due` / `X classes left`).

- **Visual Identity & Theme**:
  - Preserves CoachMaster's crisp **Indigo (`#4F46E5`), Emerald (`#10B981`), Amber (`#F59E0B`), and Coral (`#EF4444`)** Material 3 palette.
  - Status-coded visual chips: Emerald for `Present` & `Open`, Coral for `Absent/Bunk` & `Unmarked Alert`, Amber/Purple for `Approved Leave (Fee Pause)`, and Slate/Sky for `Tuition Holiday (Fee Frozen)`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Attendance-Driven Fee Engine with Fallback Compatibility**
  - *Chosen Approach*: `FeeCalculator.calculateStudentDues` filters all `AttendanceRecord` entries for the student where `date >= student.joiningDate`.
    - `presentCount = count(status == "present")`
    - `absentCount = count(status == "absent")`
    - `holidayCount = count(status == "holiday")`
    - `leaveCount = count(status == "leave")`
    - `totalBillableDays = presentCount + absentCount`
    - `classesPerCycle = batch?.classesPerCycle ?: config.defaultClassesPerCycle` (default `30`)
    - `completedCycles = totalBillableDays / classesPerCycle`
    - `currentCycleProgress = totalBillableDays % classesPerCycle`
    - `classesLeftForNextFee = classesPerCycle - currentCycleProgress`
    - `totalAccrued = completedCycles * student.monthlyFee`
  - *Why*: Directly implements the 30-working-day billing trigger where only `present` and `absent` advance the billing counter, while `holiday` and `leave` add `0` billable days.
- **Decision 2: Cloud & Backup Schema Compatibility**
  - *Chosen Approach*: Extend `AttendanceRecord` with an optional `note` field (for holiday/leave reason), `Batch` with `classesPerCycle: Int = 0` (`0` = use global default), and `InstituteConfig` with `defaultClassesPerCycle: Int = 30`. Sync these fields seamlessly in `CoachMasterRepository` (both Room DB and Firebase Firestore `tuitionAppDb/mainData` + JSON backup).
  - *Why*: Ensures holidays, reasons, and custom cycle lengths persist across app restarts, cloud sync, and JSON backup exports without breaking existing records.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose)                         │
├───────────────────────┬─────────────────────────┬─────────────────────────┤
│    DashboardScreen    │    AttendanceScreen     │     StudentsScreen      │
│ • Tuition Status Card │ • Holiday Toggle Switch │ • Cycle Progress Bar    │
│ • Unmarked Day Alert  │ • Present/Absent/Leave  │ • Dossier Working-Day   │
│ • Sunday Prompt       │ • Declare Holiday Modal │   & Holiday Proof Card  │
│ • WhatsApp Broadcast  │   (1-Day / Date Range)  │ • PDF Receipt Proof     │
└───────────┬───────────┴────────────┬────────────┴────────────┬────────────┘
            │                        │                         │
            ▼                        ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                           CoachMasterViewModel                            │
│ • markAttendance() / markTuitionHolidayRange() / markPaidSunday()         │
│ • getUnmarkedPastDates() / getMonthlyOpenAndHolidayCounts()               │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     │
            ┌────────────────────────┴────────────────────────┐
            ▼                                                 ▼
┌─────────────────────────────────────┐   ┌─────────────────────────────────┐
│          FeeCalculator              │   │      CoachMasterRepository      │
│ • Billable Days = Present + Absent  │   │ • Room Database (v3 Schema)     │
│ • Frozen Days = Holiday + Leave     │   │ • Firestore Cloud Sync          │
│ • Cycle = Billable / CycleLength    │   │ • PdfReceiptGenerator & Share   │
└─────────────────────────────────────┘   └─────────────────────────────────┘
```

### Data Model & State Updates

1. **Entities (`Entities.kt`)**:
   - `AttendanceRecord`: `status` supports `"present"`, `"absent"`, `"holiday"`, `"leave"`; add `val reason: String = ""` for holiday/leave notes.
   - `Batch`: add `val classesPerCycle: Int = 0` (`0` means inherit global `config.defaultClassesPerCycle`, or custom e.g. `26`, `12`).
   - `InstituteConfig`: add `val defaultClassesPerCycle: Int = 30`.
2. **Fee Calculation Model (`StudentDueInfo` in `FeeCalculator.kt`)**:
   - Includes `cyclesElapsed`, `daysElapsed` (total billable days), `classesPerCycle`, `currentCycleProgress`, `classesLeftInCycle`, `presentDays`, `absentDays`, `holidayDays`, `leaveDays`, `totalAccrued`, `totalPaid`, `pendingBalance`, `nextDueDateStr`, and `isOverdue`.
3. **Interactive Component & State Mapping**:
   - **Unmarked Past Days Detector**: Checks dates from the earliest active student joining date (capped to the last 14 days up to yesterday) to find dates where no `AttendanceRecord` exists. Highlights yesterday/recent unmarked dates and Sundays with 1-tap resolution actions.
   - **Holiday Range Saver & Broadcast**: Generates `AttendanceRecord(status = "holiday", reason = reason)` for all active students across the selected date or date range, persists to Room + Firebase, and triggers `ShareHelper.shareWhatsAppNotice(context, message)` when requested.
   - **PDF Receipt Proof (`PdfReceiptGenerator.kt`)**: Adds a dedicated **"Attendance & Billing Cycle Proof"** box on both Official Fee Receipts and Full History Reports displaying Total Open Days (Present + Absent), Tuition Holidays (Free/Not Charged), Approved Leaves, and Current Cycle Progress (`X / Y Classes Completed`).
