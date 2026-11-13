package com.mutinymedia.android.data

import com.mutinymedia.android.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.util.Base64

class AccountException(message: String, val expired: Boolean = false) : Exception(message)
data class AccountReply(val status: Int, val body: String)
fun interface AccountTransport {
    suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): AccountReply
}

/** Account requests never follow redirects or put credentials in URLs/logs. */
object AccountHttp : AccountTransport {
    override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): AccountReply = withContext(Dispatchers.IO) {
        val connection = URL(Network.validateHttps(url)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "MutinyMediaAndroid/0.2")
            headers.forEach { (key, value) ->
                require(!key.contains('\n') && !value.contains('\n') && !key.contains('\r') && !value.contains('\r'))
                connection.setRequestProperty(key, value)
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val text=input?.let { Network.readLimited(it, 4_000_000) }.orEmpty()
            currentCoroutineContext().ensureActive()
            AccountReply(status,text)
        } catch (_: java.io.IOException) {
            throw AccountException("Account service is unreachable. Check your connection and try again.")
        } finally { connection.disconnect() }
    }
}

internal fun parseAccountJson(text: String): Any = try {
    JSONTokener(text).nextValue()
} catch (_: Exception) { throw AccountException("The account service returned an invalid response") }
internal fun requiredText(json: JSONObject, field: String): String = json.optString(field).takeIf { it.isNotBlank() && it != "null" }
    ?: throw AccountException("The account service returned an incomplete response")
internal fun validBase(raw: String): String {
    val value = Network.validateHttps(raw).trimEnd('/')
    val uri = URI(value)
    require(uri.rawQuery == null && uri.rawFragment == null) { "Use the HTTPS server address without a query or fragment" }
    return value
}
internal fun mediaFromAccount(json: JSONObject, idKey: String, typeKey: String, titleKey: String): MediaItem? {
    val id = json.optString(idKey)
    val type = json.optString(typeKey)
    val title = json.optString(titleKey)
    if (id.isBlank() || id == "null" || title.isBlank() || title == "null" || type !in listOf("movie", "series", "anime")) return null
    return MediaItem(id, type, title, json.optString("poster").takeIf { it.isNotBlank() && it != "null" },
        json.optString("description").takeIf { it.isNotBlank() && it != "null" }, type == "anime" || id.startsWith("kitsu:") || id.startsWith("mal:"))
}

// Tokens intentionally have no generated toString(): logging a session must not reveal credentials.
class StremioSession(val token: String, val userId: String, val email: String) {
    fun encode() = JSONObject().put("token", token).put("userId", userId).put("email", email).toString()
    companion object { fun decode(raw: String): StremioSession { val j = JSONObject(raw); return StremioSession(requiredText(j,"token"), requiredText(j,"userId"), requiredText(j,"email")) } }
}
class StremioAccountClient(private val transport: AccountTransport = AccountHttp) {
    private suspend fun call(method: String, body: JSONObject): Any {
        val reply = transport.request("POST", "https://api.strem.io/api/$method", emptyMap(), body.toString())
        if (reply.status !in 200..299) throw AccountException("Stremio returned HTTP ${reply.status}", reply.status == 401 || reply.status == 403)
        val json = parseAccountJson(reply.body) as? JSONObject ?: throw AccountException("Stremio returned an invalid response")
        json.optJSONObject("error")?.let { error ->
            val invalidSession = error.optInt("code") == 1
            throw AccountException(if (method == "login") "Stremio did not accept that email/password. If you use social sign-in, import an auth key instead." else "Stremio account request failed. Sign in again if your session has expired.", invalidSession)
        }
        return json.opt("result")?.takeIf { it != JSONObject.NULL } ?: throw AccountException("Stremio returned no account data")
    }
    suspend fun login(email: String, password: String): StremioSession {
        require(email.trim().contains('@') && password.isNotEmpty()) { "Enter your Stremio email and password" }
        val result = call("login", JSONObject().put("email", email.trim()).put("password", password)) as? JSONObject
            ?: throw AccountException("Stremio returned invalid sign-in data")
        val user = result.optJSONObject("user") ?: throw AccountException("Stremio did not return a user")
        return StremioSession(requiredText(result,"authKey"), requiredText(user,"_id"), requiredText(user,"email"))
    }
    suspend fun verify(token: String): StremioSession {
        require(token.isNotBlank()) { "Enter a Stremio auth key" }
        val user = call("getUser", JSONObject().put("authKey", token.trim())) as? JSONObject
            ?: throw AccountException("Stremio returned invalid user data")
        return StremioSession(token.trim(), requiredText(user,"_id"), requiredText(user,"email"))
    }
    suspend fun addons(session: StremioSession): List<String> {
        val result = call("addonCollectionGet", JSONObject().put("authKey",session.token).put("update",true).put("addFromURL",JSONArray())) as? JSONObject
            ?: throw AccountException("Stremio returned an invalid addon collection")
        val entries = result.optJSONArray("addons") ?: throw AccountException("Stremio returned no addon collection")
        return (0 until entries.length()).mapNotNull { i ->
            val item = entries.optJSONObject(i) ?: return@mapNotNull null
            if (item.optJSONObject("flags")?.optBoolean("disabled") == true) return@mapNotNull null
            runCatching { StremioClient().normalizeBase(item.optString("transportUrl")) }.getOrNull()
        }.distinct()
    }
    suspend fun library(session: StremioSession): List<MediaItem> {
        val result = call("datastoreGet", JSONObject().put("authKey",session.token).put("collection","library").put("ids",JSONArray()).put("all",true))
        val entries = result as? JSONArray ?: (result as? JSONObject)?.optJSONArray("items")
            ?: throw AccountException("Stremio returned an invalid library")
        return (0 until entries.length()).mapNotNull { index ->
            val item = entries.optJSONObject(index) ?: return@mapNotNull null
            if (item.optBoolean("removed") || item.optBoolean("temp")) null else mediaFromAccount(item,"_id","type","name")
        }.distinctBy { it.type to it.id }.take(2_000)
    }
    suspend fun logout(session: StremioSession) { call("logout",JSONObject().put("authKey",session.token)) }
}

