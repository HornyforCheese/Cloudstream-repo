package com.mutinymedia.android.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AccountApiTest {
    private val publicKey = "eyJhbGciOiJIUzI1NiJ9."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"anon\"}".toByteArray())+".fixture"
    private fun discovery(backend: String="https://nuvio.example",key: String=publicKey) = JSONObject().put("version",1).put("service","nuvio")
        .put("backend_url",backend).put("publishable_key",key).put("capabilities",JSONObject().put("email_password_auth",true)).toString()
    private fun auth(access: String="access-fixture",refresh: String="refresh-fixture") = JSONObject().put("access_token",access).put("refresh_token",refresh)
        .put("expires_in",3600).put("user",JSONObject().put("id","user-fixture").put("email","user@example.invalid")).toString()

    @Test fun stremioLoginPreservesPasswordAndSeparatesTokenFromUiIdentity() = runBlocking {
        val client=StremioAccountClient(AccountTransport { method,url,_,body ->
            assertEquals("POST",method);assertEquals("https://api.strem.io/api/login",url)
            val json=JSONObject(body!!);assertEquals("  secret with spaces  ",json.getString("password"));assertEquals("user@example.invalid",json.getString("email"))
            AccountReply(200,"""{"result":{"authKey":"auth-fixture","user":{"_id":"id-fixture","email":"user@example.invalid"}}}""")
        })
        val session=client.login(" user@example.invalid ","  secret with spaces  ")
        assertEquals("auth-fixture",session.token)
        assertFalse(session.toString().contains(session.token))
        assertFalse(session.encode().contains("secret with spaces"))
        assertEquals(session.token,StremioSession.decode(session.encode()).token)
    }
    @Test fun stremioRejectsFailedOrMalformedAuthentication() = runBlocking {
        for(body in listOf("""{"error":{"code":2,"message":"do not expose secret"}}""","{}","""{"result":{"authKey":"secret"}}""")) {
            val client=StremioAccountClient(AccountTransport { _,_,_,_ -> AccountReply(200,body) })
            val failure=runCatching { client.login("user@example.invalid","password-fixture") }.exceptionOrNull()
            assertNotNull(failure);assertFalse(failure!!.message.orEmpty().contains("do not expose secret"))
        }
    }
    @Test fun stremioImportsOnlyActiveHttpsAddonsAndSavedTitles() = runBlocking {
        val client=StremioAccountClient(AccountTransport { _,url,_,body ->
            assertEquals("auth-fixture",JSONObject(body!!).getString("authKey"))
            AccountReply(200,if(url.endsWith("addonCollectionGet")) """{"result":{"addons":[{"transportUrl":"https://addon.example/manifest.json"},{"transportUrl":"https://disabled.example","flags":{"disabled":true}},{"transportUrl":"http://insecure.example"}]}}""" else """{"result":[{"_id":"tt1","type":"movie","name":"Saved"},{"_id":"tt2","type":"series","name":"Removed","removed":true},{"_id":"tt3","type":"movie","name":"Temporary","temp":true}]}""")
        })
        val session=StremioSession("auth-fixture","user-fixture","user@example.invalid")
        assertEquals(listOf("https://addon.example"),client.addons(session))
        assertEquals(listOf("tt1"),client.library(session).map { it.id })
    }
    @Test fun nuvioDiscoversPublishedConfigurationAndSignsInWithoutSavingPassword() = runBlocking {
        val seen=mutableListOf<String>()
        val client=NuvioAccountAdapter(AccountTransport { _,url,headers,body ->
            seen+=url
            if(url.endsWith("/.well-known/nuvio")) AccountReply(200,discovery())
            else {
                assertEquals(publicKey,headers["apikey"])
                assertEquals(" secret ",JSONObject(body!!).getString("password"))
                AccountReply(200,auth())
            }
        })
        val session=client.login("https://nuvio.example/","user@example.invalid"," secret ")
        assertEquals("https://nuvio.example/.well-known/nuvio",seen.first())
        assertEquals("https://nuvio.example/auth/v1/token?grant_type=password",seen.last())
        assertFalse(session.encode().contains(" secret "))
        assertFalse(session.toString().contains("access-fixture"))
        assertEquals("refresh-fixture",NuvioSession.decode(session.encode()).refresh)
    }
    @Test fun nuvioRejectsInsecureOrPrivilegedDiscoveryConfiguration() = runBlocking {
        val privileged="eyJhbGciOiJIUzI1NiJ9."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".toByteArray())+".fixture"
        for(document in listOf(discovery("http://insecure.example"),discovery(key=privileged),"{}")) {
            val client=NuvioAccountAdapter(AccountTransport { _,_,_,_ -> AccountReply(200,document) })
            assertTrue(runCatching { client.discover("https://nuvio.example") }.isFailure)
        }
    }
    @Test fun nuvioRefreshesExpiredSessionAndVerifiesIdentity() = runBlocking {
        val client=NuvioAccountAdapter(AccountTransport { _,url,headers,body ->
            if(url.contains("grant_type=refresh_token")) {
                assertEquals("old-refresh",JSONObject(body!!).getString("refresh_token"));AccountReply(200,auth("new-access","new-refresh"))
            } else {
                assertEquals("Bearer new-access",headers["Authorization"])
                AccountReply(200,"""{"id":"user-fixture","email":"user@example.invalid"}""")
            }
        })
        val active=client.verify(NuvioSession(NuvioServer("https://nuvio.example",publicKey),"expired","old-refresh",0,"user-fixture","user@example.invalid",2))
        assertEquals("new-access",active.access);assertEquals("new-refresh",active.refresh);assertEquals(2,active.profile)
    }
    @Test fun nuvioRespectsProfilePinAndUsesPrimaryAddonSetting() = runBlocking {
        val client=NuvioAccountAdapter(AccountTransport { _,url,_,body ->
            when {
                url.endsWith("verify_profile_pin") -> { assertEquals(2,JSONObject(body!!).getInt("p_profile_id"));AccountReply(200,"""[{"unlocked":false}]""") }
                url.contains("/addons?") -> { assertTrue(url.contains("profile_id=eq.1"));AccountReply(200,"""[{"url":"https://addon.example/manifest.json","enabled":true},{"url":"https://disabled.example","enabled":false}]""") }
                else -> error("Unexpected request")
            }
        })
        val session=NuvioSession(NuvioServer("https://nuvio.example",publicKey),"access","refresh",Long.MAX_VALUE,"user-fixture","user@example.invalid",2)
        assertFalse(client.unlock(session,2,"1234"))
        assertEquals(listOf("https://addon.example"),client.addons(session,NuvioProfile(2,"Second",true,true)))
    }
    @Test fun nuvioRejectsIdentityMismatchAndAuthFailures() = runBlocking {
        val saved=NuvioSession(NuvioServer("https://nuvio.example",publicKey),"access","refresh",Long.MAX_VALUE,"user-fixture","user@example.invalid")
        val mismatch=NuvioAccountAdapter(AccountTransport { _,_,_,_ -> AccountReply(200,"""{"id":"different-user"}""") })
        assertTrue(runCatching { mismatch.verify(saved) }.isFailure)
        val failed=NuvioAccountAdapter(AccountTransport { _,_,_,_ -> AccountReply(401,"{\"error\":\"private server details\"}") })
        val error=runCatching { failed.verify(saved) }.exceptionOrNull()
        assertNotNull(error);assertFalse(error!!.message.orEmpty().contains("private server details"))
    }
}
