package com.example.andemo.nexacro;

import com.example.andemo.disposal.DisposalDtos;
import com.example.andemo.disposal.DisposalException;
import com.example.andemo.disposal.DisposalReason;
import com.example.andemo.disposal.DisposalService;
import com.example.andemo.disposal.DisposalStatus;
import com.example.andemo.entity.User;
import com.example.andemo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GIẢ LẬP server Nexacro mobile "cũ" cho chức năng 폐기 (task 17, 18): các URL *.do nhận / trả
 * XML Dataset như X-API, đăng nhập bằng HttpSession (cookie JSESSIONID) như nhiều hệ thống Nexacro.
 *
 * Client tương ứng: nexacro-sample/disposal/ (form .xfdl + lib/common.xjs).
 * Nghiệp vụ dùng lại DisposalService (giống thực tế: service giữ nguyên, chỉ lớp vào / ra khác).
 * Tên cột theo kiểu hệ thống Hàn Quốc: CHỮ_HOA, ngày yyyyMMdd, giờ yyyyMMddHHmmss, mã trạng thái 10/20/90,
 * mã lý do 01..06 (bảng mã chung DISP_RSN).
 */
@Slf4j
@RestController
@RequestMapping("/nexacro")
@RequiredArgsConstructor
public class LegacyDisposalController {

    private static final MediaType XML = new MediaType("text", "xml", StandardCharsets.UTF_8);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter YMDHMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(KST);

    /** Session key (giống code cũ hay đặt tên SESSION_*) */
    static final String S_USER_ID = "SESSION_USER_ID";
    static final String S_ROLE = "SESSION_USER_ROLE";

    /** Mã chung DISP_RSN (폐기사유): mã số của hệ thống cũ ↔ enum của service */
    private static final Map<String, DisposalReason> REASON_BY_CODE = new LinkedHashMap<>();

    static {
        REASON_BY_CODE.put("01", DisposalReason.EXPIRED);
        REASON_BY_CODE.put("02", DisposalReason.DAMAGED);
        REASON_BY_CODE.put("03", DisposalReason.SPOILED);
        REASON_BY_CODE.put("04", DisposalReason.RECALL);
        REASON_BY_CODE.put("05", DisposalReason.QUALITY);
        REASON_BY_CODE.put("99", DisposalReason.OTHER);
    }

    private final DisposalService service;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ============================ common ============================

    /** in: ds_login(USER_ID, PASSWORD) → out: ds_user(USER_ID, USER_ROLE, STORE_CD, BIZ_DT) + session */
    @PostMapping("/common/login.do")
    public ResponseEntity<String> login(@RequestBody(required = false) String body, HttpServletRequest request) {
        NxDataset in = parse(body).dataset("ds_login");
        String userId = in == null || in.getRows().isEmpty() ? null : in.getString(0, "USER_ID");
        String password = in == null || in.getRows().isEmpty() ? null : in.getString(0, "PASSWORD");
        Optional<User> user = userId == null ? Optional.empty() : userRepository.findByUsername(userId);
        if (user.isEmpty() || password == null || !passwordEncoder.matches(password, user.get().getPassword())) {
            return xml(NexacroData.error(-1, "아이디 또는 비밀번호가 올바르지 않습니다. (Sai tài khoản hoặc mật khẩu)"));
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate(); // đổi session id sau login (chống session fixation)
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(S_USER_ID, user.get().getUsername());
        session.setAttribute(S_ROLE, user.get().getRole());

        DisposalDtos.ClosingInfo closing = service.closingInfo();
        NxDataset ds = new NxDataset("ds_user").addColumn("USER_ID", "string").addColumn("USER_ROLE", "string")
                .addColumn("STORE_CD", "string").addColumn("BIZ_DT", "string");
        Map<String, Object> row = ds.addRow();
        row.put("USER_ID", user.get().getUsername());
        row.put("USER_ROLE", user.get().getRole());
        row.put("STORE_CD", closing.storeCode());
        row.put("BIZ_DT", closing.currentBusinessDate().format(YMD));
        return xml(NexacroData.success().addDataset(ds));
    }

    @PostMapping("/common/logout.do")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return xml(NexacroData.success());
    }