data class NuvioServer(val base: String, val publicKey: String)
data class NuvioProfile(val index: Int, val name: String, val pinEnabled: Boolean, val primaryAddons: Boolean)
class NuvioSession(val server: NuvioServer, val access: String, val refresh: String, val expiresAt: Long, val userId: String, val email: String, val profile: Int = 1) {
    fun withProfile(value: Int) = NuvioSession(server,access,refresh,expiresAt,userId,email,value)
    fun encode() = JSONObject().put("server",server.base).put("publicKey",server.publicKey).put("access",access).put("refresh",refresh)
        .put("expiresAt",expiresAt).put("userId",userId).put("email",email).put("profile",profile).toString()
    companion object { fun decode(raw: String): NuvioSession { val j=JSONObject(raw); return NuvioSession(NuvioServer(validBase(requiredText(j,"server")),requiredText(j,"publicKey")),requiredText(j,"access"),requiredText(j,"refresh"),j.getLong("expiresAt"),requiredText(j,"userId"),requiredText(j,"email"),j.optInt("profile",1)) } }
}
class NuvioAccountAdapter(private val transport: AccountTransport = AccountHttp) {
    suspend fun discover(url: String): NuvioServer {
        val base = validBase(url).removeSuffix("/.well-known/nuvio")
        val j = request("GET", "$base/.well-known/nuvio", emptyMap(), null) as? JSONObject
            ?: throw AccountException("This server did not publish Nuvio connection information")
        require(j.optInt("version") == 1 && j.optString("service").equals("nuvio",true)) { "This is not a supported Nuvio server" }
        require(j.optJSONObject("capabilities")?.optBoolean("email_password_auth") == true) { "This Nuvio server does not support email sign-in" }
        val publicKey = requiredText(j,"publishable_key")
        if (publicKey.startsWith("eyJ")) {
            val role = runCatching { JSONObject(String(Base64.getUrlDecoder().decode(publicKey.split('.')[1]),Charsets.UTF_8)).optString("role") }.getOrNull()
            require(role == "anon") { "The Nuvio server must publish an anonymous client key, not a privileged key" }
        } else require(publicKey.startsWith("sb_publishable_")) { "The Nuvio server did not publish a supported public client key" }
        return NuvioServer(validBase(requiredText(j,"backend_url")),publicKey)
    }
    private suspend fun request(method: String, url: String, headers: Map<String,String>, body: JSONObject?): Any {
        val reply = transport.request(method,url,headers,body?.toString())
        if (reply.status !in 200..299) {
            val expired = reply.status == 401 || (reply.status == 400 && url.contains("grant_type=refresh_token"))
            throw AccountException(if (expired) "Nuvio sign-in was not accepted or the session expired" else "Nuvio returned HTTP ${reply.status}. Check your account/server and try again.",expired)
        }
        return if (reply.body.isBlank()) JSONObject() else parseAccountJson(reply.body)
    }
    private fun headers(server: NuvioServer, token: String? = null) = mapOf("apikey" to server.publicKey, "Authorization" to "Bearer ${token ?: server.publicKey}")
    private fun session(server: NuvioServer, data: Any, previous: NuvioSession? = null): NuvioSession {
        val j = data as? JSONObject ?: throw AccountException("Nuvio returned invalid sign-in data")
        val user = j.optJSONObject("user") ?: throw AccountException("Nuvio did not return a user")
        val expires = j.optLong("expires_at").takeIf { it > 0 } ?: (System.currentTimeMillis()/1000 + j.optLong("expires_in",3600))
        return NuvioSession(server,requiredText(j,"access_token"),requiredText(j,"refresh_token"),expires,requiredText(user,"id"),requiredText(user,"email"),previous?.profile ?: 1)
    }
    suspend fun login(serverUrl: String, email: String, password: String): NuvioSession {
        require(email.trim().contains('@') && password.isNotEmpty()) { "Enter your Nuvio email and password" }
        val server = discover(serverUrl)
        return session(server,request("POST","${server.base}/auth/v1/token?grant_type=password",headers(server),JSONObject().put("email",email.trim()).put("password",password)))
    }
    suspend fun refresh(saved: NuvioSession): NuvioSession = session(saved.server,request("POST","${saved.server.base}/auth/v1/token?grant_type=refresh_token",headers(saved.server),JSONObject().put("refresh_token",saved.refresh)),saved)
    suspend fun verify(saved: NuvioSession): NuvioSession {
        var active = if (saved.expiresAt <= System.currentTimeMillis() / 1000 + 60) refresh(saved) else saved
        suspend fun checkIdentity(session: NuvioSession) {
            val user = request("GET", "${session.server.base}/auth/v1/user", headers(session.server, session.access), null) as? JSONObject
                ?: throw AccountException("Nuvio returned invalid user data")
            if (requiredText(user, "id") != saved.userId) {
                throw AccountException("Nuvio session identity could not be verified", expired = true)
            }
        }
        try {
            checkIdentity(active)
        } catch (e: AccountException) {
            if (!e.expired || active !== saved) throw e
            active = refresh(saved)
            checkIdentity(active)
        }
        return active
    }
    private suspend fun rpc(session: NuvioSession, name: String, body: JSONObject) = request("POST","${session.server.base}/rest/v1/rpc/$name",headers(session.server,session.access),body)
    suspend fun profiles(session: NuvioSession): List<NuvioProfile> {
        val rows = rpc(session,"sync_pull_profiles",JSONObject()) as? JSONArray ?: throw AccountException("Nuvio returned invalid profiles")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { j ->
            val index=j.optInt("profile_index");if(index !in 1..6) null else NuvioProfile(index,j.optString("name").ifBlank { "Profile $index" },j.optBoolean("pin_enabled"),j.optBoolean("uses_primary_addons"))
        } }.sortedBy { it.index }
    }
    suspend fun unlock(session: NuvioSession, profile: Int, pin: String): Boolean {
        val value=rpc(session,"verify_profile_pin",JSONObject().put("p_profile_id",profile).put("p_pin",pin))
        val j=(value as? JSONObject) ?: (value as? JSONArray)?.optJSONObject(0)
        return j?.optBoolean("unlocked") == true
    }
    suspend fun addons(session: NuvioSession, profile: NuvioProfile): List<String> {
        val index=if(profile.primaryAddons) 1 else profile.index
        val rows=request("GET","${session.server.base}/rest/v1/addons?select=url,enabled&profile_id=eq.$index&order=sort_order.asc",headers(session.server,session.access),null) as? JSONArray
            ?: throw AccountException("Nuvio returned invalid addons")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.takeIf { it.optBoolean("enabled",true) }?.let { runCatching { StremioClient().normalizeBase(it.optString("url")) }.getOrNull() } }.distinct()
    }
    suspend fun library(session: NuvioSession): List<MediaItem> {
        val all=mutableListOf<MediaItem>()
        for (offset in 0 until 2_000 step 100) {
            val rows=rpc(session,"sync_pull_library",JSONObject().put("p_profile_id",session.profile).put("p_limit",100).put("p_offset",offset)) as? JSONArray
                ?: throw AccountException("Nuvio returned an invalid library")
            (0 until rows.length()).forEach { i -> rows.optJSONObject(i)?.let { mediaFromAccount(it,"content_id","content_type","name") }?.let(all::add) }
            if(rows.length()<100) break
        }
        return all.distinctBy { it.type to it.id }.take(2_000)
    }
    suspend fun logout(session: NuvioSession) { request("POST","${session.server.base}/auth/v1/logout?scope=local",headers(session.server,session.access),JSONObject()) }
}
