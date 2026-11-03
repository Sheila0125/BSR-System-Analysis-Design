package com.bsr.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountRow, Long> {

    Optional<AccountRow> findByLoginNameIgnoreCase(String loginName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AccountRow account where account.accountId = :accountId")
    Optional<AccountRow> findByIdForUpdate(Long accountId);
}
