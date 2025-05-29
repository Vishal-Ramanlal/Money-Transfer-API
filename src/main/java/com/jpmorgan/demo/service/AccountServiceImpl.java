package com.jpmorgan.demo.service;

import com.jpmorgan.demo.dto.TransferRequest;
import com.jpmorgan.demo.exception.AccountNotFoundException;
import com.jpmorgan.demo.exception.InsufficientFundsException;
import com.jpmorgan.demo.exception.InvalidCurrencyException;
import com.jpmorgan.demo.model.Account;
import com.jpmorgan.demo.model.Currency;
import com.jpmorgan.demo.repository.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    // USD_TO_AUD_RATE defines how many USD make 1 AUD. Given 0.5 USD = 1 AUD.
    private static final BigDecimal USD_PER_AUD = new BigDecimal("0.50"); // 0.50 USD for 1 AUD
    private static final BigDecimal AUD_PER_USD = new BigDecimal("2.00");    // 1 USD = 2 AUD
    private static final BigDecimal JPN_PER_USD = new BigDecimal("110.00"); // 1 USD = 110 JPN (Assumed)
    private static final BigDecimal CNY_PER_USD = new BigDecimal("7.00");    // 1 USD = 7 CNY (Assumed)

    // Required for intermediate calculations. Using BigDecimal for precision.
    private static final int DEFAULT_SCALE = 10; // Scale for intermediate calculations
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP; // Rounding mode for intermediate calculations

    private static final BigDecimal TRANSACTION_FEE_PERCENTAGE = new BigDecimal("0.01");

    @Override
    @Transactional
    public String transferMoney(TransferRequest transferRequest) {
        Account fromAccount = accountRepository.findById(transferRequest.getFromAccountId())
                .orElseThrow(() -> new AccountNotFoundException("From account not found"));
        Account toAccount = accountRepository.findById(transferRequest.getToAccountId())
                .orElseThrow(() -> new AccountNotFoundException("To account not found"));

        if (transferRequest.getCurrency() != fromAccount.getCurrency()) {
            throw new InvalidCurrencyException("Transfer currency must match the sender's account base currency.");
        }
        // Get the amount to transfer in the sender's currency
        BigDecimal amountToTransferInFromCurrency = transferRequest.getAmount();
        BigDecimal transactionFee = amountToTransferInFromCurrency.multiply(TRANSACTION_FEE_PERCENTAGE)
                                    .setScale(getScaleForCurrency(fromAccount.getCurrency()), DEFAULT_ROUNDING);
        // Calculate the total deduction from the sender's account
        BigDecimal totalDeduction = amountToTransferInFromCurrency.add(transactionFee);

        // Check if the sender has enough funds
        if (fromAccount.getBalance().compareTo(totalDeduction) < 0) {
            throw new InsufficientFundsException("Insufficient funds in sender's account.");
        }

        // Convert the amount to the recipient's currency
        BigDecimal amountInToCurrency;
        try {
            // Convert the amount to the recipient's currency
            amountInToCurrency = convertAmount(amountToTransferInFromCurrency, fromAccount.getCurrency(), toAccount.getCurrency());
        } catch (ArithmeticException e) {
             throw new InvalidCurrencyException("Error during currency conversion calculation: " + e.getMessage());
        }

        // Update the sender's and recipient's balances
        fromAccount.setBalance(fromAccount.getBalance().subtract(totalDeduction));
        toAccount.setBalance(toAccount.getBalance().add(amountInToCurrency));

        // Save the updated balances
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Return a success message
        return "Transfer successful from " + fromAccount.getCurrency() + " to " + toAccount.getCurrency() + ".";
    }

    @Override
    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
    }

    /*
     * Get the scale for the currency.
     * 
     * @param currency The currency.
     * @return The scale for the currency.
     */
    private int getScaleForCurrency(Currency currency) {
        switch (currency) {
            case JPN:
                return 0;
            case USD:
            case AUD:
            case CNY:
            default:
                return 2;
        }
    }

    /*
     * Convert the amount from the source currency to the target currency.
     * We convert the amount to USD first and then to the target currency. As the banking system uses USD as an intermediate currency.
     * 
     * @param amount The amount to convert.
     * @param fromCurrency The source currency.
     * @param toCurrency The target currency.
     * @return The converted amount.
     */
    private BigDecimal convertAmount(BigDecimal amount, Currency fromCurrency, Currency toCurrency) {
        if (fromCurrency == toCurrency) {
            return amount.setScale(getScaleForCurrency(toCurrency), DEFAULT_ROUNDING);
        }

        BigDecimal amountInUSD;

        // Step 1: Convert 'amount' from 'fromCurrency' to USD
        switch (fromCurrency) {
            case USD:
                amountInUSD = amount;
                break;
            case AUD:
                amountInUSD = amount.multiply(USD_PER_AUD); // amount_aud * (0.5 USD / 1 AUD)
                break;
            case JPN:
                amountInUSD = amount.divide(JPN_PER_USD, DEFAULT_SCALE, DEFAULT_ROUNDING); // amount_jpn / (110 JPN / 1 USD)
                break;
            case CNY:
                amountInUSD = amount.divide(CNY_PER_USD, DEFAULT_SCALE, DEFAULT_ROUNDING); // amount_cny / (7 CNY / 1 USD)
                break;
            default:
                throw new InvalidCurrencyException("Unsupported source currency for conversion: " + fromCurrency);
        }

        // Step 2: Convert 'amountInUSD' to 'toCurrency'
        BigDecimal finalAmount;
        switch (toCurrency) {
            case USD:
                finalAmount = amountInUSD;
                break;
            case AUD:
                finalAmount = amountInUSD.multiply(AUD_PER_USD); // amount_usd * (2 AUD / 1 USD)
                break;
            case JPN:
                finalAmount = amountInUSD.multiply(JPN_PER_USD);
                break;
            case CNY:
                finalAmount = amountInUSD.multiply(CNY_PER_USD);
                break;
            default:
                throw new InvalidCurrencyException("Unsupported target currency for conversion: " + toCurrency);
        }
        return finalAmount.setScale(getScaleForCurrency(toCurrency), DEFAULT_ROUNDING);
    }
} 