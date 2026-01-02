# 🚀 FreeChat

**FreeChat** is a modern, modular **chat + calling application** built with  
**Jetpack Compose**, following **SOLID principles** and **MVVM architecture**.

It supports:
- 💬 Real-time messaging (MQTT)
- 📞 Audio & Video calls (Agora)
- 🕘 Call history
- 🟢 Online / Offline presence
- ☁️ Firestore persistence
- 🔔 Push notifications via FCM

Each user is uniquely identified using a **UUID generated from their username**.

---

## 📱 Features

### 💬 Messaging
- Real-time messaging using **MQTT**
- Typing indicators ✍️
- Message status:
  - Sent ✓
  - Delivered ✓✓
  - Seen ✓✓ (Blue)
- Offline support via Firestore cache

---

📞 Audio & Video Calls (Updated)

One-to-one Audio calls

One-to-one Video calls

Group calls (10+ participants)

Audio calls with multiple users

Dynamic participant grid UI

Add/remove participants during the call

Mic mute/unmute per participant

Speaker toggle

End call for all participants

Call duration timer

Firestore sync for participants and call state

Handles real-time UI updates when users join/leave

Incoming call screen (Accept / Reject)

Call state synced using Firestore

Mic mute / unmute 🎙

Camera on / off 📷

Switch camera 🔄

End call sync for both users

---
### 🎙 Voice-to-Action (Speech Recognition)
- FreeChat uses Android SpeechRecognizer to allow hands-free navigation and actions.

- Commands Supported:

- "Open chat [Name]" – Navigates directly to a specific conversation.

- "Audio call [Name]" – Initiates a 1:1 Agora audio call.

- "Video call [Name]" – Initiates a 1:1 Agora video call.

- Implementation: Integrated via a side-effect in Compose to trigger ViewModel intents based on recognized text.
---

### 🕘 Call History
- Incoming / Outgoing calls
- Missed / Rejected / Completed calls
- Audio / Video type
- Call duration
- Timestamp
- Stored per chat in Firestore

---

### 🟢 Online / Offline Presence
- Real-time online status
- Last seen timestamp
- Updates on:
  - App foreground / background
  - App termination
  - Network disconnect
- Visible in:
  - Chat list
  - Chat header
  - Call screens

---

### 🔔 Notifications (FCM)
- New message alerts
- Incoming call notifications
- Missed call notifications
- Works in background & killed state

---

## 🏗 Architecture

FreeChat follows **Clean Modular Architecture + MVVM**.

---

## 📦 Modules

### 🔹 Core Modules

#### `coreModel`
```kotlin
ChatMessage
CallModel
CallHistory
IncomingCallData
UserPresence
