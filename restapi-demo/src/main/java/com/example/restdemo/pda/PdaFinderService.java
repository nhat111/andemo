package com.example.restdemo.pda;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chọn PDA khi người dùng bấm "Tìm PDA" trên PC (1 nút, không có danh sách thiết bị).
 *
 * Trong các PDA của cửa hàng người bấm, hoạt động trong inactive-days ngày gần nhất:
 *   1. Ưu tiên PDA của CHÍNH người bấm (người dùng gần nhất của máy = người bấm)
 *   2. Không có → PDA dùng gần nhất của CỬA HÀNG (nếu fallback-to-store = true), ngược lại: không có máy
 *   3. Trong nhóm được chọn, các máy hoạt động cách máy mới nhất <= near-minutes phút → gửi TẤT CẢ
 *   4. Không còn máy nào → NONE
 * Máy đã đăng xuất vẫn được chọn (máy dùng chung thường đăng xuất rồi mới thất lạc).
 */
@Service
public class PdaFinderService {

    public static final String RULE_USER = "USER";   // tìm theo máy của người bấm
    public static final String RULE_STORE = "STORE"; // người bấm không có máy → máy của cửa hàng
    public static final String RULE_NONE = "NONE";   // không có máy nào

    private final DeviceActivityMapper mapper;
    private final int inactiveDays;
    private final int nearMinutes;
    private final boolean fallbackToStore;

    public PdaFinderService(DeviceActivityMapper mapper,
                            @Value("${pda.finder.inactive-days:7}") int inactiveDays,
                            @Value("${pda.finder.near-minutes:10}") int nearMinutes,
                            @Value("${pda.finder.fallback-to-store:true}") boolean fallbackToStore) {
        this.mapper = mapper;
        this.inactiveDays = inactiveDays;
        this.nearMinutes = nearMinutes;
        this.fallbackToStore = fallbackToStore;
    }

    public Result find(LoginUser presser) {
        return find(presser, fallbackToStore);
    }

    public Result find(LoginUser presser, boolean fallbackToStore) {
        // Mốc thời gian tính bằng giờ của app server; LAST_ACTIVE_AT ghi bằng SYSDATE của DB.
        // Hai đồng hồ nên đồng bộ (NTP); lệch vài giây không ảnh hưởng mốc tính bằng ngày.
        LocalDateTime activeSince = LocalDateTime.now().minusDays(inactiveDays);
        List<DeviceActivity> candidates =
                mapper.findCandidates(presser.getStoreCd(), presser.getUserId(), activeSince);
        if (candidates.isEmpty()) {
            return new Result(RULE_NONE, Collections.<DeviceActivity>emptyList());
        }

        // SQL đã xếp máy của người bấm lên đầu → dòng đầu cho biết người bấm có máy hay không
        boolean presserHasDevice = presser.getUserId().equals(candidates.get(0).getUserId());
        if (!presserHasDevice && !fallbackToStore) {
            return new Result(RULE_NONE, Collections.<DeviceActivity>emptyList());
        }

        // Trong nhóm được chọn (cùng ưu tiên), dòng đầu là máy mới nhất: lấy các máy gần nó
        LocalDateTime newest = candidates.get(0).getLastActiveAt();
        LocalDateTime nearFrom = newest.minusMinutes(nearMinutes);
        List<DeviceActivity> targets = new ArrayList<>();
        for (DeviceActivity d : candidates) {
            boolean sameGroup = presser.getUserId().equals(d.getUserId()) == presserHasDevice;
            if (sameGroup && !d.getLastActiveAt().isBefore(nearFrom)) {
                targets.add(d);
            }
        }
        return new Result(presserHasDevice ? RULE_USER : RULE_STORE, targets);
    }

    public static class Result {
        private final String rule;
        private final List<DeviceActivity> targets;

        Result(String rule, List<DeviceActivity> targets) {
            this.rule = rule;
            this.targets = targets;
        }

        public String getRule() {
            return rule;
        }

        public List<DeviceActivity> getTargets() {
            return targets;
        }
    }
}
