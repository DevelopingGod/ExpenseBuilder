# 💰 ExpenseBuilder
> **Track Daily Expenses. Manage Transactions. Export with Real-Time Currency Conversion.**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Size](https://img.shields.io/badge/App_Size-~3MB-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

## 📱 About The App
**ExpenseBuilder** is a powerful yet lightweight Android application designed for travelers, students, and finance enthusiasts. Unlike standard expense trackers, ExpenseBuilder focuses on **multi-currency reporting**.

Whether you are studying abroad or traveling, you can log expenses in one currency and generate reports converted to another (e.g., spend in **USD**, generate report in **INR**) using real-time exchange rates.

## ✨ Key Features

* **🌍 Dynamic Currency Conversion:** Automatically fetches live exchange rates (via Open Exchange Rates API) to convert your expense totals instantly.
* **📊 Smart Reporting:**
    * **PDF Export:** Generate professional, printable reports with auto-calculated totals.
    * **Excel/CSV Export:** Export data to CSV format that opens natively in Microsoft Excel or Google Sheets.
* **📉 Daily & Account Modes:**
    * **Daily Expenses:** Track day-to-day spending with categories, quantity, and units.
    * **Account Transactions:** Manage bank-to-bank transfers (Credit/Debit) with beneficiary details.
* **⚡ Ultra-Lightweight:** Optimized performance with an app size of just **~3 MB**.
* **🔒 Privacy Focused:** 100% Offline storage. Your financial data stays on your device.

## 📸 Screenshots

| Dashboard | Add Expense | PDF Report |
|:---------:|:-----------:|:----------:|
| <img src="https://via.placeholder.com/200x400?text=Home+Screen" width="200"> | <img src="https://via.placeholder.com/200x400?text=Add+Entry" width="200"> | <img src="https://via.placeholder.com/200x400?text=PDF+Export" width="200"> |

*(Note: Upload your actual screenshots to the repo and replace the links above)*

## 🛠️ Tech Stack

* **Language:** Kotlin
* **Architecture:** MVVM (Model-View-ViewModel)
* **Networking:** Retrofit + OkHttp (for Currency API)
* **Reports:**
    * Native PDF Generation (`android.graphics.pdf`)
    * Native CSV Generation (Crash-proof Excel support)
* **UI:** XML / Material Design

## 🚀 Installation

You can download the latest version of the app directly from the releases section.

1.  Download the **[Latest APK](https://github.com/DevelopingGod/ExpenseBuilder/raw/refs/heads/main/app/release/app-release.apk)**.
2.  Open the file on your Android device.
3.  If prompted, allow **"Install from Unknown Sources"**.
4.  Install and enjoy!

## 💡 How to Use

1.  **Select Currency:** Choose your Base Currency (e.g., USD) and Target Currency (e.g., INR) from the dashboard.
2.  **Add Entry:** Click the `+` button to log a Daily Expense or Account Transaction.
3.  **Export:** Click the **PDF** or **Excel** icon at the top.
    * *Magic:* The app automatically converts all prices to your Target Currency in the report!
4.  **Share:** Find the report in your **Downloads** folder to share via WhatsApp or Email.

## 🤝 Contributing

Contributions are welcome!
1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 👤 Author

**[Sankalp Indish]**
* GitHub: [@DevelopingGod](https://github.com/DevelopingGod)
* LinkedIn: [https://www.linkedin.com/in/sankalp-indish/]

---
*If you find this project useful, please give it a ⭐️ on GitHub!*
