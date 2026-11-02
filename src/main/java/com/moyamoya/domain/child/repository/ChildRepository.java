package com.moyamoya.domain.child.repository;

import com.moyamoya.domain.child.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
}
