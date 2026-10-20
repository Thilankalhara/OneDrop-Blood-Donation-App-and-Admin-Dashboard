# 🩸 OneDrop — Blood Donation Mobile App & Admin Dashboard

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.oracle.com/java/)
[![GlassFish](https://img.shields.io/badge/Server-GlassFish%205-blue.svg)](https://glassfish.org/)
[![MySQL](https://img.shields.io/badge/Database-MySQL%208-blue.svg)](https://www.mysql.com/)

> **OneDrop** is a full-stack blood donation ecosystem comprising an **Android Mobile Application** for blood donors and a **Java Web Admin Dashboard** for healthcare administrators. It connects blood donors with hospitals, tracks donations with a points-based leaderboard, provides real-time emergency blood alerts, and supports monetary donations via Stripe.

---

## 📸 App Screenshots & Admin Preview

### 📱 Android Mobile Application

<table>
  <tr>
    <td align="center" width="25%"><img src="Screen shots/sign up.jpg" width="180"/><br/><b>Sign Up</b></td>
    <td align="center" width="25%"><img src="Screen shots/home screen.jpg" width="180"/><br/><b>Home Screen</b></td>
    <td align="center" width="25%"><img src="Screen shots/forget pass.jpg" width="180"/><br/><b>Forget Password</b></td>
    <td align="center" width="25%"><img src="Screen shots/verify email.jpg" width="180"/><br/><b>Verify Email</b></td>
  </tr>
  <tr>
    <td align="center" width="25%"><img src="Screen shots/LBoard.PNG" width="180"/><br/><b>Donor Leaderboard</b></td>
    <td align="center" width="25%"><img src="Screen shots/QR.PNG" width="180"/><br/><b>QR Scanner</b></td>
    <td align="center" width="25%"><img src="Screen shots/Donations screen.jpg" width="180"/><br/><b>Donations Screen</b></td>
    <td align="center" width="25%"><img src="Screen shots/donation centers map.jpg" width="180"/><br/><b>Donation Centers Map</b></td>
  </tr>
</table>

### 💻 Admin Dashboard

<table>
  <tr>
    <td align="center" width="25%"><img src="Screen shots/ADMD.PNG" width="300"/><br/><b>Main Dashboard</b></td>
    <td align="center" width="25%"><img src="Screen shots/ADUM.PNG" width="300"/><br/><b>User Management</b></td>
    <td align="center" width="25%"><img src="Screen shots/ADQRM.PNG" width="300"/><br/><b>QR Management</b></td>
    <td align="center" width="25%"><img src="Screen shots/ADS.PNG" width="300"/><br/><b>Admin Stats</b></td>
  </tr>
  <tr>
    <td align="center" width="25%"><img src="Screen shots/AdQR.PNG" width="300"/><br/><b>Hospital & QR</b></td>
    <td align="center" width="25%"><img src="Screen shots/Capture.PNG" width="300"/><br/><b>System Overview</b></td>
    <td align="center" width="25%"><img src="Screen shots/ADC.PNG" width="300"/><br/><b>Donation Centers</b></td>
    <td align="center" width="25%"><img src="Screen shots/ADL.PNG" width="300"/><br/><b>Admin Logs</b></td>
  </tr>
  <tr>
    <td align="center" width="25%"><img src="Screen shots/OneDrop Admin Panel - Monetary Donations.png" width="300"/><br/><b>Monetary Donations</b></td>
    <td align="center" width="25%"><img src="Screen shots/Top Donaters-admin.png" width="300"/><br/><b>Top Donors</b></td>
    <td align="center" width="25%"></td>
    <td align="center" width="25%"></td>
  </tr>
</table>

---

## ✨ Features Breakdown

### 📱 Android Donor App
- **🔐 Secure Authentication**: Sign up & sign in with BCrypt password hashing and OTP email verification.
- **📷 QR Code Scanner**: Live camera scanning using CameraX and ML Kit to verify blood donations at hospitals.
- **🏆 Gamified Leaderboard**: Earn 100 points per verified blood donation with global donor rankings.
- **🚨 Emergency Blood Alerts**: Receive push notifications and alert feeds for urgent blood group requests.
- **🗺 Interactive Hospital Map**: Locate nearby blood donation centers and hospitals.
- **💰 Monetary Donations**: Support blood bank drives via secure card payments powered by Stripe.
- **📜 Donation History & Profile**: Track past donations, manage profile details, and customize settings.

### 💻 Healthcare Admin Dashboard
- **📊 Overview Analytics**: Real-time stats on total blood donors, verified donations, and active requests.
- **👥 User & Donor Management**: Review donor profiles, update user statuses, and verify accounts.
- **🏷 QR Code Generator & Verification**: Issue and manage unique hospital QR codes for donation validation.
- **💳 Financial Transaction Logs**: View monetary donation records and Stripe payment histories.
- **📢 Alert Dispatcher**: Publish emergency blood shortage alerts to target donor groups.

---

## 🏗 System Architecture

```
+--------------------------+               +----------------------------+
|   Android Mobile App     |               |    Admin Web Dashboard     |
| (Java, CameraX, ML Kit)  |               |  (JSP, Servlets, HTML/CSS) |
+------------+-------------+               +-------------+--------------+
             |                                           |
             | REST HTTP / JSON                         | JDBC SQL
             v                                           v
+-----------------------------------------------------------------------+
|                       GlassFish 5 Application Server                  |
|                    Java EE Backend (Servlets & REST APIs)             |
+-----------------------------------+-----------------------------------+
                                    |
                                    v JDBC
                       +-------------------------+
                       |     MySQL 8 Database    |
                       +-------------------------+
```

---

## 🗄 Database Architecture & Workbench Model

The repository includes the official MySQL Workbench relational schema model and ER diagram:
- 📐 **MySQL Workbench Model:** [`OneDrop 2.0.mwb`](OneDrop%202.0.mwb)
- 🖼 **ER Diagram:** [`oneDrop ER.png`](oneDrop%20ER.png)

### Key Database Tables:
- **`users`**: Registered blood donor accounts, profile details, BCrypt password hashes, FCM push tokens.
- **`donations`**: Verified blood donation logs, donation timestamps, hospital IDs, and points earned.
- **`qr_codes`**: Unique hospital QR verification codes (`UNUSED`, `USED`, `DEACTIVATED`).
- **`monetary_donations`**: Stripe financial transaction records and donor contribution logs.
- **`blood_alerts`**: Urgent blood group shortage alerts broadcasted to app users.
- **`hospitals`**: Registered hospital locations, geocoordinates, and contact details.
- **`admins`**: Dashboard administrator login credentials and management access levels.

---

## 🛠 Tech Stack

### Client (Android App)
- **Language:** Java
- **Min SDK:** 28 | **Target SDK:** 36
- **Camera & Scanning:** AndroidX CameraX `1.3.1`, ML Kit Barcode Scanning `17.3.0`
- **Payments:** Stripe Android SDK
- **Maps & Location:** OpenStreetMap / Google Maps API
- **Networking & JSON:** `HttpURLConnection`, Gson `2.10.1`, `org.json`
- **Notifications:** Firebase Cloud Messaging (FCM)

### Backend (Server & Admin Dashboard)
- **Framework:** Java EE (Servlets & JSPs)
- **Application Server:** GlassFish 5 / Tomcat
- **Build System:** Apache Ant / NetBeans
- **Database:** MySQL 8
- **Security:** jBCrypt `0.4`, JWT (`jjwt 0.11.5`)
- **Payment Processing:** Stripe Java SDK `23.0.0`
- **Mail Service:** JavaMail API `1.6.2` with SMTP TLS

---

## ⚙️ Setup & Configuration Guide

### Prerequisites
- **Java JDK:** 8 or higher
- **Application Server:** GlassFish 5 (or compatible Java EE container)
- **Database:** MySQL 8.0+
- **IDE:** NetBeans IDE (Backend) & Android Studio (Frontend)

---

### 1. Database Setup

1. Open your MySQL client and create the database:
```sql
CREATE DATABASE one_drop;
USE one_drop;
```
2. Import the schema tables from [`OneDrop 2.0.mwb`](OneDrop%202.0.mwb) or execute table DDL statements.

---

### 2. Backend & Database Credentials Setup

Configure your database connection and credentials in `back/oneDrop/src/java/database/connectionProvider.java` using system environment variables or direct values:

| Configuration | Environment Variable | Default Placeholder | Code File Path |
|---|---|---|---|
| **Database URL** | `DB_URL` | `jdbc:mysql://localhost:3306/one_drop` | `back/oneDrop/src/java/database/connectionProvider.java` |
| **Database User** | `DB_USER` | `YOUR_DB_USER` | `back/oneDrop/src/java/database/connectionProvider.java` |
| **Database Password** | `DB_PASS` | `YOUR_DB_PASSWORD` | `back/oneDrop/src/java/database/connectionProvider.java` |

---

### 3. Email & SMTP App Password Setup

The backend uses **JavaMail API** over SMTP TLS to send OTP verification codes and password reset emails.

#### Steps to Generate a Gmail App Password:
1. Go to your **Google Account** -> **Security**.
2. Ensure **2-Step Verification** is turned **ON**.
3. Search for **App Passwords**.
4. Create a new App Password named `OneDrop Backend` and copy the 16-character generated password (e.g. `abcd efgh ijkl mnop`).

#### Configure Email Credentials:

| Setting | Environment Variable | Default Placeholder | File Path |
|---|---|---|---|
| **Sender Email** | `APP_EMAIL` | `your_email@gmail.com` | `back/oneDrop/src/java/model/Mail.java` |
| **Gmail App Password** | `APP_PASSWORD` | `your_app_password` | `back/oneDrop/src/java/model/Mail.java` |

---

### 4. Stripe Payment Gateway Setup

| Key Type | Scope | Environment Variable / Code Location |
|---|---|---|
| **Stripe Secret Key** | Server-Side Only | `STRIPE_SECRET_KEY` in `back/oneDrop/src/java/config/StripeKeyConfig.java` |
| **Stripe Publishable Key** | Backend Config | `STRIPE_PUBLISHABLE_KEY` in `back/oneDrop/src/java/config/StripeKeyConfig.java` |
| **Stripe Publishable Key** | Android App Client | `STRIPE_PUBLISHABLE_KEY` in `front/OneDrop/app/src/main/java/com/example/onedrop/Activity/Fragment/MoneyDonationFragment.java` |

---

### 5. JWT Secret Key Setup

In `back/oneDrop/src/java/util/JwtUtil.java`, configure a minimum 32-character secret key:
```java
// Set environment variable JWT_SECRET or update placeholder:
System.getenv("JWT_SECRET"); // fallback: "YOUR_JWT_SECRET_KEY_MIN_32_CHARS_LONG"
```

---

### 6. Android Mobile App & Firebase Setup

1. Open the `/front/OneDrop` project directory in Android Studio.
2. **Firebase Setup**:
   - Copy `app/google-services.json.example` to `app/google-services.json`.
   - Download your actual `google-services.json` from the Firebase Console and place it inside `front/OneDrop/app/`.
   - *(Note: `google-services.json` is automatically ignored by `.gitignore` to keep your credentials safe.)*
3. **Backend Base URL Setup**:
   - Update your server endpoint in `front/OneDrop/app/src/main/java/com/example/onedrop/Activity/Utils/Constants.java`:
```java
public static final String BASE_URL = "http://YOUR_SERVER_IP:8080/oneDrop/";
```
4. Build and run on an Android device or emulator.

---

## 🔒 Security Notice

All API keys, secrets, database passwords, SMTP credentials, Firebase credentials, and signing keys have been sanitized and excluded from this repository (`.gitignore`). Developers setting up the project should configure their own keys following the setup guide above.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

---

## 👨‍💻 Author

**Thilan Kalhara**
- GitHub: [@Thilankalhara](https://github.com/Thilankalhara)
- Repository: [OneDrop-Blood-Donation-App-and-Admin-Dashboard](https://github.com/Thilankalhara/OneDrop-Blood-Donation-App-and-Admin-Dashboard)

> *"Every drop counts. Donate blood, save lives."*
