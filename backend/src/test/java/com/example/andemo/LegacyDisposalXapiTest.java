package com.example.andemo;

import com.example.andemo.nexacro.NexacroData;
import com.example.andemo.nexacro.NexacroXml;
import com.example.andemo.nexacro.NxDataset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server Nexacro "cũ" cho 폐기 (nexacro-sample/disposal): XML Dataset + session cookie,
 * gửi / nhận đúng như form .xfdl gọi transaction().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyDisposalXapiTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String KIMBAP = "8801111222333";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void withoutSessionReturnsErrorCodeMinus99() {
        NexacroData out = call("/nexacro/disposal/selectList.do", new NexacroData(), null);
        assertThat(out.getParams().get("ErrorCode")).isEqualTo(-99L);
    }

    @Test
    void loginWrongPasswordIsRejected() {
        NexacroData out = login("user", "sai-mat-khau").data;
        assertThat(out.getParams().get("ErrorCode")).isEqualTo(-1L);
    }

    @Test
    void loginThenInquiryAndDetailUseKoreanStyleColumns() {
        Session s = login("user", "123456");
        NxDataset user = s.data.dataset("ds_user");
        assertThat(user.getString(0, "USER_ROLE")).isEqualTo("USER");
        assertThat(user.getString(0, "BIZ_DT")).isEqualTo(TODAY);

        NexacroData list = call("/nexacro/disposal/selectList.do",
                data(ds("ds_cond", Map.of("STAT_CD", "10", "FROM_DT", TODAY, "TO_DT", TODAY))), s.cookie);
        NxDataset dsList = list.dataset("ds_list");
        assertThat(dsList.getRows()).hasSize(3);
        assertThat(dsList.getString(0, "STAT_NM")).isEqualTo("등록");
        assertThat(dsList.getColumns().get("TOT_COST_AMT")).isEqualTo("int");

        NexacroData detail = call("/nexacro/disposal/selectDetail.do",
                param("DISPOSAL_NO", "S001-" + TODAY + "-0003"), s.cookie);
        assertThat(detail.dataset("ds_master").getString(0, "CLOSE_YN")).isEqualTo("N");
        NxDataset det = detail.dataset("ds_detail");
        assertThat(det.getString(0, "REASON_CD")).isEqualTo("04"); // 리콜
        assertThat(det.getRows().get(0).get("AVAIL_QTY")).isEqualTo(20L);

        NexacroData codes = call("/nexacro/common/selectCode.do", param("GRP_CD", "DISP_RSN"), s.cookie);
        assertThat(codes.dataset("ds_code").getRows()).hasSize(6);
    }

    @Test
    void saveNewThenEditWithRowTypesThenConfirmByManager() {
        Session user = login("user", "123456");

        // 등록: ds_master (DISPOSAL_NO trống) + ds_detail toàn dòng insert
        NxDataset master = ds("ds_master", Map.of("DISPOSAL_NO", "", "REMARK", "Hủy cuối ca"));
        NxDataset detail = new NxDataset("ds_detail").addColumn("ITEM_CD", "string")
                .addColumn("DISP_QTY", "int").addColumn("REASON_CD", "string");
        insert(detail, KIMBAP, 2, "01");
        insert(detail, "8901234567890", 1, "02");
        NexacroData saved = call("/nexacro/disposal/save.do", data(master, detail), user.cookie);
        assertThat(saved.getParams().get("ErrorCode")).isEqualTo(0L);
        String no = saved.dataset("ds_master").getString(0, "DISPOSAL_NO");
        assertThat(no).isEqualTo("S001-" + TODAY + "-0004");
        long ver = ((Number) saved.dataset("ds_master").getRows().get(0).get("VER")).longValue();

        // 수정: ds_detail:U chỉ gửi dòng đổi — update kimbap 2→3, delete nước suối
        NxDataset m2 = ds("ds_master", Map.of("DISPOSAL_NO", no, "REMARK", "Sửa", "VER", ver));
        NxDataset d2 = new NxDataset("ds_detail").addColumn("ITEM_CD", "string")
                .addColumn("DISP_QTY", "int").addColumn("REASON_CD", "string");
        Map<String, Object> upd = d2.addRow();
        upd.put("ITEM_CD", KIMBAP);
        upd.put("DISP_QTY", 3L);
        upd.put("REASON_CD", "01");
        upd.put(NxDataset.ROW_TYPE, "update");
        upd.put(NxDataset.ORG_ROW, new LinkedHashMap<>(Map.of("ITEM_CD", KIMBAP, "DISP_QTY", 2L, "REASON_CD", "01")));
        Map<String, Object> del = d2.addRow();
        del.put("ITEM_CD", "8901234567890");
        del.put("DISP_QTY", 1L);
        del.put("REASON_CD", "02");
        del.put(NxDataset.ROW_TYPE, "delete");
        NexacroData edited = call("/nexacro/disposal/save.do", data(m2, d2), user.cookie);
        assertThat(edited.getParams().get("ErrorCode")).isEqualTo(0L);
        assertThat(edited.dataset("ds_detail").getRows()).hasSize(1);
        assertThat(edited.dataset("ds_detail").getRows().get(0).get("DISP_QTY")).isEqualTo(3L);
        long ver2 = ((Number) edited.dataset("ds_master").getRows().get(0).get("VER")).longValue();

        // 점원 확정 → -3
        NexacroData denied = call("/nexacro/disposal/confirm.do", params(Map.of("DISPOSAL_NO", no, "VER", ver2)), user.cookie);
        assertThat(denied.getParams().get("ErrorCode")).isEqualTo(-3L);

        // 점장 확정 → 20
        Session admin = login("admin", "123456");
        NexacroData ok = call("/nexacro/disposal/confirm.do", params(Map.of("DISPOSAL_NO", no, "VER", ver2)), admin.cookie);
        assertThat(ok.getParams().get("ErrorCode")).isEqualTo(0L);
        assertThat(ok.dataset("ds_master").getString(0, "STAT_CD")).isEqualTo("20");
        assertThat(ok.dataset("ds_detail").getRows().get(0).get("STOCK_QTY")).isEqualTo(9L);
    }

    @Test
    void businessErrorsBecomeNegativeErrorCodesWithKoreanFriendlyMessages() {
        Session admin = login("admin", "123456");
        NexacroData shortage = call("/nexacro/disposal/confirm.do",
                param("DISPOSAL_NO", "S001-" + TODAY + "-0003"), admin.cookie);
        assertThat(shortage.getParams().get("ErrorCode")).isEqualTo(-5L);
        assertThat(shortage.getParams().get("ErrorMsg").toString()).contains("가용재고 부족").contains("khả dụng 20");

        String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        NexacroData closed = call("/nexacro/disposal/cancelConfirm.do",
                params(Map.of("DISPOSAL_NO", "S001-" + yesterday + "-0001", "RSN", "x")), admin.cookie);
        assertThat(closed.getParams().get("ErrorCode")).isEqualTo(-4L);

        NexacroData badReason = call("/nexacro/disposal/save.do", data(ds("ds_master", Map.of("DISPOSAL_NO", "")),
                ds("ds_detail", Map.of("ITEM_CD", KIMBAP, "DISP_QTY", 1L, "REASON_CD", "77"))), admin.cookie);
        assertThat(badReason.getParams().get("ErrorCode")).isEqualTo(-1L);
    }

    // ---------------- helpers ----------------

    private record Session(String cookie, NexacroData data) {
    }

    private Session login(String user, String password) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> res = rest.exchange("/nexacro/common/login.do", HttpMethod.POST,
                new HttpEntity<>(NexacroXml.write(data(ds("ds_login", Map.of("USER_ID", user, "PASSWORD", password)))), h),
                String.class);
        String setCookie = res.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return new Session(setCookie == null ? null : setCookie.split(";")[0], NexacroXml.parse(res.getBody()));
    }

    private NexacroData call(String url, NexacroData in, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_XML);
        if (cookie != null) {
            h.add(HttpHeaders.COOKIE, cookie);
        }
        return NexacroXml.parse(rest.exchange(url, HttpMethod.POST,
                new HttpEntity<>(NexacroXml.write(in), h), String.class).getBody());
    }

    private static NexacroData data(NxDataset... datasets) {
        NexacroData d = new NexacroData();
        for (NxDataset ds : datasets) {
            d.addDataset(ds);
        }
        return d;
    }

    private static NexacroData param(String name, Object value) {
        return params(Map.of(name, value));
    }

    private static NexacroData params(Map<String, Object> values) {
        NexacroData d = new NexacroData();
        d.getParams().putAll(values);
        return d;
    }

    private static NxDataset ds(String id, Map<String, Object> row) {
        NxDataset ds = new NxDataset(id);
        row.forEach((k, v) -> ds.addColumn(k, v instanceof Number ? "int" : "string"));
        ds.addRow().putAll(row);
        return ds;
    }

    private static void insert(NxDataset ds, String item, long qty, String reason) {
        Map<String, Object> r = ds.addRow();
        r.put("ITEM_CD", item);
        r.put("DISP_QTY", qty);
        r.put("REASON_CD", reason);
        r.put(NxDataset.ROW_TYPE, "insert");
    }
}
