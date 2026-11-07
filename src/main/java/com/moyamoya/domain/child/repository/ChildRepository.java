package com.moyamoya.domain.child.repository;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildRepository extends JpaRepository<Child, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Child c where c.id = :childId")
    Optional<Child> findByIdForUpdate(@Param("childId") Long childId);

    Optional<Child> findFirstByUserOrderByIdAsc(User user);
}
