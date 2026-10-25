package com.manhunt.app.network

import android.util.Log
import com.manhunt.app.data.Constants
import com.manhunt.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface FirebaseRestApi {

    @PUT("rooms/{joinCode}.json")
    suspend fun createRoom(
        @Path("joinCode") joinCode: String,
        @Body roomData: RoomNetworkModel
    ): Response<Void>

    @GET("rooms/{joinCode}.json")
    suspend fun getRoom(
        @Path("joinCode") joinCode: String
    ): Response<RoomNetworkModel?>

    @PUT("rooms/{joinCode}/players/{playerId}.json")
    suspend fun putPlayer(
        @Path("joinCode") joinCode: String,
        @Path("playerId") playerId: String,
        @Body player: Player
    ): Response<Void>

    @PATCH("rooms/{joinCode}/players/{playerId}.json")
    suspend fun patchPlayerLocation(
        @Path("joinCode") joinCode: String,
        @Path("playerId") playerId: String,
        @Body locationUpdates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    @PATCH("rooms/{joinCode}/session.json")
    suspend fun patchSession(
        @Path("joinCode") joinCode: String,
        @Body sessionUpdates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    @PUT("rooms/{joinCode}/lastCatchAttempt.json")
    suspend fun putCatchAttempt(
        @Path("joinCode") joinCode: String,
        @Body catchAttempt: CatchAttemptPayload
    ): Response<Void>

    @DELETE("rooms/{joinCode}/players/{playerId}.json")
    suspend fun deletePlayer(
        @Path("joinCode") joinCode: String,
        @Path("playerId") playerId: String
    ): Response<Void>
}

class FirebaseSyncService private constructor() {

    private val TAG = "FirebaseSyncService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val api: FirebaseRestApi

    private val _syncStatus = MutableStateFlow(NetworkSyncStatus.ONLINE)
    val syncStatus: StateFlow<NetworkSyncStatus> = _syncStatus.asStateFlow()

    private val _roomState = MutableStateFlow<RoomNetworkModel?>(null)
    val roomState: StateFlow<RoomNetworkModel?> = _roomState.asStateFlow()

    private var pollingJob: Job? = null
    private var activeJoinCode: String? = null

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor())
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val baseUrl = if (Constants.FIREBASE_DATABASE_URL.endsWith("/")) {
            Constants.FIREBASE_DATABASE_URL
        } else {
            "${Constants.FIREBASE_DATABASE_URL}/"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(FirebaseRestApi::class.java)
    }

    /**
     * Start continuous cloud sync polling for the active room.
     * Polls Firebase REST every 2.5 seconds to receive latest GPS positions and game events.
     */
    fun startRoomSync(joinCode: String) {
        activeJoinCode = joinCode
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                fetchRoomData(joinCode)
                delay(Constants.CLOUD_SYNC_POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun fetchRoomData(joinCode: String): RoomNetworkModel? {
        return try {
            _syncStatus.value = NetworkSyncStatus.SYNCING
            val response = api.getRoom(joinCode)
            if (response.isSuccessful) {
                _syncStatus.value = NetworkSyncStatus.ONLINE
                val data = response.body()
                _roomState.value = data
                data
            } else {
                Log.w(TAG, "Get room response code: ${response.code()}")
                _syncStatus.value = NetworkSyncStatus.OFFLINE
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching room data: ${e.message}")
            _syncStatus.value = NetworkSyncStatus.OFFLINE
            null
        }
    }

    /**
     * Organizer creates room via PUT /rooms/{join_code}.json
     */
    suspend fun createRoom(room: RoomNetworkModel): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = NetworkSyncStatus.SYNCING
                val response = api.createRoom(room.session.joinCode, room)
                val success = response.isSuccessful
                _syncStatus.value = if (success) NetworkSyncStatus.ONLINE else NetworkSyncStatus.OFFLINE
                if (success) {
                    startRoomSync(room.session.joinCode)
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room on Firebase: ${e.message}")
                _syncStatus.value = NetworkSyncStatus.OFFLINE
                false
            }
        }
    }

    /**
     * Player joins room via PUT /rooms/{join_code}/players/{player_id}.json
     */
    suspend fun joinPlayer(joinCode: String, player: Player): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = NetworkSyncStatus.SYNCING
                val response = api.putPlayer(joinCode, player.id, player)
                val success = response.isSuccessful
                _syncStatus.value = if (success) NetworkSyncStatus.ONLINE else NetworkSyncStatus.OFFLINE
                if (success) {
                    startRoomSync(joinCode)
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error joining room: ${e.message}")
                _syncStatus.value = NetworkSyncStatus.OFFLINE
                false
            }
        }
    }

    /**
     * Partial player update via PATCH /rooms/{join_code}/players/{player_id}.json
     */
    suspend fun patchPlayer(joinCode: String, playerId: String, updates: Map<String, Any>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                api.patchPlayerLocation(joinCode, playerId, updates).isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Failed to patch player: ${e.message}")
                false
            }
        }
    }

    /**
     * Push location update via PATCH /rooms/{join_code}/players/{player_id}.json
     */
    suspend fun updateLocation(
        joinCode: String,
        playerId: String,
        lat: Double,
        lng: Double,
        isOutOfZone: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val updates = mapOf(
                    "latitude" to lat,
                    "longitude" to lng,
                    "isOutOfZone" to isOutOfZone,
                    "lastUpdatedEpochMs" to System.currentTimeMillis()
                )
                val response = api.patchPlayerLocation(joinCode, playerId, updates)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Failed to patch location: ${e.message}")
                false
            }
        }
    }

    /**
     * Updates session status, countdown timer, or pause state
     */
    suspend fun updateSession(joinCode: String, updates: Map<String, Any>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.patchSession(joinCode, updates)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Failed to patch session: ${e.message}")
                false
            }
        }
    }

    /**
     * Submits a catch attempt to Firebase
     */
    suspend fun sendCatchAttempt(joinCode: String, attempt: CatchAttemptPayload): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.putCatchAttempt(joinCode, attempt)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Failed to put catch attempt: ${e.message}")
                false
            }
        }
    }

    /**
     * Leave room
     */
    suspend fun leaveRoom(joinCode: String, playerId: String) {
        withContext(Dispatchers.IO) {
            try {
                api.deletePlayer(joinCode, playerId)
            } catch (ignored: Exception) {}
        }
        stopSync()
    }

    fun stopSync() {
        pollingJob?.cancel()
        activeJoinCode = null
        _roomState.value = null
    }

    companion object {
        val instance: FirebaseSyncService by lazy { FirebaseSyncService() }

        /**
         * Static helper callable from Background Location Service
         */
        fun pushLocationUpdate(
            joinCode: String,
            playerId: String,
            lat: Double,
            lng: Double,
            isOutOfZone: Boolean
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                instance.updateLocation(joinCode, playerId, lat, lng, isOutOfZone)
            }
        }
    }
}
