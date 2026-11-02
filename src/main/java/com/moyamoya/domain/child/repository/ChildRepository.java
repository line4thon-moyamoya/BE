package com.moyamoya.domain.child.repository;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {

    Optional<Child> findFirstByUserOrderByIdAsc(User user);
}
