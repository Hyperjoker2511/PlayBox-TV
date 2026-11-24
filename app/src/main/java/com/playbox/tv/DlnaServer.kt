package com.playbox.tv

import android.content.Context
import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Batch 2: DLNA / UPnP renderer, hand-rolled with the Android framework
 * only — no UPnP library, so no new dependency. A phone running VLC,
 * BubbleUPnP or any DLNA sender on the same Wi-Fi finds "PlayBox TV",
 * sends it a video URL, and PlayBox plays it with the normal player.
 *
 * What it speaks:
 * - SSDP: M-SEARCH answers + alive/byebye NOTIFY on 239.255.255.250:1900.
 * - HTTP: device description + service descriptions (SCPD) + SOAP control
 *   for AVTransport (SetAVTransportURI / Play / Pause / Stop / Seek /
 *   GetPositionInfo / GetTransportInfo / GetMediaInfo) and
 *   RenderingControl (volume / mute), + GENA event subscriptions.
 * - DIDL-Lite: the title from the sender's metadata, for the player UI.
 *
 * What it does NOT do: full eventing for every state variable (only
 * LastChange, which is what senders actually read), SetNextAVTransportURI
 * / Next / Previous (no cast queue in v1), or screen mirroring (that is
 * Miracast, not DLNA — the TV's own Miracast does that).
 *
 * The player itself is driven through [DlnaHandler], implemented by
 * CastService — this file knows nothing about ExoPlayer.
 */

/** What the renderer needs from the player, and what it reports back. */
interface DlnaHandler {
    fun onSetUri(uri: String, title: String)
    fun onPlay()
    fun onPause()
    fun onStop()
    fun onSeekMs(positionMs: Long)
    fun onSetVolume(percent: Int)
    fun onSetMute(muted: Boolean)
    fun currentState(): DlnaState
}

data class DlnaState(
    val transportState: String, // STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING
    val currentUri: String,
    val trackTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val volumePercent: Int,
    val muted: Boolean
)

object DlnaServer {
    private const val TAG = "PlayBoxDlna"
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val HTTP_PORT_FIRST = 1400
    private const val CACHE_SECONDS = 1800

    private var running = false
    private var httpPort = HTTP_PORT_FIRST
    private var udn: String = ""
    private var handler: DlnaHandler? = null
    private var appContext: Context? = null
    private var ssdpSocket: MulticastSocket? = null
    private var httpSocket: ServerSocket? = null
    private var pool = Executors.newCachedThreadPool()
    private var scheduler: ScheduledExecutorService? = null

    private data class Subscription(val callback: String, val sid: String, var seq: Int = 0, var expiresAt: Long = 0)
    private val avtSubs = ConcurrentHashMap<String, Subscription>()
    private val rcSubs = ConcurrentHashMap<String, Subscription>()
    private var lastNotifiedState: String = ""
    private var lastPositionNotifyMs = 0L

    @Synchronized
    fun start(context: Context, handler: DlnaHandler) {
        if (running) return
        this.handler = handler
        appContext = context.applicationContext
        udn = context.getSharedPreferences("playbox_dlna", Context.MODE_PRIVATE)
            .getString("udn", null) ?: UUID.randomUUID().toString().also {
                context.getSharedPreferences("playbox_dlna", Context.MODE_PRIVATE)
                    .edit().putString("udn", it).apply()
            }
        running = true
        // Bind the HTTP server first (find a free port), so the SSDP
        // LOCATION we advertise is one that actually answers.
        httpPort = bindHttp()
        startSsdp()
        scheduler = Executors.newSingleThreadScheduledExecutor()
        // Re-announce well before the cache expires; also re-check the
        // position notify while playing.
        scheduler?.scheduleAtFixedRate({ runCatching { sendAlive() } }, 900, 900, TimeUnit.SECONDS)
        scheduler?.scheduleAtFixedRate({ runCatching { maybeNotifyPosition() } }, 1, 1, TimeUnit.SECONDS)
        sendAlive()
        Log.i(TAG, "DLNA renderer up on port $httpPort, UDN $udn")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        runCatching { sendByebye() }
        runCatching { ssdpSocket?.close() }
        runCatching { httpSocket?.close() }
        scheduler?.shutdownNow()
        scheduler = null
        pool.shutdownNow()
        pool = Executors.newCachedThreadPool()
        ssdpSocket = null
        httpSocket = null
        avtSubs.clear()
        rcSubs.clear()
        Log.i(TAG, "DLNA renderer stopped")
    }

    // ---------- Network helpers ----------

    /** The box's LAN address (the one senders must reach us on). */
    fun localIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.filter { !it.isLoopbackAddress }
                ?.sortedWith(compareBy({ it.isLinkLocalAddress }, { it.hostAddress }))
                ?.firstOrNull()?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun bindHttp(): Int {
        var port = HTTP_PORT_FIRST
        while (port < HTTP_PORT_FIRST + 20) {
            try {
                httpSocket = ServerSocket(port)
                val server = httpSocket!!
                pool.execute {
                    while (running && !server.isClosed) {
                        try {
                            val client = server.accept()
                            pool.execute { runCatching { handleHttp(client) } }
                        } catch (_: Exception) { /* closed while stopping */ }
                    }
                }
                return port
            } catch (_: Exception) {
                port++
            }
        }
        throw IllegalStateException("No free port for the DLNA server")
    }

    private fun startSsdp() {
        pool.execute {
            try {
                val socket = MulticastSocket(SSDP_PORT)
                socket.joinGroup(InetAddress.getByName(SSDP_ADDR))
                ssdpSocket = socket
                val buf = ByteArray(2048)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) { break }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (text.startsWith("M-SEARCH")) handleMSearch(packet, text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSDP listener ended", e)
            }
        }
    }

    private fun handleMSearch(packet: DatagramPacket, text: String) {
        val st = text.lines()
            .firstOrNull { it.startsWith("ST:", ignoreCase = true) }
            ?.substringAfter(":", "")?.trim() ?: return
        val wanted = st == "ssdp:all" || st == "upnp:rootdevice" ||
            st == "urn:schemas-upnp-org:device:MediaRenderer:1"
        if (!wanted) return
        val ip = localIpAddress() ?: return
        val location = "http://$ip:$httpPort/device.xml"
        val body = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=$CACHE_SECONDS\r\n" +
            "DATE: ${httpDate()}\r\n" +
            "EXT:\r\n" +
            "LOCATION: $location\r\n" +
            "SERVER: Zeph/1.5 UPnP/1.0\r\n" +
            "ST: $st\r\n" +
            "USN: uuid:$udn${if (st == "upnp:rootdevice") "" else "::urn:schemas-upnp-org:device:MediaRenderer:1"}\r\n" +
            "\r\n"
        try {
            val data = body.toByteArray(Charsets.UTF_8)
            ssdpSocket?.send(DatagramPacket(data, data.size, packet.address, packet.port))
        } catch (e: Exception) {
            Log.w(TAG, "SSDP reply failed", e)
        }
    }

    private fun sendAlive() {
        val ip = localIpAddress() ?: return
        val location = "http://$ip:$httpPort/device.xml"
        val types = listOf(
            "upnp:rootdevice" to "uuid:$udn",
            "urn:schemas-upnp-org:device:MediaRenderer:1" to "uuid:$udn::urn:schemas-upnp-org:device:MediaRenderer:1"
        )
        for ((nt, usn) in types) {
            val body = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: $SSDP_ADDR:$SSDP_PORT\r\n" +
                "CACHE-CONTROL: max-age=$CACHE_SECONDS\r\n" +
                "LOCATION: $location\r\n" +
                "NT: $nt\r\n" +
                "NTS: ssdp:alive\r\n" +
                "SERVER: Zeph/1.5 UPnP/1.0\r\n" +
                "USN: $usn\r\n" +
                "\r\n"
            sendSsdp(body)
        }
    }

    private fun sendByebye() {
        for ((nt, usn) in listOf(
            "upnp:rootdevice" to "uuid:$udn",
            "urn:schemas-upnp-org:device:MediaRenderer:1" to "uuid:$udn::urn:schemas-upnp-org:device:MediaRenderer:1"
        )) {
            val body = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: $SSDP_ADDR:$SSDP_PORT\r\n" +
                "NT: $nt\r\n" +
                "NTS: ssdp:byebye\r\n" +
                "USN: $usn\r\n" +
                "\r\n"
            sendSsdp(body)
        }
    }

    private fun sendSsdp(body: String) {
        try {
            val socket = ssdpSocket ?: return
            val data = body.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(SSDP_ADDR), SSDP_PORT))
        } catch (e: Exception) {
            Log.w(TAG, "SSDP notify failed", e)
        }
    }

    private fun httpDate(): String {
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
        return fmt.format(java.util.Date())
    }

    // ---------- HTTP + SOAP + GENA ----------

    private fun handleHttp(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val idx = line!!.indexOf(':')
                if (idx > 0) {
                    val name = line!!.substring(0, idx).trim().uppercase()
                    val value = line!!.substring(idx + 1).trim()
                    headers[name] = value
                    if (name == "CONTENT-LENGTH") contentLength = value.toIntOrNull() ?: 0
                }
            }
            val bodyChars = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(bodyChars, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            val body = String(bodyChars, 0, read)
            val out: OutputStream = socket.getOutputStream()
            when {
                method == "GET" && path == "/device.xml" -> respond(out, 200, "text/xml", deviceXml())
                method == "GET" && path == "/avtransport.xml" -> respond(out, 200, "text/xml", AVT_SCPD)
                method == "GET" && path == "/renderingcontrol.xml" -> respond(out, 200, "text/xml", RC_SCPD)
                method == "POST" && path == "/control/avtransport" -> handleSoap(out, headers, body, isAvt = true)
                method == "POST" && path == "/control/renderingcontrol" -> handleSoap(out, headers, body, isAvt = false)
                method == "SUBSCRIBE" && (path == "/event/avtransport" || path == "/event/renderingcontrol") ->
                    handleSubscribe(out, headers, path == "/event/avtransport")
                method == "UNSUBSCRIBE" && (path == "/event/avtransport" || path == "/event/renderingcontrol") ->
                    handleUnsubscribe(out, headers, path == "/event/avtransport")
                else -> respond(out, 404, "text/plain", "Not found")
            }
        }
    }

    private fun respond(out: OutputStream, code: Int, contentType: String, body: String, extraHeaders: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: $contentType; charset=\"utf-8\"\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            extraHeaders +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun deviceXml(): String {
        val ip = localIpAddress() ?: "127.0.0.1"
        return """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
<friendlyName>Zeph Player</friendlyName>
<manufacturer>Zeph</manufacturer>
<modelName>Zeph Player</modelName>
<modelNumber>1.1</modelNumber>
<UDN>uuid:$udn</UDN>
<presentationURL>http://$ip:$httpPort/device.xml</presentationURL>
<serviceList>
<service>
<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
<serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
<SCPDURL>/avtransport.xml</SCPDURL>
<controlURL>/control/avtransport</controlURL>
<eventSubURL>/event/avtransport</eventSubURL>
</service>
<service>
<serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
<serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
<SCPDURL>/renderingcontrol.xml</SCPDURL>
<controlURL>/control/renderingcontrol</controlURL>
<eventSubURL>/event/renderingcontrol</eventSubURL>
</service>
</serviceList>
</device>
</root>"""
    }

    private fun handleSoap(out: OutputStream, headers: Map<String, String>, body: String, isAvt: Boolean) {
        val soapAction = headers["SOAPACTION"]?.trim('"') ?: ""
        val action = soapAction.substringAfter('#', "")
        val args = parseSoapArgs(body)
        val h = handler
        if (h == null) {
            respond(out, 500, "text/xml", soapFault("No handler"))
            return
        }
        try {
            val result = if (isAvt) handleAvtAction(h, action, args) else handleRcAction(h, action, args)
            if (result == null) {
                respond(out, 500, "text/xml", soapFault("Unknown action: $action"))
            } else {
                respond(out, 200, "text/xml", soapEnvelope(action, result))
            }
        } catch (e: Exception) {
            Log.w(TAG, "SOAP $action failed", e)
            respond(out, 500, "text/xml", soapFault(e.message ?: "Failed"))
        }
    }

    private fun handleAvtAction(h: DlnaHandler, action: String, args: Map<String, String>): String? {
        val state = h.currentState()
        return when (action) {
            "SetAVTransportURI" -> {
                val uri = args["CurrentURI"] ?: ""
                val meta = args["CurrentURIMetaData"] ?: ""
                val title = parseDidlTitle(meta).ifBlank { uri.substringAfterLast('/') }
                h.onSetUri(uri, title)
                ""
            }
            "GetMediaInfo" -> {
                "<NrTracks>1</NrTracks>" +
                    "<MediaDuration>${formatTime(state.durationMs)}</MediaDuration>" +
                    "<CurrentURI>${xmlEscape(state.currentUri)}</CurrentURI>" +
                    "<CurrentURIMetaData></CurrentURIMetaData>" +
                    "<NextURI></NextURI><NextURIMetaData></NextURIMetaData>" +
                    "<PlayMedium>NETWORK</PlayMedium><RecordMedium>NOT_IMPLEMENTED</RecordMedium>" +
                    "<WriteStatus>NOT_IMPLEMENTED</WriteStatus>"
            }
            "GetTransportInfo" -> {
                "<CurrentTransportState>${state.transportState}</CurrentTransportState>" +
                    "<CurrentTransportStatus>OK</CurrentTransportStatus>" +
                    "<CurrentSpeed>1</CurrentSpeed>"
            }
            "GetPositionInfo" -> {
                "<Track>1</Track>" +
                    "<TrackDuration>${formatTime(state.durationMs)}</TrackDuration>" +
                    "<TrackMetaData></TrackMetaData>" +
                    "<TrackURI>${xmlEscape(state.currentUri)}</TrackURI>" +
                    "<RelTime>${formatTime(state.positionMs)}</RelTime>" +
                    "<AbsTime>${formatTime(state.positionMs)}</AbsTime>" +
                    "<RelCount>0</RelCount><AbsCount>0</AbsCount>"
            }
            "GetDeviceCapabilities" -> {
                "<PlayMedia>NETWORK</PlayMedia><RecMedia>NOT_IMPLEMENTED</RecMedia>" +
                    "<RecQualityModes>NOT_IMPLEMENTED</RecQualityModes>"
            }
            "GetTransportSettings" -> "<PlayMode>NORMAL</PlayMode><RecQualityMode>NOT_IMPLEMENTED</RecQualityMode>"
            "Play" -> {
                h.onPlay()
                ""
            }
            "Pause" -> {
                h.onPause()
                ""
            }
            "Stop" -> {
                h.onStop()
                ""
            }
            "Seek" -> {
                val target = args["Target"] ?: "0:00:00"
                h.onSeekMs(parseTime(target))
                ""
            }
            else -> null
        }
    }

    private fun handleRcAction(h: DlnaHandler, action: String, args: Map<String, String>): String? {
        val state = h.currentState()
        return when (action) {
            "GetVolume" -> "<CurrentVolume>${state.volumePercent}</CurrentVolume>"
            "SetVolume" -> {
                (args["DesiredVolume"]?.toIntOrNull())?.let { h.onSetVolume(it.coerceIn(0, 100)) }
                ""
            }
            "GetMute" -> "<CurrentMute>${if (state.muted) 1 else 0}</CurrentMute>"
            "SetMute" -> {
                (args["DesiredMute"]?.let { it == "1" || it.equals("true", ignoreCase = true) })?.let { h.onSetMute(it) }
                ""
            }
            "GetVolumeDBRange" -> "<MinValue>-60</MinValue><MaxValue>0</MaxValue>"
            else -> null
        }
    }

    private fun soapEnvelope(action: String, resultXml: String): String {
        val service = if (action in setOf("GetVolume", "SetVolume", "GetMute", "SetMute", "GetVolumeDBRange"))
            "urn:schemas-upnp-org:service:RenderingControl:1"
        else "urn:schemas-upnp-org:service:AVTransport:1"
        return """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:${action}Response xmlns:u="$service">$resultXml</u:${action}Response></s:Body>
</s:Envelope>"""
    }

    private fun soapFault(message: String): String {
        return """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>${xmlEscape(message)}</faultstring></s:Fault></s:Body>
</s:Envelope>"""
    }

    /** Pull the named arguments out of a SOAP body, by element name.
     * Only leaf elements are arguments (the envelope and the action
     * element hold other elements, not values). */
    private fun parseSoapArgs(body: String): Map<String, String> {
        val doc = parseXmlDocument(body) ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as? Element ?: continue
            var hasElementChild = false
            val children = el.childNodes
            for (j in 0 until children.length) {
                if (children.item(j).nodeType == Node.ELEMENT_NODE) { hasElementChild = true; break }
            }
            if (!hasElementChild) {
                val text = el.textContent?.trim() ?: ""
                if (text.isNotEmpty()) out[el.tagName] = text
            }
        }
        return out
    }

    /** The dc:title from a DIDL-Lite metadata block, or "" when absent. */
    fun parseDidlTitle(didl: String): String {
        val doc = parseXmlDocument(didl) ?: return ""
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as? Element ?: continue
            // Exactly "title", or a namespaced "dc:title" — not "subtitle".
            if (el.tagName == "title" || el.tagName.endsWith(":title")) {
                return el.textContent?.trim() ?: ""
            }
        }
        return ""
    }

    /** Parse a small XML string with the platform DOM parser (the same
     * code runs in unit tests and on the box). External entities are
     * off: the XML comes from a device on the user's own Wi-Fi, but it
     * is still untrusted input, and a DIDL never needs a DOCTYPE. */
    private fun parseXmlDocument(xml: String): org.w3c.dom.Document? {
        if (xml.isBlank()) return null
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isCoalescing = true
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "XML parse failed", e)
            null
        }
    }

    // ---------- GENA eventing ----------

    private fun handleSubscribe(out: OutputStream, headers: Map<String, String>, isAvt: Boolean) {
        val sid = headers["SID"]
        val callback = headers["CALLBACK"]?.trim('<', '>', ' ') ?: ""
        val timeout = headers["TIMEOUT"]?.substringAfter("Second-", "")?.toIntOrNull() ?: 1800
        val subs = if (isAvt) avtSubs else rcSubs
        if (sid != null) {
            // Renewal.
            subs[sid]?.let {
                it.expiresAt = System.currentTimeMillis() + timeout * 1000L
                respond(out, 200, "text/xml", "", "SID: $sid\r\nTIMEOUT: Second-$timeout\r\n")
                return
            }
        }
        if (callback.isBlank()) {
            respond(out, 400, "text/plain", "Missing CALLBACK")
            return
        }
        val newSid = "uuid:" + UUID.randomUUID().toString()
        subs[newSid] = Subscription(callback, newSid, expiresAt = System.currentTimeMillis() + timeout * 1000L)
        respond(out, 200, "text/xml", "", "SID: $newSid\r\nTIMEOUT: Second-$timeout\r\n")
        // Send the current state right away, so the sender does not wait.
        pool.execute { runCatching { notifyOne(subs[newSid]!!, isAvt) } }
    }

    private fun handleUnsubscribe(out: OutputStream, headers: Map<String, String>, isAvt: Boolean) {
        val sid = headers["SID"] ?: ""
        (if (isAvt) avtSubs else rcSubs).remove(sid)
        respond(out, 200, "text/xml", "")
    }

    /** Notify every subscriber whose state changed, plus a position
     * heartbeat while playing (senders poll too, so this is a courtesy). */
    fun notifyStateChanged() {
        val state = handler?.currentState() ?: return
        val key = "${state.transportState}|${state.currentUri}|${state.volumePercent}|${state.muted}"
        if (key != lastNotifiedState) {
            lastNotifiedState = key
            avtSubs.values.toList().forEach { sub -> pool.execute { runCatching { notifyOne(sub, true) } } }
            rcSubs.values.toList().forEach { sub -> pool.execute { runCatching { notifyOne(sub, false) } } }
        }
    }

    private fun maybeNotifyPosition() {
        val state = handler?.currentState() ?: return
        if (state.transportState != "PLAYING") return
        val now = System.currentTimeMillis()
        if (now - lastPositionNotifyMs < 1000) return
        lastPositionNotifyMs = now
        avtSubs.values.toList().forEach { sub -> pool.execute { runCatching { notifyOne(sub, true) } } }
    }

    private fun notifyOne(sub: Subscription, isAvt: Boolean) {
        if (System.currentTimeMillis() > sub.expiresAt) {
            (if (isAvt) avtSubs else rcSubs).remove(sub.sid)
            return
        }
        val state = handler?.currentState() ?: return
        val lastChange = if (isAvt) {
            "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT/\">" +
                "<InstanceID val=\"0\">" +
                "<TransportState val=\"${state.transportState}\"/>" +
                "<CurrentTrackURI val=\"${xmlEscape(state.currentUri)}\"/>" +
                "<CurrentTrackDuration val=\"${formatTime(state.durationMs)}\"/>" +
                "<RelativeTimePosition val=\"${formatTime(state.positionMs)}\"/>" +
                "</InstanceID></Event>"
        } else {
            "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\">" +
                "<InstanceID val=\"0\">" +
                "<Volume channel=\"Master\" val=\"${state.volumePercent}\"/>" +
                "<Mute channel=\"Master\" val=\"${if (state.muted) 1 else 0}\"/>" +
                "</InstanceID></Event>"
        }
        val body = "<?xml version=\"1.0\"?>\n" +
            "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" +
            "<e:property><LastChange>${xmlEscape(lastChange)}</LastChange></e:property>" +
            "</e:propertyset>"
        val url = sub.callback
        val hostPort = url.substringAfter("http://", "").substringBefore('/', "")
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "80").toIntOrNull() ?: 80
        val path = "/" + url.substringAfter('/', "")
        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 5_000
                val bytes = body.toByteArray(Charsets.UTF_8)
                val head = "NOTIFY $path HTTP/1.1\r\n" +
                    "HOST: $hostPort\r\n" +
                    "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "NT: upnp:event\r\n" +
                    "NTS: upnp:propchange\r\n" +
                    "SID: ${sub.sid}\r\n" +
                    "SEQ: ${sub.seq++}\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().write(head.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "NOTIFY failed, dropping sub ${sub.sid}", e)
            (if (isAvt) avtSubs else rcSubs).remove(sub.sid)
        }
    }

    // ---------- Small helpers ----------

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun parseTime(value: String): Long {
        val parts = value.split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                else -> (value.toDouble() * 1000).toLong()
            }
        } catch (_: Exception) { 0L }
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private const val AVT_SCPD = """<?xml version="1.0"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<actionList>
<action><name>SetAVTransportURI</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
<argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetMediaInfo</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>
<argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>
<argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
<argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
<argument><name>NextURI</name><direction>out</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>
<argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>
<argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>PlaybackStorageMedium</relatedStateVariable></argument>
<argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>RecordStorageMedium</relatedStateVariable></argument>
<argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>RecordMediumWriteStatus</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetTransportInfo</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>
<argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>
<argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetPositionInfo</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Track</name><direction>out</direction><relatedStateVariable>CurrentTrack</relatedStateVariable></argument>
<argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>
<argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>CurrentTrackMetaData</relatedStateVariable></argument>
<argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>CurrentTrackURI</relatedStateVariable></argument>
<argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>
<argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>
<argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounterPosition</relatedStateVariable></argument>
<argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounterPosition</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetDeviceCapabilities</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>PlayMedia</name><direction>out</direction><relatedStateVariable>PossiblePlaybackStorageMedia</relatedStateVariable></argument>
<argument><name>RecMedia</name><direction>out</direction><relatedStateVariable>PossibleRecordStorageMedia</relatedStateVariable></argument>
<argument><name>RecQualityModes</name><direction>out</direction><relatedStateVariable>PossibleRecordQualityModes</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetTransportSettings</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>PlayMode</name><direction>out</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>
<argument><name>RecQualityMode</name><direction>out</direction><relatedStateVariable>CurrentRecordQualityMode</relatedStateVariable></argument>
</argumentList></action>
<action><name>Play</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
</argumentList></action>
<action><name>Pause</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
</argumentList></action>
<action><name>Stop</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
</argumentList></action>
<action><name>Seek</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Unit</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable></argument>
<argument><name>Target</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable></argument>
</argumentList></action>
</actionList>
<serviceStateTable>
<stateVariable sendEvents="no"><name>TransportState</name><dataType>string</dataType><allowedValueList><allowedValue>STOPPED</allowedValue><allowedValue>PLAYING</allowedValue><allowedValue>PAUSED_PLAYBACK</allowedValue><allowedValue>TRANSITIONING</allowedValue><allowedValue>NO_MEDIA_PRESENT</allowedValue></allowedValueList></stateVariable>
<stateVariable sendEvents="no"><name>TransportStatus</name><dataType>string</dataType><allowedValueList><allowedValue>OK</allowedValue><allowedValue>ERROR_OCCURRED</allowedValue></allowedValueList></stateVariable>
<stateVariable sendEvents="no"><name>PlaybackStorageMedium</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>RecordStorageMedium</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>PossiblePlaybackStorageMedia</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>PossibleRecordStorageMedia</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentPlayMode</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>RecordMediumWriteStatus</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>PossibleRecordQualityModes</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentTrack</name><dataType>ui4</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentTrackMetaData</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>CurrentTrackURI</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>RelativeCounterPosition</name><dataType>i4</dataType></stateVariable>
<stateVariable sendEvents="no"><name>AbsoluteCounterPosition</name><dataType>i4</dataType></stateVariable>
<stateVariable sendEvents="yes"><name>LastChange</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekMode</name><dataType>string</dataType><allowedValueList><allowedValue>ABS_TIME</allowedValue><allowedValue>REL_TIME</allowedValue><allowedValue>TRACK_NR</allowedValue></allowedValueList></stateVariable>
<stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
</serviceStateTable>
</scpd>"""

    private const val RC_SCPD = """<?xml version="1.0"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<actionList>
<action><name>GetVolume</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
<argument><name>CurrentVolume</name><direction>out</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
</argumentList></action>
<action><name>SetVolume</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
<argument><name>DesiredVolume</name><direction>in</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetMute</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
<argument><name>CurrentMute</name><direction>out</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
</argumentList></action>
<action><name>SetMute</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
<argument><name>DesiredMute</name><direction>in</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
</argumentList></action>
<action><name>GetVolumeDBRange</name><argumentList>
<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
<argument><name>MinValue</name><direction>out</direction><relatedStateVariable>VolumeDB</relatedStateVariable></argument>
<argument><name>MaxValue</name><direction>out</direction><relatedStateVariable>VolumeDB</relatedStateVariable></argument>
</argumentList></action>
</actionList>
<serviceStateTable>
<stateVariable sendEvents="no"><name>Volume</name><dataType>ui2</dataType><allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange></stateVariable>
<stateVariable sendEvents="no"><name>Mute</name><dataType>boolean</dataType></stateVariable>
<stateVariable sendEvents="no"><name>VolumeDB</name><dataType>i2</dataType></stateVariable>
<stateVariable sendEvents="yes"><name>LastChange</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType></stateVariable>
<stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
</serviceStateTable>
</scpd>"""
}
