package com.playbox.tv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.util.Properties

/**
 * Batch 2: SMB / NAS browsing + playback (jcifs-ng, pure Java, SMB2/3).
 *
 * Two halves: browsing (list a server's folders/files) and playback.
 * ExoPlayer cannot open smb:// URIs, so the player's data-source factory
 * routes smb:// to [SmbDataSource] and everything else to Media3's
 * default source (see [RoutingDataSourceFactory], wired in
 * PlaybackService).
 *
 * NOTE: server passwords are stored in plain text in SharedPreferences
 * on the user's own TV — the same place their Wi-Fi password lives.
 * They never leave the device, and the smb:// URLs we keep never embed
 * credentials (auth lives in the CIFSContext, not the URL).
 */

data class SmbServer(val host: String, val share: String, val user: String, val pass: String) {
    fun url(): String = "smb://$host/$share/"
}

data class SmbEntry(
    val name: String,
    val url: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)

object SmbNetwork {
    private const val TAG = "PlayBoxSmb"
    private const val PREFS = "playbox_smb_servers"
    private const val KEY = "servers"

    fun servers(context: Context): List<SmbServer> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 2) SmbServer(parts[0], parts[1], parts.getOrNull(2) ?: "", parts.getOrNull(3) ?: "")
            else null
        }.sortedBy { it.host.lowercase() }
    }

    fun saveServer(context: Context, server: SmbServer) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll { it.split("|").take(2) == listOf(server.host, server.share) }
        set.add(listOf(server.host, server.share, server.user, server.pass).joinToString("|"))
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun removeServer(context: Context, server: SmbServer) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll { it.split("|").take(2) == listOf(server.host, server.share) }
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun serverForHost(context: Context, host: String): SmbServer? =
        servers(context).firstOrNull { it.host.equals(host, ignoreCase = true) }

    /** A context for one server: guest when no username, NTLM otherwise. */
    fun contextFor(server: SmbServer): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.soTimeout", "8000")
            setProperty("jcifs.smb.client.responseTimeout", "8000")
            setProperty("jcifs.smb.client.connTimeout", "8000")
        }
        val base = BaseContext(PropertyConfiguration(props))
        return if (server.user.isBlank()) {
            @Suppress("DEPRECATION")
            base.withGuestCrendentials()
        } else {
            base.withCredentials(NtlmPasswordAuthenticator("", server.user, server.pass))
        }
    }

    /** Context for playing an smb:// URL: the saved server for that host,
     * or a guest context when the server was never saved. */
    fun contextForUrl(context: Context, url: String): CIFSContext {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: ""
        val server = serverForHost(context, host)
        return if (server != null) contextFor(server) else contextFor(SmbServer(host, "", "", ""))
    }

    /** List one folder on a server: folders first, then files, by name.
     * Throws on auth/network failure — the UI turns that into a message. */
    fun listFolder(server: SmbServer, url: String): List<SmbEntry> {
        val ctx = contextFor(server)
        val folder = SmbFile(url, ctx)
        return folder.listFiles()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<SmbFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map {
                SmbEntry(
                    name = it.name.trimEnd('/'),
                    url = it.url.toString(),
                    isDir = it.isDirectory,
                    size = runCatching { it.length() }.getOrDefault(0L),
                    lastModified = runCatching { it.lastModified() }.getOrDefault(0L)
                )
            }
    }
}

/** Batch 2: reads one smb:// file through jcifs-ng. Seek = reopen and
 * skip, which is what ExoPlayer expects from a DataSource. */
@UnstableApi
private class SmbDataSource(private val smbContext: CIFSContext) : BaseDataSource(true) {
    private var input: InputStream? = null
    private var bytesRemaining: Long = 0
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val file = SmbFile(dataSpec.uri.toString(), smbContext)
        val length = runCatching { file.length() }.getOrDefault(C.LENGTH_UNSET.toLong())
        input = file.inputStream
        var toSkip = dataSpec.position
        while (toSkip > 0) {
            val skipped = input!!.skip(toSkip)
            if (skipped <= 0) break
            toSkip -= skipped
        }
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
        else if (length != C.LENGTH_UNSET.toLong()) length - dataSpec.position
        else C.LENGTH_UNSET.toLong()
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
        else minOf(length.toLong(), bytesRemaining).toInt()
        val read = input?.read(buffer, offset, toRead) ?: -1
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { input?.close() }
        input = null
        transferEnded()
    }
}

/** Batch 2: smb:// goes to jcifs-ng, everything else to the default
 * Media3 data source. Created once per player (PlaybackService). */
@UnstableApi
class RoutingDataSourceFactory(private val appContext: Context) : DataSource.Factory {
    private val defaultFactory = DefaultDataSource.Factory(appContext)
    override fun createDataSource(): DataSource = RoutingDataSource(appContext, defaultFactory.createDataSource())
}

@UnstableApi
private class RoutingDataSource(
    private val appContext: Context,
    private val defaultSource: DataSource
) : BaseDataSource(true) {
    private var active: DataSource? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        active = if (dataSpec.uri.scheme == "smb") {
            SmbDataSource(SmbNetwork.contextForUrl(appContext, dataSpec.uri.toString()))
        } else {
            defaultSource
        }
        val len = active!!.open(dataSpec)
        transferStarted(dataSpec)
        return len
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = active?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (read > 0) bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        active?.close()
        active = null
        uri = null
        transferEnded()
    }
}
