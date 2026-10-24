package com.manhunt.app.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.manhunt.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class P2PNetworkManager(private val context: Context) {

    private val TAG = "ManhuntP2P"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Ports & Multicast Group
    private val UDP_PORT = 8888
    private val TCP_PORT = 9876
    private val MULTICAST_IP = "239.255.42.99"

    private var multicastLock: WifiManager.MulticastLock? = null
    private var udpSocket: MulticastSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    // Connected clients on Host
    private val connectedClients = ConcurrentHashMap<String, PrintWriter>()

    // Exposed Flows
    private val _discoveredRooms = MutableStateFlow<Map<String, DiscoveredRoom>>(emptyMap())
    val discoveredRooms: StateFlow<Map<String, DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<P2PPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<P2PPacket> = _incomingPackets.asSharedFlow()

    private var isHosting = false
    private var isClientConnected = false
    private var discoveryJob: Job? = null
    private var listenJob: Job? = null

    init {
        acquireMulticastLock()
    }

    private fun acquireMulticastLock() {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("manhunt_multicast")?.apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    /**
     * Start broadcasting room discovery packets via UDP Multicast and run TCP server.
     */
    fun startHosting(session: GameSession) {
        isHosting = true
        startTcpServer()
        startUdpBroadcasting(session)
    }

    private fun startUdpBroadcasting(session: GameSession) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            try {
                val group = InetAddress.getByName(MULTICAST_IP)
                val socket = DatagramSocket()
                while (isActive && isHosting) {
                    val packet = P2PPacket.DiscoveryBroadcast(
                        joinCode = session.joinCode,
                        roomName = session.roomName,
                        hostPort = TCP_PORT,
                        playerCount = 1,
                        zoneRadiusMeters = session.zoneRadiusMeters
                    )
                    val json = gson.toJson(packet)
                    val data = json.toByteArray()
                    val datagram = DatagramPacket(data, data.size, group, UDP_PORT)
                    socket.send(datagram)
                    delay(2000)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "UDP Broadcast error: ${e.message}")
            }
        }
    }

    /**
     * Start listening for UDP discovery broadcasts from nearby organizers.
     */
    fun startDiscoveryListener() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                val group = InetAddress.getByName(MULTICAST_IP)
                val socket = MulticastSocket(UDP_PORT)
                socket.joinGroup(group)
                val buffer = ByteArray(2048)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val text = String(packet.data, 0, packet.length)

                    try {
                        val broadcast = gson.fromJson(text, P2PPacket.DiscoveryBroadcast::class.java)
                        if (broadcast.joinCode.isNotEmpty()) {
                            val room = DiscoveredRoom(
                                joinCode = broadcast.joinCode,
                                roomName = broadcast.roomName,
                                hostIp = senderIp,
                                hostPort = broadcast.hostPort,
                                playerCount = broadcast.playerCount,
                                zoneRadiusMeters = broadcast.zoneRadiusMeters
                            )
                            val current = _discoveredRooms.value.toMutableMap()
                            current[broadcast.joinCode] = room
                            _discoveredRooms.value = current
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing discovery packet: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener error: ${e.message}")
            }
        }
    }

    /**
     * Runs TCP Relay Server on the Organizer's phone.
     */
    private fun startTcpServer() {
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(TCP_PORT)
                Log.d(TAG, "TCP Server listening on port $TCP_PORT")
                while (isActive && isHosting) {
                    val client = tcpServerSocket?.accept() ?: break
                    handleClientConnection(client)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP Server error: ${e.message}")
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        val clientAddress = "${socket.inetAddress.hostAddress}:${socket.port}"
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                connectedClients[clientAddress] = writer

                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    parseAndDispatchPacket(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handler exception: ${e.message}")
            } finally {
                connectedClients.remove(clientAddress)
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Connect as a client to an Organizer's phone IP.
     */
    fun connectToHost(hostIp: String, port: Int = TCP_PORT, onConnected: () -> Unit = {}) {
        scope.launch {
            try {
                clientSocket = Socket(hostIp, port)
                clientWriter = PrintWriter(clientSocket!!.getOutputStream(), true)
                isClientConnected = true
                withContext(Dispatchers.Main) { onConnected() }

                val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                while (isActive && isClientConnected && !clientSocket!!.isClosed) {
                    val line = reader.readLine() ?: break
                    parseAndDispatchPacket(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host: ${e.message}")
            }
        }
    }

    /**
     * Host broadcasts packet to all connected clients.
     */
    fun broadcastToClients(packet: P2PPacket) {
        val json = gson.toJson(packet)
        scope.launch {
            connectedClients.values.forEach { writer ->
                try {
                    writer.println(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending to client: ${e.message}")
                }
            }
        }
    }

    /**
     * Client sends packet to host.
     */
    fun sendToHost(packet: P2PPacket) {
        val json = gson.toJson(packet)
        scope.launch {
            try {
                clientWriter?.println(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to host: ${e.message}")
            }
        }
    }

    private suspend fun parseAndDispatchPacket(json: String) {
        try {
            when {
                json.contains("\"type\":\"JOIN_REQUEST\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.JoinRequest::class.java)
                    _incomingPackets.emit(pkt)
                }
                json.contains("\"type\":\"SYNC_STATE\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.SyncState::class.java)
                    _incomingPackets.emit(pkt)
                }
                json.contains("\"type\":\"LOCATION_UPDATE\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.LocationUpdate::class.java)
                    _incomingPackets.emit(pkt)
                }
                json.contains("\"type\":\"CATCH_ATTEMPT\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.CatchAttempt::class.java)
                    _incomingPackets.emit(pkt)
                }
                json.contains("\"type\":\"CATCH_RESULT\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.CatchResult::class.java)
                    _incomingPackets.emit(pkt)
                }
                json.contains("\"type\":\"PAUSE_TOGGLE\"") -> {
                    val pkt = gson.fromJson(json, P2PPacket.PauseToggle::class.java)
                    _incomingPackets.emit(pkt)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet parsing failed: ${e.message}")
        }
    }

    fun stop() {
        discoveryJob?.cancel()
        listenJob?.cancel()
        try { udpSocket?.close() } catch (ignored: Exception) {}
        try { tcpServerSocket?.close() } catch (ignored: Exception) {}
        try { clientSocket?.close() } catch (ignored: Exception) {}
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
    }
}
