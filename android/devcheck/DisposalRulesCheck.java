import com.example.andemo.rules.DisposalRules;

/**
 * Test quy tắc hủy hàng bằng hàm main: không cần Gradle, JUnit, emulator hay internet.
 * Chỉ cần java + javac (Android Studio có sẵn trong thư mục jbr).
 *
 * Chạy (từ thư mục android/):
 *   Windows:  devcheck\run.bat
 *   Mac/Linux: sh devcheck/run.sh
 * Hướng dẫn: docs/nexacro-migration/TEST_WITH_MAIN.md
 *
 * Thêm test: viết thêm 1 dòng check(...) trong main.
 */
public class DisposalRulesCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // R10: xác nhận thêm 1 lần khi giá vốn >= 100.000원
        check("R10 99.999 không hỏi lần 2", false, DisposalRules.needsSecondConfirm(99_999));
        check("R10 100.000 hỏi lần 2 (biên)", true, DisposalRules.needsSecondConfirm(100_000));
        check("R10 0 không hỏi", false, DisposalRules.needsSecondConfirm(0));

        // R5: ghi chú tối đa 100 ký tự
        check("R5 null hợp lệ", null, DisposalRules.remarkError(null));
        check("R5 100 ký tự hợp lệ (biên)", null, DisposalRules.remarkError(repeat('a', 100)));
        check("R5 101 ký tự báo lỗi", "Tối đa 100 ký tự", DisposalRules.remarkError(repeat('a', 101)));
        check("R5 tiếng Hàn 100 ký tự hợp lệ", null, DisposalRules.remarkError(repeat('가', 100)));

        // R3: thiếu tồn
        check("R3 hủy 5, khả dụng 5: đủ", false, DisposalRules.isShortage(5, 5L));
        check("R3 hủy 6, khả dụng 5: thiếu", true, DisposalRules.isShortage(6, 5L));
        check("R3 không có trong tồn kho: thiếu", true, DisposalRules.isShortage(1, null));

        // R1: từ ngày <= đến ngày
        check("R1 cùng ngày", true, DisposalRules.validPeriod("2026-10-01", "2026-10-01"));
        check("R1 từ > đến", false, DisposalRules.validPeriod("2026-10-02", "2026-10-01"));
        check("R1 qua tháng", true, DisposalRules.validPeriod("2026-09-30", "2026-10-01"));
        check("R1 trống = không giới hạn", true, DisposalRules.validPeriod("", "2026-10-01"));

        // Hiển thị
        check("won 1234567", "1,234,567원", DisposalRules.won(1_234_567));
        check("won chuỗi", "1,000원", DisposalRules.won("1000"));
        check("won chuỗi rỗng giữ nguyên", "", DisposalRules.won(""));
        check("status CONFIRMED", "Đã xác nhận (확정)", DisposalRules.statusLabel("CONFIRMED"));
        check("status lạ giữ nguyên", "XYZ", DisposalRules.statusLabel("XYZ"));

        System.out.println();
        System.out.println("PASS " + passed + " / FAIL " + failed);
        if (failed > 0) {
            System.exit(1); // mã thoát khác 0: script / máy build biết là hỏng
        }
    }

    /** So sánh kết quả mong đợi với kết quả thật, in OK / FAIL */
    private static void check(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
            System.out.println("  OK    " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  mong đợi=" + expected + "  thực tế=" + actual);
        }
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
