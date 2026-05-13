# Watchdog – Android Malware & Security Analysis System

## Overview

Watchdog is a cybersecurity-focused Android application and FastAPI backend system designed to analyze Android APK files, detect suspicious behavior, and provide security risk insights. The project combines Android frontend functionality with backend malware analysis services to help identify potentially harmful applications.

---

# Features

## Android Application

* User-friendly Android interface
* APK upload and scan functionality
* Device security monitoring
* Scan result visualization
* Real-time communication with backend APIs

## FastAPI Backend

* APK analysis engine
* Risk scoring system
* Malware behavior detection
* Secret/API key detection
* Scan result storage
* REST API support

## Security Analysis

* Static APK analysis
* Permission inspection
* Risk categorization
* Suspicious pattern detection
* Security report generation

---

# Tech Stack

## Frontend

* Kotlin
* Android Studio
* Gradle

## Backend

* Python
* FastAPI
* SQLite
* Celery

## Tools & Technologies

* Git & GitHub
* REST APIs
* APK Analysis Libraries

---

# Project Structure

```text
watchman/
│
├── app/                        # Android application
├── gradle/
├── backend_fastapi/
│   ├── app/
│   ├── requirements.txt
│   └── README.md
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── .gitignore
```

---

# Installation & Setup

## Android Application Setup

1. Clone the repository

```bash
git clone https://github.com/yuvrage/Watchdog.git
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Run the application on:

* Android Emulator
* Physical Android Device

---

# Backend Setup

## Create Virtual Environment

```bash
python -m venv venv
```

## Activate Environment

### Windows

```bash
venv\Scripts\activate
```

### Linux / Mac

```bash
source venv/bin/activate
```

## Install Dependencies

```bash
pip install -r requirements.txt
```

## Run FastAPI Server

```bash
uvicorn app.main:app --reload
```

---

# API Features

* APK Upload API
* Security Scan API
* Risk Analysis API
* Device Monitoring API
* Scan Report API

---

# Future Improvements

* AI-based malware detection
* Real-time threat intelligence
* Cloud deployment
* Dashboard analytics
* Advanced permission analysis
* Automated report generation

---

---

# Author

## Yuvraj Tiwari

# License

This project is developed for educational and research purposes.
