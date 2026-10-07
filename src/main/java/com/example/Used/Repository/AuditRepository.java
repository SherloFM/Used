package com.example.Used.Repository;

import com.example.Used.Model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);
}
