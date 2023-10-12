/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.example.makeitso.model.service.impl

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.makeitso.model.User
import com.example.makeitso.model.service.AccountService
import com.example.makeitso.model.service.trace
import com.example.makeitso.screens.settings.EditUiState
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
import com.google.firebase.auth.ktx.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.log

class AccountServiceImpl @Inject constructor(private val auth: FirebaseAuth) : AccountService {

  override val currentUserId: String
    get() = auth.currentUser?.uid.orEmpty()

  override val hasUser: Boolean
    get() = auth.currentUser != null

  override val currentUser: Flow<User>
    get() = callbackFlow {
      val listener =
        FirebaseAuth.AuthStateListener { auth ->
          this.trySend(auth.currentUser?.let {
            if (it.isAnonymous) User(id = it.uid, isAnonymous = it.isAnonymous)
            else User(id = it.uid,
              isAnonymous = it.isAnonymous,
              username = it.providerData[1].displayName ?: "",
              email = it.providerData[1].email ?: "",
              picUrl = it.providerData[1].photoUrl,
              providerInfo = it.providerData[1].providerId)
          } ?: User())
        }
      auth.addAuthStateListener(listener)
      awaitClose { auth.removeAuthStateListener(listener) }
    }

  override suspend fun authenticate(email: String, password: String):Unit =
    trace(AUTHENTICATE_TRACE) {
      auth.signInWithEmailAndPassword(email, password).await()
  }

  override suspend fun sendRecoveryEmail(email: String) {
    auth.sendPasswordResetEmail(email).await()
  }

  override suspend fun createAnonymousAccount() {
    auth.signInAnonymously().await()
  }

  override suspend fun linkAccount(email: String, password: String):Unit = trace(LINK_ACCOUNT_TRACE)
  {
    //TODO
    val credential = EmailAuthProvider.getCredential(email, password)
    auth.currentUser!!.linkWithCredential(credential).await()
//    auth.currentUser!!.delete().await()
//    auth.createUserWithEmailAndPassword(email, password)

  }

  override suspend fun deleteAccount() {
    auth.currentUser!!.delete().await()
  }

  override suspend fun signOut() {
    if (auth.currentUser!!.isAnonymous) {
      auth.currentUser!!.delete()
    }
    auth.signOut()

    // Sign the user back in anonymously.
    createAnonymousAccount()
  }

  override suspend fun signInGoogle(intent: Intent,oneTapClient: SignInClient):Boolean {
    Log.d("test-signInGoogle1", "asdad")
    try{
      val credential = oneTapClient.getSignInCredentialFromIntent(intent)
      Log.d("test-signInGoogle2", "asdad")
      val googleCredentials = GoogleAuthProvider.getCredential(credential.googleIdToken, null)
      Log.d("test-signInGoogle3", "asdad")
      try {
        auth.signInWithCredential(googleCredentials).await()
      } catch(e: Exception) {
        e.printStackTrace()
        Log.d("fsf", "$e")
        if(e is CancellationException) throw e
      }
      return true
    } catch (_:Exception) {}
    return false
  }

  override suspend fun changeProfile(newInfo: EditUiState) {
    Log.d("test","${newInfo.username} ${newInfo.email} ${newInfo.picUrl}")
    val profileUpdates = userProfileChangeRequest {
      displayName = newInfo.username
      photoUri = Uri.parse(newInfo.picUrl)
      Log.d("test1","$displayName $photoUri")
    }
    auth.currentUser!!.updateProfile(profileUpdates)
    auth.currentUser!!.updateEmail(newInfo.email)
  }

  override suspend fun getUserInfo() {
    Log.d("tttestst", "$auth")
    auth.currentUser?.let{
//      for (profile in it.providerData) {
      val profile: UserInfo = if (it.providerData.size > 1) it.providerData[1]
      else it.providerData[0]
      // Id of the provider (ex: google.com)
      val providerId = profile.providerId

      // UID specific to the provider
      val uid = profile.uid

      // Name, email address, and profile photo Url
      val name = profile.displayName
      val email = profile.email
      val photoUrl = profile.photoUrl
      Log.d("Info", "provider: ${providerId}\n name: ${name}\n email: " +
              "$email\n photoUrl: $photoUrl\n userId: $uid")
//      }
    }
  }

  companion object {
    private const val LINK_ACCOUNT_TRACE = "linkAccount"
    private const val AUTHENTICATE_TRACE = "authenticate"

  }
}
