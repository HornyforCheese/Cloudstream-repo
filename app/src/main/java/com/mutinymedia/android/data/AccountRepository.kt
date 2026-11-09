package com.mutinymedia.android.data

import com.mutinymedia.android.domain.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI state contains no authentication tokens. */
data class AccountStatus(val email: String? = null, val connected: Boolean = false, val saved: Boolean = false, val checking: Boolean = false, val error: String? = null)
data class AccountImport(val addons: List<String>, val items: List<MediaItem>, val warning: String? = null)

class AccountRepository(private val secrets: SecretsStore, private val stremio: StremioAccountClient = StremioAccountClient(), private val nuvio: NuvioAccountAdapter = NuvioAccountAdapter()) {
    val stremioStatus=MutableStateFlow(AccountStatus())
    val nuvioStatus=MutableStateFlow(AccountStatus())
    val nuvioProfiles=MutableStateFlow<List<NuvioProfile>>(emptyList())
    val nuvioProfile=MutableStateFlow(1)
    private val stremioLock=Mutex()
    private val nuvioLock=Mutex()
    private var stremioSession: StremioSession?=null
    private var nuvioSession: NuvioSession?=null
    private val unlocked=mutableSetOf<Int>()
    private suspend fun <T> guard(state: MutableStateFlow<AccountStatus>, block: suspend () -> T): T {
        state.value=state.value.copy(checking=true,error=null)
        try { return block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.value=state.value.copy(connected=state.value.connected && !(e is AccountException && e.expired),error=e.message ?: "Account operation failed"); throw e }
        finally { state.value=state.value.copy(checking=false) }
    }
    private suspend fun save(session: StremioSession) { currentCoroutineContext().ensureActive();secrets.saveSecret("stremio_session",session.encode());stremioSession=session;stremioStatus.value=AccountStatus(session.email,true,true) }
    private suspend fun save(session: NuvioSession) { currentCoroutineContext().ensureActive();secrets.saveSecret("nuvio_session",session.encode());nuvioSession=session;nuvioProfile.value=session.profile;nuvioStatus.value=AccountStatus(session.email,true,true) }
    suspend fun restoreStremio() = withContext(Dispatchers.IO) { stremioLock.withLock {
        val raw=secrets.readSecret("stremio_session") ?: return@withLock
        stremioStatus.value=AccountStatus(saved=true,checking=true)
        try { guard(stremioStatus) { save(stremio.verify(StremioSession.decode(raw).token)) } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { stremioStatus.value=AccountStatus(saved=true,error="Saved Stremio sign-in could not be verified. Retry or sign in again.") }
    } }
    suspend fun restoreNuvio() = withContext(Dispatchers.IO) { nuvioLock.withLock {
        val raw=secrets.readSecret("nuvio_session") ?: return@withLock
        nuvioStatus.value=AccountStatus(saved=true,checking=true)
        try { guard(nuvioStatus) { save(nuvio.verify(NuvioSession.decode(raw)));loadProfiles() } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { nuvioStatus.value=nuvioStatus.value.copy(saved=true,checking=false,error="Saved Nuvio sign-in could not be fully verified. Retry or sign in again.") }
    } }
    suspend fun loginStremio(email: String,password: String) = withContext(Dispatchers.IO) { stremioLock.withLock { guard(stremioStatus) { save(stremio.login(email,password)) } } }
    suspend fun importStremioKey(key: String) = withContext(Dispatchers.IO) { stremioLock.withLock { guard(stremioStatus) { save(stremio.verify(key)) } } }
    suspend fun loginNuvio(server: String,email: String,password: String) = withContext(Dispatchers.IO) { nuvioLock.withLock { guard(nuvioStatus) {
        save(nuvio.login(server,email,password));unlocked.clear();nuvioProfiles.value=emptyList();loadProfiles()
    } } }
    private suspend fun loadProfiles() {
        val session=nuvioSession ?: error("Sign in to Nuvio first")
        val profiles=nuvio.profiles(session)
        nuvioProfiles.value=profiles
        val selected=profiles.firstOrNull { it.index==session.profile } ?: profiles.firstOrNull()
        if(selected!=null && selected.index!=session.profile) save(session.withProfile(selected.index))
    }
    private suspend fun currentNuvio(): NuvioSession {
        val stored=nuvioSession ?: error("Sign in to Nuvio first")
        val active=nuvio.verify(stored)
        save(active)
        return active
    }
    suspend fun selectNuvioProfile(index: Int,pin: String) = withContext(Dispatchers.IO) { nuvioLock.withLock { guard(nuvioStatus) {
        val session=currentNuvio()
        if(nuvioProfiles.value.isEmpty()) loadProfiles()
        val profile=nuvioProfiles.value.firstOrNull { it.index==index } ?: error("Select an available Nuvio profile")
        if(profile.pinEnabled) {
            require(pin.isNotBlank()) { "Enter this profile's PIN" }
            check(nuvio.unlock(session,index,pin)) { "Nuvio did not unlock that profile. Check the PIN and try again." }
            unlocked.add(index)
        }
        save(session.withProfile(index))
    } } }
    suspend fun pullStremio(): AccountImport = withContext(Dispatchers.IO) { stremioLock.withLock { guard(stremioStatus) {
        val session=stremioSession ?: error("Sign in to Stremio first")
        val urls=stremio.addons(session)
        var warning: String?=null
        val items=try { stremio.library(session) } catch(e: CancellationException) { throw e } catch(_: Exception) { warning="Stremio addons imported, but the library could not be downloaded. Retry import later.";emptyList() }
        AccountImport(urls,items,warning)
    } } }
    suspend fun pullNuvio(): AccountImport = withContext(Dispatchers.IO) { nuvioLock.withLock { guard(nuvioStatus) {
        val session=currentNuvio()
        if(nuvioProfiles.value.isEmpty()) loadProfiles()
        val profile=nuvioProfiles.value.firstOrNull { it.index==session.profile } ?: error("This Nuvio account has no profile to import")
        check(!profile.pinEnabled || unlocked.contains(profile.index)) { "Select and unlock this Nuvio profile before importing" }
        val urls=nuvio.addons(session,profile)
        var warning: String?=null
        val items=try { nuvio.library(session) } catch(e: CancellationException) { throw e } catch(_: Exception) { warning="Nuvio addons imported, but the watchlist could not be downloaded. Retry import later.";emptyList() }
        AccountImport(urls,items,warning)
    } } }
    suspend fun logoutStremio(): Boolean = withContext(Dispatchers.IO) { stremioLock.withLock {
        var remote=true
        try { (stremioSession ?: secrets.readSecret("stremio_session")?.let { StremioSession.decode(it) })?.let { stremio.logout(it) } } catch(e: CancellationException) { throw e } catch(_: Exception) { remote=false }
        finally { currentCoroutineContext().ensureActive();secrets.saveSecret("stremio_session","");stremioSession=null;stremioStatus.value=AccountStatus() }
        remote
    } }
    suspend fun logoutNuvio(): Boolean = withContext(Dispatchers.IO) { nuvioLock.withLock {
        var remote=true
        try { (nuvioSession ?: secrets.readSecret("nuvio_session")?.let { NuvioSession.decode(it) })?.let { nuvio.logout(it) } } catch(e: CancellationException) { throw e } catch(_: Exception) { remote=false }
        finally { currentCoroutineContext().ensureActive();secrets.saveSecret("nuvio_session","");nuvioSession=null;unlocked.clear();nuvioProfiles.value=emptyList();nuvioProfile.value=1;nuvioStatus.value=AccountStatus() }
        remote
    } }
}
