package com.nepal.transparency.watchdog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CorruptionReportRepository extends JpaRepository<CorruptionReport, String> {
    List<CorruptionReport> findByProjectTid(String projectTid);
    boolean existsByProjectTidAndWhistleblowerHash(String projectTid, String whistleblowerHash);
}
