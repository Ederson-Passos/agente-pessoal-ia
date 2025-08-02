package com.assistenteia.agentepessoal

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assistenteia.agentepessoal.managers.AudioPlayerManager
import com.assistenteia.agentepessoal.ui.theme.AgentePessoalTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    var uiState by mutableStateOf<UiState>(UiState.SignedOut)
        private set

    fun startEmailFlow(account: GoogleSignInAccount, activity: ComponentActivity) {
        viewModelScope.launch {
            uiState = UiState.Loading("Acessando os e-mails...")
            try {
                val emails = getEmailsFromGmail(account, activity)

                uiState = UiState.Loading("Analisando os e-mails...")
                val audioResponse = RetrofitClient
                    .aiApiService
                    .getSummaryAudio(EmailRequest(emails = emails))

                uiState = UiState.Loading("Preparando o áudio...")
                AudioPlayerManager.play(audioResponse.audioUrl) {
                    uiState = UiState.Finished
                }
                uiState = UiState.Playing(summaryText = audioResponse.summaryText)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Flow failed", e)
                uiState = UiState.Error("Falha no fluxo: ${e.message}")
            }
        }
    }

    private suspend fun getEmailsFromGmail(
        account: GoogleSignInAccount,
        activity: ComponentActivity): List<EmailData> = withContext(Dispatchers.IO
        ) {
        val credential = GoogleAccountCredential.usingOAuth2(
            activity, listOf(GmailScopes.GMAIL_READONLY)
        )
        credential.selectedAccount = account.account

        val gmailService = Gmail.Builder(
            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
        )
            .setApplicationName("Agente Pessoal IA")
            .build()

        val messages = gmailService.users().messages().list("me")
            .setMaxResults(5).setQ("is:unread").execute().messages ?: emptyList()

        val emailList = mutableListOf<EmailData>()
        for (messageSummary in messages) {
            val message = gmailService.users().messages().get(
                "me",
                messageSummary.id
            ).setFormat("metadata").setMetadataHeaders(listOf("Subject", "From")).execute()
            val subject = message.payload.headers.find { it.name == "Subject" }?.value
            val from = message.payload.headers.find { it.name == "From" }?.value
            emailList.add(EmailData(from = from, subject = subject, snippet = message.snippet))
        }
        return@withContext emailList
    }

    fun updateState(newState: UiState) {
        uiState = newState
    }
}

sealed class UiState {
    object SignedOut : UiState()
    object Idle : UiState()
    data class Loading(val message: String) : UiState()
    data class Playing(val summaryText: String) : UiState()
    object Finished : UiState()
    data class Error(val message: String) : UiState()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.startEmailFlow(account, this)
        } catch (e: ApiException) {
            viewModel.updateState(UiState.Error("Falha no login com a Google: ${e.statusCode}"))
            Log.w("MainActivity", "Google sign in failed", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentePessoalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState = viewModel.uiState
                    MainScreen(uiState = uiState, onButtonClick = { checkSignInAndStartFlow() })
                }
            }
        }
    }

    private fun checkSignInAndStartFlow() {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)

        if (lastSignedInAccount != null) {
            Log.d("MainActivity", "Usuário já autenticado. Iniciando o fluxo.")
            viewModel.startEmailFlow(lastSignedInAccount, this)
        } else {
            Log.d("MainActivity", "Nenhum usuário autenticado. Iniciando o ecrã de login.")
            signIn()
        }
    }

    private fun signIn() {
        viewModel.updateState(UiState.Loading("Aguardando login..."))
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .build()
        val signInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(signInClient.signInIntent)
    }
}

@Composable
fun MainScreen(uiState: UiState, onButtonClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (uiState) {
            is UiState.SignedOut, is UiState.Idle, is UiState.Finished ->
                Button(onClick = onButtonClick) { Text("Analisar meus e-mails") }
            is UiState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.message)
            }
            is UiState.Playing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Reproduzindo o resumo...")
            }
            is UiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onButtonClick) { Text("Tentar novamente") }
            }
        }
    }
}