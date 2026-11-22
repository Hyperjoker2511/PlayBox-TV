package com.playbox.tv

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.util.Vector

/**
 * Patch 7: FTP / SFTP browsing + playback (Apache Commons Net for
 * FTP, JSch for SFTP).
 *
 * Playback goes through LibVLC — Media3 has no FTP/SFTP source, so
 * every FTP/SFTP file routes to the VLC engine (see engineFor), which
 * plays ftp:// and sftp:// URLs natively. That is why the play URL
 * carries the login in it, unlike the smb:// URLs (where the auth
 * lives in the CIFSContext): VLC needs it in the URL.
 *
 * NOTE: server passwords are stored in plain text in
 * SharedPreferences on the user's own TV — the same place the SMB
 * passwords already live. They never leave the device.
 */

data class FtpServer(val protocol: String, val host: String, val port: Int, val user: String, val pass: String) {
    fun rootPath(): String = "/"
    fun label(): String = "$protocol://$host:$port"
}

data class FtpEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)

/** "ftp://192.168.1.5:21" for a server row. (unit-tested) */
internal fun formatFtpServer(server: FtpServer): String = server.label()

/** The URL VLC plays for one file: credentials in the userinfo part,
 * each path segment URL-encoded. Anonymous FTP (blank user) has no
 * userinfo at all. Pure Java (URLEncoder, not android.net.Uri) so
 * the unit tests can run it. (unit-tested) */
internal fun ftpPlayUrl(server: FtpServer, path: String): String {
    val userinfo = if (server.user.isBlank()) "" else
        urlEncodeSegment(server.user) + ":" + urlEncodeSegment(server.pass) + "@"
    val encodedPath = path.split("/").joinToString("/") { urlEncodeSegment(it) }
    return "${server.protocol}://$userinfo${server.host}:${server.port}$encodedPath"
}

/** One URL segment, percent-encoded. URLEncoder is form encoding —
 * it writes a space as +, so the + is fixed to %20 after. */
internal fun urlEncodeSegment(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

object FtpNetwork {
    private const val PREFS = "playbox_ftp_servers"
    private const val KEY = "servers"

    fun servers(context: Context): List<FtpServer> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 4) FtpServer(
                parts[0], parts[1],
                parts[2].toIntOrNull() ?: if (parts[0] == "sftp") 22 else 21,
                parts.getOrNull(3) ?: "", parts.getOrNull(4) ?: ""
            ) else null
        }.sortedBy { it.host.lowercase() }
    }

    fun saveServer(context: Context, server: FtpServer) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll {
            val p = it.split("|")
            p.size >= 3 && p[0] == server.protocol && p[1] == server.host && p[2] == server.port.toString()
        }
        set.add(listOf(server.protocol, server.host, server.port.toString(), server.user, server.pass).joinToString("|"))
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun removeServer(context: Context, server: FtpServer) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.removeAll {
            val p = it.split("|")
            p.size >= 3 && p[0] == server.protocol && p[1] == server.host && p[2] == server.port.toString()
        }
        prefs.edit().putStringSet(KEY, set).apply()
    }

    /** List one folder on a server: folders first, then files, by
     * name. Throws on auth/network failure — the UI turns that into
     * a message. */
    fun listFolder(server: FtpServer, path: String): List<FtpEntry> =
        if (server.protocol == "sftp") listSftp(server, path) else listFtp(server, path)

    private fun listFtp(server: FtpServer, path: String): List<FtpEntry> {
        val ftp = FTPClient()
        try {
            ftp.connectTimeout = 8000
            ftp.connect(server.host, server.port)
            if (server.user.isBlank()) ftp.login("anonymous", "") else ftp.login(server.user, server.pass)
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            val files = ftp.listFiles(path)
            return files.filter { !it.name.startsWith(".") }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map {
                    FtpEntry(
                        name = it.name,
                        path = joinFtpPath(path, it.name),
                        isDir = it.isDirectory,
                        size = it.size,
                        lastModified = it.timestamp?.timeInMillis ?: 0L
                    )
                }
        } finally {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    private fun listSftp(server: FtpServer, path: String): List<FtpEntry> {
        val jsch = JSch()
        val session = jsch.getSession(server.user, server.host, server.port)
        session.setPassword(server.pass)
        // The user's own server, on their own Wi-Fi — no known-hosts
        // file on a TV. (The same trust call SMB makes by NTLM.)
        session.setConfig("StrictHostKeyChecking", "no")
        try {
            session.connect(8000)
            val channel = session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(8000)
                @Suppress("UNCHECKED_CAST")
                val raw = channel.ls(path) as Vector<ChannelSftp.LsEntry>
                return raw.filter { !it.filename.startsWith(".") }
                    .sortedWith(compareBy({ !it.attrs.isDir }, { it.filename.lowercase() }))
                    .map {
                        FtpEntry(
                            name = it.filename,
                            path = joinFtpPath(path, it.filename),
                            isDir = it.attrs.isDir,
                            size = it.attrs.size,
                            lastModified = it.attrs.mTime.toLong() * 1000L
                        )
                    }
            } finally {
                runCatching { channel.disconnect() }
            }
        } finally {
            runCatching { session.disconnect() }
        }
    }

    /** "/Movies" + "film.mkv" -> "/Movies/film.mkv". (unit-tested) */
    internal fun joinFtpPath(dir: String, name: String): String =
        dir.trimEnd('/') + "/" + name
}
