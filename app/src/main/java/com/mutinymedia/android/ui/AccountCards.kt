package com.mutinymedia.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutinymedia.android.MutinyViewModel
import com.mutinymedia.android.data.AccountStatus

@Composable
private fun AccountState(state: AccountStatus) {
    Text(when {
        state.checking -> "Checking account…"
        state.connected -> "Signed in as ${state.email}"
        state.saved -> "Saved sign-in needs verification"
        else -> "Not signed in"
    }, color=if(state.connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
}

@Composable
fun StremioAccountCard(vm: MutinyViewModel) {
    val state by vm.accounts.stremioStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var useToken by remember { mutableStateOf(false) }
    val enabled=!busy && !state.checking
    MutinyPanel("Stremio account") {
        AccountState(state)
        if (!state.connected) {
            Text("Sign in to import your installed addons and saved library. Your password is not saved.",style=MaterialTheme.typography.bodySmall)
            if(!useToken) {
                OutlinedTextField(email,{email=it},label={Text("Stremio email")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                OutlinedTextField(password,{password=it},label={Text("Stremio password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                Button(onClick={val secret=password;password="";vm.loginStremio(email,secret)},enabled=enabled && email.isNotBlank() && password.isNotEmpty()) { Text("Sign in to Stremio") }
            } else {
                Text("For a social-login account, import your Stremio auth key. It is verified with Stremio and stored encrypted.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(token,{token=it},label={Text("Stremio auth key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                Button(onClick={val key=token;token="";vm.connectStremioKey(key)},enabled=enabled && token.isNotBlank()) { Text("Connect Stremio auth key") }
            }
            TextButton(onClick={useToken=!useToken;password="";token=""},enabled=enabled) { Text(if(useToken) "Use email and password" else "Use an auth key instead") }
        } else {
            Button(onClick={vm.syncStremio()},enabled=enabled) { Text("Import Stremio addons & library") }
            Text("Imports merge with your local library. They do not upload, replace or delete your cloud collection.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.saved && !state.connected) OutlinedButton(onClick={vm.verifyStremio()},enabled=enabled) { Text("Verify saved Stremio sign-in") }
        if(state.saved || state.connected) OutlinedButton(onClick={vm.logoutStremio()},enabled=enabled) { Text("Sign out of Stremio") }
    }
}

@Composable
fun NuvioAccountCard(vm: MutinyViewModel) {
    val state by vm.accounts.nuvioStatus.collectAsStateWithLifecycle()
    val profiles by vm.accounts.nuvioProfiles.collectAsStateWithLifecycle()
    val selected by vm.accounts.nuvioProfile.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var server by remember { mutableStateOf("https://api.nuvio.tv") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pending by remember(selected) { mutableIntStateOf(selected) }
    var pin by remember { mutableStateOf("") }
    val enabled=!busy && !state.checking
    MutinyPanel("Nuvio • built-in account adapter") {
        AccountState(state)
        if(!state.connected) {
            Text("Connect your Nuvio account to import profile addons and watchlist. The official server is prefilled; self-hosted servers must publish Nuvio discovery information.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(server,{server=it},label={Text("Nuvio server")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            OutlinedTextField(email,{email=it},label={Text("Nuvio email")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            OutlinedTextField(password,{password=it},label={Text("Nuvio password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            Button(onClick={val secret=password;password="";vm.loginNuvio(server,email,secret)},enabled=enabled && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty()) { Text("Sign in to Nuvio") }
        } else {
            if(profiles.isNotEmpty()) {
                Text("Choose a Nuvio profile")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile -> FilterChip(selected=pending==profile.index,onClick={pending=profile.index;pin=""},enabled=enabled,label={Text(profile.name+if(profile.pinEnabled) " • PIN" else "")}) }
                }
                val profile=profiles.firstOrNull { it.index==pending }
                if(profile?.pinEnabled==true) OutlinedTextField(pin,{pin=it},label={Text("Profile PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                Button(onClick={val value=pin;pin="";vm.selectNuvioProfile(pending,value)},enabled=enabled && profile!=null) { Text("Select / unlock profile") }
                Text("Active profile: ${profiles.firstOrNull { it.index==selected }?.name ?: selected}",style=MaterialTheme.typography.bodySmall)
            }
            Button(onClick={vm.syncNuvio()},enabled=enabled) { Text("Import Nuvio addons & watchlist") }
            Text("The adapter is included in this app. Imported HTTPS addons feed Browse and playback. Account import does not run Nuvio's executable JS plugins or overwrite cloud data.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.saved) OutlinedButton(onClick={vm.verifyNuvio()},enabled=enabled) { Text("Refresh Nuvio account / profiles") }
        if(state.saved || state.connected) OutlinedButton(onClick={vm.logoutNuvio()},enabled=enabled) { Text("Sign out of Nuvio") }
    }
}