    /** args: GRP_CD (DISP_RSN | DISP_STAT) → out: ds_code(CD, CD_NM) */
    @PostMapping("/common/selectCode.do")
    public ResponseEntity<String> selectCode(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            String group = param(in, "GRP_CD");
            NxDataset ds = new NxDataset("ds_code").addColumn("CD", "string").addColumn("CD_NM", "string");
            if ("DISP_RSN".equals(group)) {
                REASON_BY_CODE.forEach((code, reason) -> code(ds, code, reason.getKoreanName()));
            } else if ("DISP_STAT".equals(group)) {
                for (DisposalStatus s : DisposalStatus.values()) {
                    code(ds, s.getCode(), s.getKoreanName());
                }
            } else {
                throw new DisposalException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION",
                        "알 수 없는 코드그룹: " + group);
            }
            return NexacroData.success().addDataset(ds);
        });
    }

    // ============================ 폐기조회 / 상세 ============================

    /** in: ds_cond(STAT_CD, FROM_DT, TO_DT) → out: ds_list */
    @PostMapping("/disposal/selectList.do")
    public ResponseEntity<String> selectList(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            NxDataset cond = in.dataset("ds_cond");
            String stat = cond == null || cond.getRows().isEmpty() ? null : cond.getString(0, "STAT_CD");
            LocalDate from = date(cond == null || cond.getRows().isEmpty() ? null : cond.getString(0, "FROM_DT"));
            LocalDate to = date(cond == null || cond.getRows().isEmpty() ? null : cond.getString(0, "TO_DT"));

            NxDataset ds = new NxDataset("ds_list")
                    .addColumn("DISPOSAL_NO", "string").addColumn("BIZ_DT", "string")
                    .addColumn("STAT_CD", "string").addColumn("STAT_NM", "string")
                    .addColumn("REG_ID", "string").addColumn("REG_DTM", "string")
                    .addColumn("LINE_CNT", "int").addColumn("TOT_QTY", "int").addColumn("TOT_COST_AMT", "int")
                    .addColumn("REMARK", "string");
            for (DisposalDtos.Summary s : service.list(status(stat), from, to)) {
                Map<String, Object> r = ds.addRow();
                r.put("DISPOSAL_NO", s.disposalNo());
                r.put("BIZ_DT", s.businessDate().format(YMD));
                r.put("STAT_CD", s.statusCode());
                r.put("STAT_NM", s.statusName());
                r.put("REG_ID", s.registeredBy());
                r.put("REG_DTM", dtm(s.registeredAt()));
                r.put("LINE_CNT", s.lineCount());
                r.put("TOT_QTY", s.totalQty());
                r.put("TOT_COST_AMT", s.totalCostAmount());
                r.put("REMARK", nvl(s.remark()));
            }
            return NexacroData.success().addDataset(ds);
        });
    }

    /** args: DISPOSAL_NO → out: ds_master (1 dòng), ds_detail */
    @PostMapping("/disposal/selectDetail.do")
    public ResponseEntity<String> selectDetail(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) ->
                detailData(service.detail(required(in, "DISPOSAL_NO"), user, admin)));
    }

    /** 스캔: args ITEM_CD → out: ds_item */
    @PostMapping("/disposal/selectItem.do")
    public ResponseEntity<String> selectItem(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            DisposalDtos.ItemInfo i = service.item(required(in, "ITEM_CD"));
            NxDataset ds = new NxDataset("ds_item")
                    .addColumn("ITEM_CD", "string").addColumn("ITEM_NM", "string")
                    .addColumn("STOCK_QTY", "int").addColumn("AVAIL_QTY", "int")
                    .addColumn("COST_PRC", "int").addColumn("SALE_PRC", "int");
            Map<String, Object> r = ds.addRow();
            r.put("ITEM_CD", i.itemCode());
            r.put("ITEM_NM", i.itemName());
            r.put("STOCK_QTY", i.onHandQty());
            r.put("AVAIL_QTY", i.availableQty());
            r.put("COST_PRC", i.costPrice());
            r.put("SALE_PRC", i.salePrice());
            return NexacroData.success().addDataset(ds);
        });
    }

    // ============================ 등록 / 수정 ============================

    /**
     * 등록 + 수정 chung 1 URL (kiểu Nexacro hay làm):
     * in: ds_master(DISPOSAL_NO, REMARK, VER), ds_detail:U (ITEM_CD, DISP_QTY, REASON_CD; rowtype insert/update/delete)
     * DISPOSAL_NO trống = đăng ký mới. Có = sửa: áp các dòng thay đổi lên các dòng hiện có.
     * out: ds_master, ds_detail của phiếu sau khi lưu.
     */
    @PostMapping("/disposal/save.do")
    public ResponseEntity<String> save(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            NxDataset master = in.dataset("ds_master");
            NxDataset detail = in.dataset("ds_detail");
            if (master == null || master.getRows().isEmpty()) {
                return NexacroData.error(-1, "ds_master 가 없습니다.");
            }
            String no = master.getString(0, "DISPOSAL_NO");
            String remark = master.getString(0, "REMARK");
            List<Map<String, Object>> changed = detail == null ? List.of() : detail.getRows();

            if (no == null || no.isBlank()) {
                List<DisposalDtos.LineInput> lines = new ArrayList<>();
                for (Map<String, Object> r : changed) {
                    if (!"delete".equals(NxDataset.rowType(r))) {
                        lines.add(lineInput(r));
                    }
                }
                return detailData(service.register(new DisposalDtos.SaveRequest(null, emptyToNull(remark), lines),
                        user, admin));
            }

            // Sửa: bắt đầu từ các dòng hiện có, áp rowtype theo ITEM_CD
            DisposalDtos.Detail current = service.detail(no, user, admin);
            LinkedHashMap<String, DisposalDtos.LineInput> lines = new LinkedHashMap<>();
            for (DisposalDtos.Line l : current.items()) {
                lines.put(l.itemCode(), new DisposalDtos.LineInput(l.itemCode(), l.qty(), l.reasonCode()));
            }
            for (Map<String, Object> r : changed) {
                String type = NxDataset.rowType(r);
                Map<String, Object> org = orgRow(r);
                String orgCode = org != null && org.get("ITEM_CD") != null ? org.get("ITEM_CD").toString() : str(r, "ITEM_CD");
                switch (type) {
                    case "insert" -> lines.put(str(r, "ITEM_CD"), lineInput(r));
                    case "update" -> {
                        lines.remove(orgCode);
                        lines.put(str(r, "ITEM_CD"), lineInput(r));
                    }
                    case "delete" -> lines.remove(orgCode);
                    default -> { /* dòng không đổi: bỏ qua */ }
                }
            }
            Long ver = longParam(master.getRows().get(0).get("VER"));
            return detailData(service.update(no, new DisposalDtos.SaveRequest(ver, emptyToNull(remark),
                    new ArrayList<>(lines.values())), user, admin));
        });
    }

    // ============================ 취소 / 확정 / 확정취소 ============================

    /** args: DISPOSAL_NO, VER, RSN */
    @PostMapping("/disposal/cancel.do")
    public ResponseEntity<String> cancel(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> detailData(service.cancel(required(in, "DISPOSAL_NO"),
                action(in), user, admin)));
    }

    /** args: DISPOSAL_NO, VER. Chỉ 점장 (ADMIN). */
    @PostMapping("/disposal/confirm.do")
    public ResponseEntity<String> confirm(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            if (!admin) {
                return NexacroData.error(-3, "점장만 확정할 수 있습니다. (Chỉ cửa hàng trưởng được xác nhận)");
            }
            return detailData(service.confirm(required(in, "DISPOSAL_NO"), action(in), user));
        });
    }

    /** args: DISPOSAL_NO, VER, RSN. Chỉ 점장 (ADMIN). */
    @PostMapping("/disposal/cancelConfirm.do")
    public ResponseEntity<String> cancelConfirm(@RequestBody(required = false) String body, HttpServletRequest request) {
        return call(request, body, (in, user, admin) -> {
            if (!admin) {
                return NexacroData.error(-3, "점장만 확정취소할 수 있습니다. (Chỉ cửa hàng trưởng được hủy xác nhận)");
            }
            return detailData(service.cancelConfirm(required(in, "DISPOSAL_NO"), action(in), user));
        });
    }

    // ============================ helpers ============================

    private interface Handler {
        NexacroData handle(NexacroData in, String user, boolean admin);
    }

    /**
     * Khung chung của mọi service: đọc XML, kiểm tra session, gọi nghiệp vụ, đổi lỗi thành ErrorCode &lt; 0.
     * ErrorCode -99 = hết phiên (client gfn_callback đưa về màn login).
     */
    private ResponseEntity<String> call(HttpServletRequest request, String body, Handler handler) {
        HttpSession session = request.getSession(false);
        String user = session == null ? null : (String) session.getAttribute(S_USER_ID);
        if (user == null) {
            return xml(NexacroData.error(-99, "세션이 만료되었습니다. 다시 로그인하세요. (Hết phiên, đăng nhập lại)"));
        }
        boolean admin = "ADMIN".equals(session.getAttribute(S_ROLE));
        try {
            return xml(handler.handle(parse(body), user, admin));
        } catch (DisposalException e) {
            String msg = e.getMessage();
            if (!e.getDetails().isEmpty()) {
                msg += "\n- " + String.join("\n- ", e.getDetails());
            }
            return xml(NexacroData.error(errorCode(e.getCode()), msg));
        } catch (ObjectOptimisticLockingFailureException e) {
            return xml(NexacroData.error(-2, "다른 사용자가 먼저 변경했습니다. 다시 조회하세요. (Dữ liệu đã bị người khác thay đổi)"));
        } catch (IllegalArgumentException e) {
            return xml(NexacroData.error(-1, e.getMessage()));
        }
    }

    /** Mã lỗi âm theo loại, để client phân biệt nếu cần */
    private static int errorCode(String code) {
        return switch (code) {
            case "STALE_DATA" -> -2;
            case "FORBIDDEN" -> -3;
            case "CLOSED_PERIOD" -> -4;
            case "INSUFFICIENT_QTY" -> -5;
            default -> -1;
        };
    }

    private static NexacroData detailData(DisposalDtos.Detail d) {
        NxDataset m = new NxDataset("ds_master")
                .addColumn("DISPOSAL_NO", "string").addColumn("STORE_CD", "string").addColumn("BIZ_DT", "string")
                .addColumn("STAT_CD", "string").addColumn("STAT_NM", "string").addColumn("REMARK", "string")
                .addColumn("REG_ID", "string").addColumn("REG_DTM", "string")
                .addColumn("UPD_ID", "string").addColumn("UPD_DTM", "string")
                .addColumn("CFM_ID", "string").addColumn("CFM_DTM", "string")
                .addColumn("CNCL_ID", "string").addColumn("CNCL_DTM", "string").addColumn("CNCL_RSN", "string")
                .addColumn("CFM_CNCL_ID", "string").addColumn("CFM_CNCL_DTM", "string").addColumn("CFM_CNCL_RSN", "string")
                .addColumn("TOT_QTY", "int").addColumn("TOT_COST_AMT", "int").addColumn("TOT_SALE_AMT", "int")
                .addColumn("CLOSE_YN", "string").addColumn("VER", "int");
        Map<String, Object> r = m.addRow();
        r.put("DISPOSAL_NO", d.disposalNo());
        r.put("STORE_CD", d.storeCode());
        r.put("BIZ_DT", d.businessDate().format(YMD));
        r.put("STAT_CD", d.statusCode());
        r.put("STAT_NM", d.statusName());
        r.put("REMARK", nvl(d.remark()));
        r.put("REG_ID", d.registeredBy());
        r.put("REG_DTM", dtm(d.registeredAt()));
        r.put("UPD_ID", nvl(d.updatedBy()));
        r.put("UPD_DTM", dtm(d.updatedAt()));
        r.put("CFM_ID", nvl(d.confirmedBy()));
        r.put("CFM_DTM", dtm(d.confirmedAt()));
        r.put("CNCL_ID", nvl(d.cancelledBy()));
        r.put("CNCL_DTM", dtm(d.cancelledAt()));
        r.put("CNCL_RSN", nvl(d.cancelReason()));
        r.put("CFM_CNCL_ID", nvl(d.confirmCancelledBy()));
        r.put("CFM_CNCL_DTM", dtm(d.confirmCancelledAt()));
        r.put("CFM_CNCL_RSN", nvl(d.confirmCancelReason()));
        r.put("TOT_QTY", d.totalQty());
        r.put("TOT_COST_AMT", d.totalCostAmount());
        r.put("TOT_SALE_AMT", d.totalSaleAmount());
        r.put("CLOSE_YN", d.closed() ? "Y" : "N");
        r.put("VER", d.version());

        // Chỉ trả dữ liệu thô: bật / tắt nút, kiểm tra thiếu tồn nằm trong SCRIPT của form (như code cũ)
        NxDataset det = new NxDataset("ds_detail")
                .addColumn("LINE_NO", "int").addColumn("ITEM_CD", "string").addColumn("ITEM_NM", "string")
                .addColumn("DISP_QTY", "int").addColumn("REASON_CD", "string").addColumn("REASON_NM", "string")
                .addColumn("COST_PRC", "int").addColumn("SALE_PRC", "int")
                .addColumn("COST_AMT", "int").addColumn("SALE_AMT", "int")
                .addColumn("STOCK_QTY", "int").addColumn("AVAIL_QTY", "int");
        for (DisposalDtos.Line l : d.items()) {
            Map<String, Object> x = det.addRow();
            x.put("LINE_NO", l.lineNo());
            x.put("ITEM_CD", l.itemCode());
            x.put("ITEM_NM", l.itemName());
            x.put("DISP_QTY", l.qty());
            x.put("REASON_CD", reasonCode(l.reasonCode()));
            x.put("REASON_NM", l.reasonName());
            x.put("COST_PRC", l.costPrice());
            x.put("SALE_PRC", l.salePrice());
            x.put("COST_AMT", l.costAmount());
            x.put("SALE_AMT", l.saleAmount());
            x.put("STOCK_QTY", l.onHandQty());
            x.put("AVAIL_QTY", l.availableQty());
        }
        return NexacroData.success().addDataset(m).addDataset(det);
    }

    private static DisposalDtos.LineInput lineInput(Map<String, Object> r) {
        String code = str(r, "REASON_CD");
        DisposalReason reason = code == null ? null : REASON_BY_CODE.get(code);
        if (code != null && reason == null) {
            throw new IllegalArgumentException("폐기사유 코드가 올바르지 않습니다: " + code);
        }
        return new DisposalDtos.LineInput(str(r, "ITEM_CD"), longParam(r.get("DISP_QTY")), reason);
    }

    private static String reasonCode(DisposalReason reason) {
        for (Map.Entry<String, DisposalReason> e : REASON_BY_CODE.entrySet()) {
            if (e.getValue() == reason) {
                return e.getKey();
            }
        }
        return null;
    }

    private static DisposalDtos.ActionRequest action(NexacroData in) {
        return new DisposalDtos.ActionRequest(longParam(in.getParams().get("VER")), emptyToNull(param(in, "RSN")));
    }

    private static DisposalStatus status(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (DisposalStatus s : DisposalStatus.values()) {
            if (s.getCode().equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("상태 코드가 올바르지 않습니다: " + code);
    }

    private static void code(NxDataset ds, String code, String name) {
        Map<String, Object> r = ds.addRow();
        r.put("CD", code);
        r.put("CD_NM", name);
    }

    private static String param(NexacroData in, String name) {
        Object v = in.getParams().get(name);
        return v == null ? null : v.toString();
    }

    private static String required(NexacroData in, String name) {
        String v = param(in, name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " 가 없습니다.");
        }
        return v.trim();
    }

    private static Long longParam(Object v) {
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("숫자가 아닙니다: " + v);
        }
    }

    private static String str(Map<String, Object> r, String col) {
        Object v = r.get(col);
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> orgRow(Map<String, Object> r) {
        Object o = r.get(NxDataset.ORG_ROW);
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static LocalDate date(String yyyymmdd) {
        return yyyymmdd == null || yyyymmdd.isBlank() ? null : LocalDate.parse(yyyymmdd.trim(), YMD);
    }

    private static String dtm(Instant t) {
        return t == null ? "" : YMDHMS.format(t);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static NexacroData parse(String body) {
        return body == null || body.isBlank() ? new NexacroData() : NexacroXml.parse(body);
    }

    private static ResponseEntity<String> xml(NexacroData data) {
        return ResponseEntity.ok().contentType(XML).body(NexacroXml.write(data));
    }
}
