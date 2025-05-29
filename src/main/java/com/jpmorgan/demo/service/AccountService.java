package com.jpmorgan.demo.service;

import com.jpmorgan.demo.dto.TransferRequest;
import com.jpmorgan.demo.model.Account;

public interface AccountService {

    /*
     * Transfer money between accounts.
     * 
     * @param transferRequest The transfer request containing the source and target account IDs, amount, and currency.
     * @return A success message.
     */
    String transferMoney(TransferRequest transferRequest);


    /*
     * Get an account by its ID.
     * 
     * @param id The ID of the account to get.
     * @return The account.
     */
    Account getAccountById(Long id);
} 