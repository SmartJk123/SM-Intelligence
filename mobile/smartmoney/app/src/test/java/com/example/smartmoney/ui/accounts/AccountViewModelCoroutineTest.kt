package com.example.smartmoney.ui.accounts

import com.example.smartmoney.core.coroutine.TestDispatcherProvider
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test demonstrating deterministic coroutine testing via [TestDispatcherProvider].
 */
class AccountViewModelCoroutineTest {

    // Simple fakes for the test
    class FakeAccountRepository : AccountRepository {
        var syncCalled = false
        override fun getAccountsFlow(userId: String): Flow<List<Account>> = MutableStateFlow(emptyList())
        override suspend fun syncAccounts(userId: String): Result<Unit> {
            syncCalled = true
            return Result.success(Unit)
        }
        override suspend fun createAccount(account: Account): Result<Account> = Result.success(account)
        override suspend fun deleteAccount(id: String): Result<Unit> = Result.success(Unit)
    }

    class FakeBankAccountRepository : BankAccountRepository {
        var addBankAccountCalled = false
        override fun getBankAccounts(): Flow<List<BankAccount>> = MutableStateFlow(emptyList())
        override suspend fun addBankAccount(bankName: String, accountNumber: String, cardType: String): Result<Unit> {
            addBankAccountCalled = true
            return Result.success(Unit)
        }
        override suspend fun removeBankAccount(id: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun testRefreshAccounts_executesDeterministically_withTestDispatchers() = runTest {
        // 1. Arrange: Use our TestDispatcherProvider which forces Unconfined/immediate execution
        val testDispatchers = TestDispatcherProvider()
        val fakeRepo = FakeAccountRepository()
        val fakeBankRepo = FakeBankAccountRepository()

        // 2. Act: Instantiate view model. Init block calls refreshAccounts() immediately.
        val viewModel = AccountViewModel(
            repository = fakeRepo,
            bankAccountRepository = fakeBankRepo,
            userId = "test-user-123",
            dispatchers = testDispatchers
        )

        // 3. Assert: Verify the view model completed its coroutine work synchronously
        assertTrue("Expected syncAccounts to be called on AccountRepository", fakeRepo.syncCalled)
        assertFalse("ViewModel should finish loading immediately due to Unconfined dispatchers", viewModel.isLoading.value)
    }
}
