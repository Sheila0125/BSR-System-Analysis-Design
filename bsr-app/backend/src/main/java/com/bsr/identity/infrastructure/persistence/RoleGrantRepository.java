package com.bsr.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface RoleGrantRepository extends JpaRepository<RoleGrantRow, Long> {

    List<RoleGrantRow> findByAccountIdOrderByRoleGrantId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from RoleGrantRow grant where grant.roleGrantId = :roleGrantId")
    Optional<RoleGrantRow> findByIdForUpdate(Long roleGrantId);
}
