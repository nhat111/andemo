package com.example.restdemo.pda;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** SQL nằm ở resources/mapper/DeviceActivityMapper.xml */
@Mapper
public interface DeviceActivityMapper {

    int touch(@Param("uniqueId") String uniqueId,
              @Param("storeCd") String storeCd,
              @Param("userId") String userId);

    int logout(@Param("uniqueId") String uniqueId);

    List<DeviceActivity> findCandidates(@Param("storeCd") String storeCd,
                                        @Param("userId") String userId,
                                        @Param("activeSince") LocalDateTime activeSince);

    DeviceActivity findByUniqueId(@Param("uniqueId") String uniqueId);
}
