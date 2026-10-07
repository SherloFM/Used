package com.example.Used.Repository;

import com.example.Used.Model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {
}
