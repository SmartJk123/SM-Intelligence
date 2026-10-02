package com.example.smartmoney

import com.example.smartmoney.data.remote.dto.BankAccountDto
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.ui.accounts.AccountViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BankAccountIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun bankAccount_domain_model_formats_masked_account_number_properly() {
        val account1 = BankAccount(
            id = "b-1",
            bankName = "NCBA",
            accountNumber = "1234567890",
            cardType = "Debit"
        )
        assertEquals("**** 7890", account1.maskedAccountNumber)

        val account2 = BankAccount(
            id = "b-2",
            bankName = "Equity",
            accountNumber = "1234",
            cardType = "Credit"
        )
        assertEquals("**** 1234", account2.maskedAccountNumber)

        val account3 = BankAccount(
            id = "b-3",
            bankName = "KCB",
            accountNumber = "**** 9999",
            cardType = "Debit"
        )
        assertEquals("**** 9999", account3.maskedAccountNumber)
    }

    @Test
    fun bankAccountDto_serializes_and_maps_to_domain() {
        val rawJson = """
            {
                "id": "a9e6b5f4-1234-5678-9abc-def012345678",
                "user_id": "b1e2c3d4-5678-90ab-cdef-1234567890ab",
                "bank_name": "Stanbic",
                "account_number": "9876543210",
                "card_type": "Credit"
            }
        """.trimIndent()

        val dto = json.decodeFromString<BankAccountDto>(rawJson)
        assertEquals("a9e6b5f4-1234-5678-9abc-def012345678", dto.id)
        assertEquals("Stanbic", dto.bankName)
        assertEquals("9876543210", dto.accountNumber)
        assertEquals("Credit", dto.cardType)

        val domain = dto.toDomain()
        assertEquals(dto.id, domain.id)
        assertEquals(dto.bankName, domain.bankName)
        assertEquals(dto.accountNumber, domain.accountNumber)
        assertEquals(dto.cardType, domain.cardType)
        assertEquals("**** 3210", domain.maskedAccountNumber)

        // Test round trip
        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<BankAccountDto>(encoded)
        assertEquals(dto, decoded)
    }

    @Test
    fun fakeRepository_add_and_getBankAccounts() = runBlocking {
        val fakeRepo = FakeBankAccountRepository()

        val initialAccounts = fakeRepo.getCurrentList()
        assertEquals(1, initialAccounts.size)
        assertEquals("NCBA", initialAccounts[0].bankName)

        val result = fakeRepo.addBankAccount(
            bankName = "Equity",
            accountNumber = "0112345678",
            cardType = "Debit"
        )

        assertTrue(result.isSuccess)
        val updated = fakeRepo.getCurrentList()
        assertEquals(2, updated.size)
        assertEquals("Equity", updated[1].bankName)
    }

    private class FakeBankAccountRepository(
        private val shouldFailAdd: Boolean = false
    ) : BankAccountRepository {
        private val list = mutableListOf(
            BankAccount(
                id = UUID.randomUUID().toString(),
                bankName = "NCBA",
                accountNumber = "1234567890",
                cardType = "Debit"
            )
        )
        private val flow = MutableStateFlow<List<BankAccount>>(list.toList())

        fun getCurrentList(): List<BankAccount> = list.toList()

        override suspend fun addBankAccount(
            bankName: String,
            accountNumber: String,
            cardType: String
        ): Result<Unit> {
            return if (shouldFailAdd) {
                Result.failure(RuntimeException("Network connection timeout"))
            } else {
                val newAccount = BankAccount(
                    id = UUID.randomUUID().toString(),
                    bankName = bankName,
                    accountNumber = accountNumber,
                    cardType = cardType
                )
                list.add(newAccount)
                flow.value = list.toList()
                Result.success(Unit)
            }
        }

        override suspend fun removeBankAccount(id: String): Result<Unit> {
            list.removeAll { it.id == id || it.accountNumber == id }
            flow.value = list.toList()
            return Result.success(Unit)
        }

        override fun getBankAccounts(): Flow<List<BankAccount>> = flow.asStateFlow()
    }

    @Test
    fun fakeRepository_removeBankAccount() = runBlocking {
        val fakeRepo = FakeBankAccountRepository()
        val initial = fakeRepo.getCurrentList()
        assertEquals(1, initial.size)

        val accountId = initial[0].id
        val removeResult = fakeRepo.removeBankAccount(accountId)
        assertTrue(removeResult.isSuccess)
        assertEquals(0, fakeRepo.getCurrentList().size)
    }
}
