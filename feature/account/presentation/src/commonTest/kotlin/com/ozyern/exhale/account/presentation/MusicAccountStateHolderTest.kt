package com.ozyern.exhale.account.presentation

import com.ozyern.exhale.account.MusicAccount
import com.ozyern.exhale.account.MusicAccountError
import com.ozyern.exhale.account.MusicAccountRepository
import com.ozyern.exhale.account.MusicAccountResult
import com.ozyern.exhale.account.MusicAccountState
import com.ozyern.exhale.account.MusicSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MusicAccountStateHolderTest {
    @Test
    fun restorePublishesRepositoryAccountAndCompletesLoading() = runTest {
        val account = MusicAccount(dataSyncId = "channel-1", name = "Exhale")
        val repository = FakeMusicAccountRepository().apply {
            mutableState.value = MusicAccountState.SignedIn(account)
        }
        val holder = MusicAccountStateHolder(repository, StandardTestDispatcher(testScheduler))

        advanceUntilIdle()

        assertEquals(MusicAccountState.SignedIn(account), holder.state.value.account)
        assertFalse(holder.state.value.isRestoring)
        assertEquals(1, repository.restoreCalls)
        holder.close()
    }

    @Test
    fun signOutFailureKeepsAccountAndExposesRecoverableMessage() = runTest {
        val account = MusicAccount(dataSyncId = "channel-1", name = "Exhale")
        val repository = FakeMusicAccountRepository().apply {
            mutableState.value = MusicAccountState.SignedIn(account)
            signOutResult = MusicAccountResult.Failure(MusicAccountError.StorageUnavailable)
        }
        val holder = MusicAccountStateHolder(repository, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        holder.signOut()
        advanceUntilIdle()

        assertEquals(MusicAccountState.SignedIn(account), holder.state.value.account)
        assertEquals("Secure account storage is unavailable on this device.", holder.state.value.message)
        assertFalse(holder.state.value.isWorking)
        holder.close()
    }
}

private class FakeMusicAccountRepository : MusicAccountRepository {
    val mutableState = MutableStateFlow<MusicAccountState>(MusicAccountState.SignedOut)
    override val state: StateFlow<MusicAccountState> = mutableState
    var restoreCalls: Int = 0
    var signOutResult: MusicAccountResult<Unit> = MusicAccountResult.Success(Unit)

    override suspend fun restore(): MusicAccountResult<Unit> {
        restoreCalls += 1
        return MusicAccountResult.Success(Unit)
    }

    override suspend fun completeWebSignIn(
        cookie: String,
        visitorData: String,
        pageDataSyncId: String?,
    ): MusicAccountResult<MusicSignInResult> =
        MusicAccountResult.Failure(MusicAccountError.Unknown)

    override suspend fun selectChannel(dataSyncId: String): MusicAccountResult<MusicAccount> =
        MusicAccountResult.Failure(MusicAccountError.Unknown)

    override suspend fun cancelSignIn() = Unit

    override suspend fun signOut(): MusicAccountResult<Unit> = signOutResult
}
