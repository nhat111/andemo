package com.example.andemo.disposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DisposalRequestRepository extends JpaRepository<DisposalRequest, String> {

    /** 폐기조회: lọc theo trạng thái (null = tất cả) và khoảng 영업일자 (null = không giới hạn) */
    @Query("""
            select d from DisposalRequest d
            where (:status is null or d.status = :status)
              and (cast(:from as date) is null or d.businessDate >= :from)
              and (cast(:to as date) is null or d.businessDate <= :to)
            order by d.businessDate desc, d.disposalNo desc""")
    List<DisposalRequest> search(@Param("status") DisposalStatus status,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
