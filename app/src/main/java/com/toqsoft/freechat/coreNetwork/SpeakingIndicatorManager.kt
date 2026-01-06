package com.toqsoft.freechat.coreNetwork

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.IRtcEngineEventHandler
import kotlinx.coroutines.*
import kotlin.compareTo

object SpeakingIndicatorManager {

    // Track speaking users
    private val uidToUsernameMap = mutableMapOf<Int, String>()
    private val speakingTimestamps = mutableMapOf<String, Long>()
    private val userVolumeLevels = mutableMapOf<String, Int>()
    private var currentCallId: String = ""
    private var currentUsername: String = ""
    private var isListeningToFirestore = false

    // Callbacks for UI
    var onSpeakingStateChanged: ((Map<String, Boolean>) -> Unit)? = null
    var onVolumeLevelsChanged: ((Map<String, Int>) -> Unit)? = null

    // Initialize with call context
    fun initialize(callId: String, username: String) {
        currentCallId = callId
        currentUsername = username
        startFirestoreListener()
        Log.d("SPEAKING_MANAGER", "Initialized for user: $username, callId: $callId")
    }

    private fun startFirestoreListener() {
        if (isListeningToFirestore) return

        val db = FirebaseFirestore.getInstance()
        isListeningToFirestore = true

        Log.d("SPEAKING_MANAGER", "Starting Firestore listener for call: $currentCallId")

        // Listen for speaking updates from other users
        db.collection("call_speaking_status")
            .document(currentCallId)
            .collection("speakers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SPEAKING_FIRESTORE", "Error: $error")
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: emptyList()
                Log.d("SPEAKING_FIRESTORE", "Received snapshot with ${documents.size} documents")

                documents.forEach { doc ->
                    val username = doc.id
                    val isSpeaking = doc.getBoolean("isSpeaking") ?: false
                    val volume = doc.getLong("volume")?.toInt() ?: 0
                    val timestamp = doc.getLong("timestamp") ?: 0

                    Log.d("SPEAKING_FIRESTORE", "User $username: isSpeaking=$isSpeaking, volume=$volume")

                    // Don't process our own updates
                    if (username != currentUsername) {
                        val now = System.currentTimeMillis()

                        if (isSpeaking) {
                            speakingTimestamps[username] = now
                            userVolumeLevels[username] = volume
                            Log.d("SPEAKING_MANAGER", "Added $username as speaking")
                        } else if (now - timestamp > 1000) {
                            speakingTimestamps.remove(username)
                            Log.d("SPEAKING_MANAGER", "Removed $username from speaking")
                        }

                        // Update UI
                        updateUI()
                    }
                }
            }
    }

    // Register a user with their UID and username
    fun registerUser(uid: Int, username: String) {
        uidToUsernameMap[uid] = username
        Log.d("SPEAKING_DEBUG", "Registered UID=$uid as user=$username")
    }

    // Broadcast our speaking status to Firestore
    private fun broadcastSpeakingStatus(username: String, isSpeaking: Boolean, volume: Int) {
        val db = FirebaseFirestore.getInstance()

        val speakingData = hashMapOf(
            "isSpeaking" to isSpeaking,
            "volume" to volume,
            "timestamp" to System.currentTimeMillis(),
            "uid" to (username.hashCode() and 0x7FFFFFFF)
        )

        db.collection("call_speaking_status")
            .document(currentCallId)
            .collection("speakers")
            .document(username)
            .set(speakingData)
            .addOnSuccessListener {
                Log.d("SPEAKING_BROADCAST", "Broadcast $username speaking=$isSpeaking")
            }
            .addOnFailureListener { e ->
                Log.e("SPEAKING_BROADCAST", "Failed to broadcast: $e")
            }
    }

    fun simulateSpeaking(user: String, isSpeaking: Boolean) {
        if (isSpeaking) {
            onVolumeLevelsChanged?.invoke(mapOf(user to (50..80).random()))
            onSpeakingStateChanged?.invoke(mapOf(user to true))
        } else {
            onVolumeLevelsChanged?.invoke(mapOf(user to 0))
            onSpeakingStateChanged?.invoke(mapOf(user to false))
        }
    }

    // Unregister a user
    fun unregisterUser(username: String) {
        val uidToRemove = uidToUsernameMap.entries.find { it.value == username }?.key
        uidToRemove?.let { uidToUsernameMap.remove(it) }
        speakingTimestamps.remove(username)
        userVolumeLevels.remove(username)
        Log.d("SPEAKING_DEBUG", "Unregistered user=$username")
    }

    // Clear all registrations
    fun clearAll() {
        uidToUsernameMap.clear()
        speakingTimestamps.clear()
        userVolumeLevels.clear()
        isListeningToFirestore = false

        if (currentCallId.isNotEmpty() && currentUsername.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("call_speaking_status")
                .document(currentCallId)
                .collection("speakers")
                .document(currentUsername)
                .delete()
        }

        Log.d("SPEAKING_DEBUG", "Cleared all registrations")
    }

    // Get username from UID
    fun getUsernameFromUid(uid: Int): String? {
        return uidToUsernameMap[uid]
    }

    // Get all currently speaking users
    fun getSpeakingUsers(): Set<String> {
        val now = System.currentTimeMillis()
        return speakingTimestamps.filter { now - it.value < 500 }.keys
    }

    // Get volume level for a user
    fun getVolumeLevel(username: String): Int {
        return userVolumeLevels[username] ?: 0
    }

    // Update UI callbacks
    private fun updateUI() {
        val now = System.currentTimeMillis()

        val speakingState = speakingTimestamps
            .filterKeys { it != currentUsername }
            .mapValues { now - it.value < 500 }


        // Also create volume levels EXCLUDING local user
        val filteredVolumeLevels = userVolumeLevels
            .filter { it.key != currentUsername }
            .toMap()

        Log.d("SPEAKING_UI_UPDATE",
            "Updating UI - Speaking: ${speakingState.filter { it.value }.keys}, " +
                    "Volumes: $filteredVolumeLevels")

        // Invoke callbacks
        onSpeakingStateChanged?.invoke(speakingState)
        onVolumeLevelsChanged?.invoke(filteredVolumeLevels)
    }

    // Process audio volume indicators
    fun processAudioVolume(
        speakers: Array<IRtcEngineEventHandler.AudioVolumeInfo>?,
        totalVolume: Int,
        localUsername: String,
        isMuted: Boolean
    ) {
        val now = System.currentTimeMillis()

        // Clear old timestamps
        val toRemove = speakingTimestamps.filter { now - it.value > 500 }.keys
        toRemove.forEach { speakingTimestamps.remove(it) }

        // Process remote speakers from Agora
        var isLocalSpeaking = false
        var localVolume = 0

        speakers?.forEach { speakerInfo ->
            if (speakerInfo.volume > 5) {
                val username = getUsernameFromUid(speakerInfo.uid)
                username?.let {
                    if (it != currentUsername) {
                        speakingTimestamps[it] = now
                        userVolumeLevels[it] = speakerInfo.volume
                    } else {
                        isLocalSpeaking = true
                        localVolume = speakerInfo.volume
                    }
                }
            }
        }

        // Process local user if muted
        if (!isMuted && totalVolume > 5 && !isLocalSpeaking) {
            isLocalSpeaking = true
            localVolume = totalVolume
        }

        // Ensure local user is NOT in speaking indicators
        speakingTimestamps.remove(currentUsername)
        userVolumeLevels.remove(currentUsername)

        // Broadcast our speaking status
        if (isLocalSpeaking) {
            broadcastSpeakingStatus(currentUsername, true, localVolume)
        } else {
            broadcastSpeakingStatus(currentUsername, false, 0)
        }

        // Update UI
        updateUI()
    }
}