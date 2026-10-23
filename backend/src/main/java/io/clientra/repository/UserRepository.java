package io.clientra.repository;

import io.clientra.entity.User;
import io.clientra.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleAndActiveTrueOrderByFullName(Role role);

    List<User> findByActiveTrueOrderByFullName();
}
