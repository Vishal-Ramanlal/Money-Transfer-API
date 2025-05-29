package com.jpmorgan.demo.service;

import com.jpmorgan.demo.dto.TransferRequest;
import com.jpmorgan.demo.exception.AccountNotFoundException;
import com.jpmorgan.demo.exception.InsufficientFundsException;
import com.jpmorgan.demo.exception.InvalidCurrencyException;
import com.jpmorgan.demo.model.Account;
import com.jpmorgan.demo.model.Currency;
import com.jpmorgan.demo.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountServiceImpl accountServiceImpl;

    private Account aliceAccount; // USD
    private Account bobAccount;   // JPN

    @BeforeEach
    void setUp() {
        aliceAccount = new Account(1L, "Alice", new BigDecimal("1000.00"), Currency.USD, 0L);
        bobAccount = new Account(2L, "Bob", new BigDecimal("50000"), Currency.JPN, 0L); // Bob with 50000 JPN
    }

    @Test
    void transferMoney_success_USDToBob_JPN() {
        // Alice (USD) transfers 50 USD to Bob (JPN)
        // Bob's currency is already JPN from setUp

        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency(Currency.USD);

        String result = accountServiceImpl.transferMoney(request);
        assertNotNull(result);
        assertTrue(result.startsWith("Transfer successful"));

        // Alice: 1000 USD - 50 USD (amount) - 0.50 USD (1% fee) = 949.50 USD
        assertEquals(0, new BigDecimal("949.50").compareTo(aliceAccount.getBalance()));

        // Bob: 50000 JPN + (50 USD * 110 JPN/USD) = 50000 JPN + 5500 JPN = 55500 JPN
        assertEquals(0, new BigDecimal("55500").compareTo(bobAccount.getBalance()));

        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    void transferMoney_success_AUDToAlice_USD() {
        // Setup: Bob has an AUD account, Alice has USD
        bobAccount.setCurrency(Currency.AUD);
        bobAccount.setBalance(new BigDecimal("200.00")); // Bob with 200 AUD
        aliceAccount.setBalance(new BigDecimal("100.00")); // Alice with 100 USD

        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(2L); // From Bob (AUD)
        request.setToAccountId(1L);   // To Alice (USD)
        request.setAmount(new BigDecimal("100.00")); // Transfer 100 AUD
        request.setCurrency(Currency.AUD);

        String result = accountServiceImpl.transferMoney(request);
        assertNotNull(result);

        // Bob (AUD): 200 AUD - 100 AUD (amount) - 1 AUD (1% fee) = 99.00 AUD
        assertEquals(0, new BigDecimal("99.00").compareTo(bobAccount.getBalance()));

        // Alice (USD): 100 USD + (100 AUD * 0.50 USD/AUD) = 100 USD + 50 USD = 150.00 USD
        assertEquals(0, new BigDecimal("150.00").compareTo(aliceAccount.getBalance()));
        
        verify(accountRepository, times(2)).save(any(Account.class));
    }


    @Test
    void transferMoney_AUDToAlice_USD_recurring_20_times() throws InterruptedException {
        aliceAccount.setCurrency(Currency.USD);
        bobAccount.setCurrency(Currency.AUD);
        // Bob needs enough AUD for 20 transfers of 50 AUD + 0.5 AUD fee = 50.5 AUD * 20 = 1010 AUD
        bobAccount.setBalance(new BigDecimal("1010.00")); 
        aliceAccount.setBalance(new BigDecimal("100.00")); 

        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));

        doAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            synchronized (this) {
                if (acc.getId().equals(aliceAccount.getId())) {
                    aliceAccount.setBalance(acc.getBalance());
                    aliceAccount.setVersion(aliceAccount.getVersion() + 1);
                }
                if (acc.getId().equals(bobAccount.getId())) {
                    bobAccount.setBalance(acc.getBalance());
                    bobAccount.setVersion(bobAccount.getVersion() + 1);
                }
            }
            return null;
        }).when(accountRepository).save(any(Account.class));

        BigDecimal expectedAliceBalance = aliceAccount.getBalance();
        BigDecimal expectedBobBalance = bobAccount.getBalance();

        for (int i = 0; i < 20; i++) {
            TransferRequest request = new TransferRequest();
            request.setFromAccountId(2L); 
            request.setToAccountId(1L); 
            request.setAmount(new BigDecimal("50.00")); 
            request.setCurrency(Currency.AUD);
            String result = accountServiceImpl.transferMoney(request); 
            assertNotNull(result);

            BigDecimal fee = new BigDecimal("50.00").multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amountDeductedFromBob = new BigDecimal("50.00").add(fee); 
            // 50 AUD * 0.50 USD/AUD = 25 USD
            BigDecimal amountCreditedToAliceInUSD = new BigDecimal("50.00").multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP); 

            expectedBobBalance = expectedBobBalance.subtract(amountDeductedFromBob);
            expectedAliceBalance = expectedAliceBalance.add(amountCreditedToAliceInUSD);
        }

        assertEquals(0, expectedAliceBalance.compareTo(aliceAccount.getBalance()), "Alice's balance mismatch. Expected: "+expectedAliceBalance+" Actual: "+aliceAccount.getBalance());
        assertEquals(0, expectedBobBalance.compareTo(bobAccount.getBalance()), "Bob's balance mismatch. Expected: "+expectedBobBalance+" Actual: "+bobAccount.getBalance());
        verify(accountRepository, times(40)).save(any(Account.class)); 
    }

    @Test
    void concurrentTransfers_BobToAlice_AUD_AliceToBob_USD() throws InterruptedException {
        // Setup: Alice (USD), Bob (AUD)
        aliceAccount = new Account(1L, "Alice", new BigDecimal("1000.00"), Currency.USD, 0L);
        bobAccount = new Account(2L, "Bob", new BigDecimal("1000.00"), Currency.AUD, 0L);

        TransferRequest bobToAliceRequest = new TransferRequest(); // 20 AUD from Bob to Alice
        bobToAliceRequest.setFromAccountId(2L);
        bobToAliceRequest.setToAccountId(1L);
        bobToAliceRequest.setAmount(new BigDecimal("20.00"));
        bobToAliceRequest.setCurrency(Currency.AUD);

        TransferRequest aliceToBobRequest = new TransferRequest(); // 40 USD from Alice to Bob
        aliceToBobRequest.setFromAccountId(1L);
        aliceToBobRequest.setToAccountId(2L);
        aliceToBobRequest.setAmount(new BigDecimal("40.00"));
        aliceToBobRequest.setCurrency(Currency.USD);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        doAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            synchronized (this) {
                if (savedAccount.getId().equals(1L)) {
                    aliceAccount.setBalance(savedAccount.getBalance());
                    aliceAccount.setVersion(aliceAccount.getVersion() + 1);
                } else if (savedAccount.getId().equals(2L)) {
                    bobAccount.setBalance(savedAccount.getBalance());
                    bobAccount.setVersion(bobAccount.getVersion() + 1);
                }
            }
            return savedAccount;
        }).when(accountRepository).save(any(Account.class));

        Runnable task1 = () -> { // Bob (AUD) to Alice (USD)
            try {
                Account currentAlice, currentBob;
                synchronized (this) { 
                    currentAlice = new Account(aliceAccount.getId(), aliceAccount.getName(), aliceAccount.getBalance(), aliceAccount.getCurrency(), aliceAccount.getVersion());
                    currentBob = new Account(bobAccount.getId(), bobAccount.getName(), bobAccount.getBalance(), bobAccount.getCurrency(), bobAccount.getVersion());
                }
                lenient().when(accountRepository.findById(2L)).thenReturn(Optional.of(currentBob));   // From Bob
                lenient().when(accountRepository.findById(1L)).thenReturn(Optional.of(currentAlice)); // To Alice
                String result = accountServiceImpl.transferMoney(bobToAliceRequest); 
                System.out.println("Task 1 (Bob to Alice) result: " + result);
            } catch (ObjectOptimisticLockingFailureException e) {
                System.out.println("Optimistic lock caught for Bob to Alice transfer: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Exception in task1 (Bob to Alice): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        Runnable task2 = () -> { // Alice (USD) to Bob (AUD)
            try {
                Account currentAlice, currentBob;
                synchronized (this) { 
                    currentAlice = new Account(aliceAccount.getId(), aliceAccount.getName(), aliceAccount.getBalance(), aliceAccount.getCurrency(), aliceAccount.getVersion());
                    currentBob = new Account(bobAccount.getId(), bobAccount.getName(), bobAccount.getBalance(), bobAccount.getCurrency(), bobAccount.getVersion());
                }
                lenient().when(accountRepository.findById(1L)).thenReturn(Optional.of(currentAlice)); // From Alice
                lenient().when(accountRepository.findById(2L)).thenReturn(Optional.of(currentBob));   // To Bob
                String result = accountServiceImpl.transferMoney(aliceToBobRequest); 
                System.out.println("Task 2 (Alice to Bob) result: " + result);
            } catch (ObjectOptimisticLockingFailureException e) {
                System.out.println("Optimistic lock caught for Alice to Bob transfer: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Exception in task2 (Alice to Bob): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        executorService.submit(task1);
        executorService.submit(task2);

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        System.out.println("Final Alice Balance (concurrent test): " + aliceAccount.getBalance() + " " + aliceAccount.getCurrency());
        System.out.println("Final Bob Balance (concurrent test): " + bobAccount.getBalance() + " " + bobAccount.getCurrency());

        assertTrue(aliceAccount.getBalance().compareTo(BigDecimal.ZERO) > 0, "Alice's balance should be positive.");
        assertTrue(bobAccount.getBalance().compareTo(BigDecimal.ZERO) > 0, "Bob's balance should be positive.");
        verify(accountRepository, atLeast(2)).save(any(Account.class));
    }

    @Test
    void transferMoney_fail_AliceUSDToBob_CNY() {
        // Alice (USD) account trying to transfer CNY to Bob
        aliceAccount.setBalance(new BigDecimal("100.00")); // Reset Alice's balance
        bobAccount.setCurrency(Currency.CNY); // Set Bob's currency to CNY

        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount)); // Alice USD
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));   // Bob CNY

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L); // From Alice (USD)
        request.setToAccountId(2L);   // To Bob (CNY)
        request.setAmount(new BigDecimal("40.00")); // 40 CNY
        request.setCurrency(Currency.CNY); // Attempting to send CNY from USD account

        Exception exception = assertThrows(InvalidCurrencyException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
        assertEquals("Transfer currency must match the sender's account base currency.", exception.getMessage());
        
        // Verify balances remain unchanged
        assertEquals(0, new BigDecimal("100.00").compareTo(aliceAccount.getBalance()));
        assertEquals(0, new BigDecimal("50000").compareTo(bobAccount.getBalance()));
        
        verify(accountRepository, never()).save(any(Account.class));
    }
    

    @Test
    void transferMoney_fail_fromAccountCurrencyMismatch() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount)); 
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount)); 

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L); // Alice is USD
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("40"));
        request.setCurrency(Currency.AUD); // Attempting to send AUD from USD account

        Exception exception = assertThrows(InvalidCurrencyException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
        assertEquals("Transfer currency must match the sender's account base currency.", exception.getMessage());
    }


    @Test
    void transferMoney_insufficientFunds() {
        aliceAccount.setBalance(new BigDecimal("30.00")); // Alice has 30 USD
        bobAccount.setCurrency(Currency.AUD); // Bob AUD, for conversion path
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("40.00")); 
        request.setCurrency(Currency.USD);

        Exception exception = assertThrows(InsufficientFundsException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
        assertEquals("Insufficient funds in sender's account.", exception.getMessage());
    }

    @Test
    void transferMoney_fromAccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency(Currency.USD);

        Exception exception = assertThrows(AccountNotFoundException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
        assertEquals("From account not found", exception.getMessage());
    }

    @Test
    void transferMoney_toAccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency(Currency.USD);

        Exception exception = assertThrows(AccountNotFoundException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
        assertEquals("To account not found", exception.getMessage());
    }
    
    // Removed transferMoney_invalidTargetCurrencyAfterConversion as new convertAmount handles this directly.

     @Test
    void transferMoney_OptimisticLockException() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(aliceAccount)); 
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bobAccount));
        // Ensure target can receive for this test, e.g. bobAccount is USD or AUD
        bobAccount.setCurrency(Currency.USD); 

        doThrow(new ObjectOptimisticLockingFailureException("Simulated lock error", null))
            .when(accountRepository).save(any(Account.class));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency(Currency.USD);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            accountServiceImpl.transferMoney(request);
        });
    }
} 