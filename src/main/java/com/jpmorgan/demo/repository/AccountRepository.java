package com.jpmorgan.demo.repository;

import com.jpmorgan.demo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/*
 * Account repository.
 * 
 * @param Account The account entity.
 * @param Long The ID of the account.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
} 