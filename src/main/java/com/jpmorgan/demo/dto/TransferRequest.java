package com.jpmorgan.demo.dto;

import com.jpmorgan.demo.model.Currency;
import lombok.Data;

import java.math.BigDecimal;


/*
 * Transfer request DTO.
 * 
 * @param fromAccountId The ID of the account to transfer from.
 * @param toAccountId The ID of the account to transfer to.
 * @param amount The amount to transfer.
 * @param currency The currency of the transfer.
 */
@Data
public class TransferRequest {
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private Currency currency;
} 