# 🚀 FreeChat


**FreeChat** is a modern, modular chat application built with **Jetpack Compose**, 
following **SOLID principles** and **MVVM architecture**.
It supports **real-time messaging** using **MQTT**,
persistent storage with **Firestore**, 
**push notifications via FCM**. Each user is uniquely identified with a UUID generated from their username.

---

## 📱 Features

- Real-time chat using MQTT
- Typing indicators ✍️
- Message status ticks:
  - **Sent (✓)**
  - **Delivered (✓✓)**
  - **Seen (✓✓ Blue)**
- Presence management (online/offline & last seen ⏱)
- Notifications via FCM 🔔
- User-based UUIDs for unique identification
- Modular, SOLID, and testable architecture
- Jetpack Compose UI for reactive and modern design
- Offline support with Firestore persistence

---

## 🏗 Architecture

The app follows a **modular architecture** and **SOLID principles**:

1. **Core Modules**
   - `coreModel`: Data models (`ChatMessage`, `StatusEvent`, `TypingEvent`, `UserPresence`)
   - `coreNetwork`: MQTT manager for real-time messaging
   - `coreData`: Firestore repositories
   - `coreUtils`: Utilities (UUID generation, time formatting)

2. **Feature Modules**
   - `featureChat`: Chat screens, ViewModels, and UI components
   - `featureAuth` (optional): User login/registration

3. **App Layer**
   - Hilt for dependency injection
   - Navigation using Jetpack Compose Navigation
   - MainActivity hosts Composable screens

---

## 💬 MQTT Messaging

- **Messages**:
- **Typing indicators**:
- **Message status** (SENT / DELIVERED / SEEN):
-  **Presence status**:  

---

## ☁️ Firestore Integration

- Stores chat messages for persistence
- Supports offline caching
- Each user document is identified by a **UUID derived from username**

---

## 🔔 FCM Notifications

- Push notifications for new messages
- Integrated with Firestore triggers
- Works even when the app is in the background

---

## 🎨 UI / Jetpack Compose

- Composable screens for chat list and chat detail
- Message bubbles with status icons
- Typing indicators and last seen timestamps
- Fully reactive using **StateFlow**
- Smooth scrolling and reverse layout for chat messages

---

## 🆔 User Identification

- On login, the app generates a **UUID for each username**
- This UUID is used for MQTT topics, Firestore documents, and push notifications

---

## ⚡ Getting Started

1. Clone the repository:
 ```bash
 git clone https://github.com/yourusername/freechat.git



