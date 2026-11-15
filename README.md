# 🎓 CoachMaster — Smart Tuition & Coaching Management App

## 📲 1-Tap Android APK Download (GitHub se Install Karein)

👉 **[📥 CLICK HERE TO DOWNLOAD LATEST APK (`CoachMaster.apk`)](../../releases/latest/download/CoachMaster.apk)**

Agar upar wale link par click karne par *"Not Found"* dikhe, iska matlab GitHub abhi background mein APK build kar raha hai (isme **2 se 3 minute** lagte hain):
1. **[⚙️ Yahan Click Karke Build Progress Dekhein (GitHub Actions)](../../actions)** — Jab green tick (✅) aa jaye, tab upar wale **Download APK** link par click karein.
2. Ya **[📦 Releases Page Open Karein](../../releases)** aur wahan **Assets** ke andar **`CoachMaster.apk`** par tap karke download karein.

---

## 🔐 Default Admin Login Credentials

- **Quick 4-Digit PIN:** `8833`
- **Admin Password ("Pankaj jii Login"):** `P@88337`
- **Biometric Unlock:** Lock screen par Fingerprint icon दबाकर Fingerprint / Face Unlock use karein.

---

## ✨ Key Features Included

1. **Firebase Firestore Collections Architecture (`tution-pro-32a33`):**
   - `students/{studentId}`, `attendance/{date}`, `payments/{paymentId}` — Unlimited records sync with zero 1MB document limit.
2. **Excel / CSV Accounting Export & `<75%` Attendance Alert:**
   - **Reports & Analytics** tab se Fee Ledger, Attendance Register, Expense Report, aur Master Student Summary `.CSV` (Excel) mein export karein.
   - `<75%` attendance wale students ke parents ko **1-Tap WhatsApp Warning** bhejein.
3. **Automated Daily Notifications & Nightly Encrypted Backup (`WorkManager`):**
   - Roz subah **9:00 AM** Roll-Call Reminder notification.
   - Roz raat **11:00 PM** AES-256-GCM Encrypted JSON Auto-Backup.
4. **Hardware Biometric Unlock & Guest Privacy Masking:**
   - Native Android Fingerprint / Face Unlock aur Guest Mode mein mobile numbers masking (`98765*****`).
5. **60 FPS Smooth Scrolling & Swipe Gestures:**
   - Student Card par **Right Swipe 👉** = Direct Fee Collection, **Left Swipe 👈** = Direct WhatsApp Message.
