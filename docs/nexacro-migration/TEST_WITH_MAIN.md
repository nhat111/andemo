# Test bằng hàm `main` (khi Gradle không tải được thư viện)

Trong VDI của khách: không có internet → Gradle không tải được thư viện → project đỏ, JUnit không chạy, không có emulator.
Nhưng **JDK vẫn có sẵn** (Android Studio có kèm trong thư mục `jbr`). `javac` + `java` không cần Gradle, không cần internet.
Nên: phần logic nào **viết bằng Java thuần** thì test được bằng 1 hàm `main`.

## 1. Ý tưởng

```
Activity (Android, cần emulator)          Lớp quy tắc (Java thuần)            File check (có main)
DisposalDetailActivity ─── gọi ───▶  rules/DisposalRules.java  ◀── gọi ─── devcheck/DisposalRulesCheck.java
  askConfirm():                         needsSecondConfirm(cost)            check("R10 biên", true,
  if (DisposalRules                     remarkError(remark)                       needsSecondConfirm(100_000));
      .needsSecondConfirm(cost)) ...    isShortage / validPeriod / won
```

- Activity **không tự tính**, chỉ gọi `DisposalRules`. Logic test bằng `main` chính là logic app chạy.
- `DisposalRules` chỉ được import `java.*` (không `android.*`, `org.json`, Retrofit, Gson). Không dùng `java.time` (minSdk 24).
- `devcheck/` nằm **ngoài** `app/src`, Gradle không biên dịch nó → không ảnh hưởng build APK.

## 2. Chạy

### Bước 1: tìm java của Android Studio

| Máy | Đường dẫn thường gặp |
|---|---|
| Windows | `C:\Program Files\Android\Android Studio\jbr\bin\java.exe` |
| Mac | `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java` |

Không thấy thì: Android Studio → *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*, xem đường dẫn của JDK đang chọn.
Kiểm tra: mở cmd, gõ `"C:\Program Files\Android\Android Studio\jbr\bin\java" -version` (cần bản 11 trở lên; Android Studio mới kèm 17 hoặc 21).

### Bước 2: chạy script

Mở **Terminal** trong Android Studio (tab dưới cùng) hoặc cmd, `cd` vào thư mục `android`:

```bat
devcheck\run.bat
```

Mac / Linux: `sh devcheck/run.sh`.

`run.bat` tự dùng `C:\Program Files\Android\Android Studio\jbr` nếu chưa có `JAVA_HOME`. Cài chỗ khác thì trước khi chạy:

```bat
set "JAVA_HOME=D:\Tools\Android Studio\jbr"
devcheck\run.bat
```

`run.bat` chạy 2 phần: **1. `ProjectCheck`** soát manifest / layout / id (xem [CHECKLISTS.md](CHECKLISTS.md)), **2. `DisposalRulesCheck`** test quy tắc (mục này).

### Bước 3: đọc kết quả

```
  OK    R10 99.999 không hỏi lần 2
  OK    R10 100.000 hỏi lần 2 (biên)
  ...
  FAIL  R5 101 ký tự báo lỗi  mong đợi=Tối đa 100 ký tự  thực tế=null

PASS 18 / FAIL 1
```

Có `FAIL` thì mã thoát = 1 (`echo %errorlevel%`), máy build / script khác biết là hỏng.

### Script làm gì (tự gõ tay cũng được)

```bat
javac -encoding UTF-8 -d build\devcheck ^
  app\src\main\java\com\example\andemo\rules\DisposalRules.java ^
  devcheck\DisposalRulesCheck.java
java -cp build\devcheck DisposalRulesCheck
```

1. `javac` biên dịch **đúng 2 file** (lớp quy tắc + file check) ra `build\devcheck`. Không đụng file nào khác trong project, nên các file Android đang "đỏ" không ảnh hưởng.
2. `java -cp build\devcheck DisposalRulesCheck` chạy hàm `main`.
3. `-encoding UTF-8`: mã nguồn có tiếng Việt / Hàn. `chcp 65001` trong `run.bat`: cmd hiện được Unicode (vẫn thấy `?` thì đổi font cmd sang *Consolas*, hoặc chạy trong Terminal của Android Studio).

## 3. Thêm 1 test

Mở `devcheck/DisposalRulesCheck.java`, thêm 1 dòng trong `main`:

```java
check("tên test", giáTrịMongĐợi, DisposalRules.hàmCầnTest(thamSố));
```

Ví dụ thêm quy tắc mới "SL hủy phải > 0":

1. Thêm hàm vào `DisposalRules`:
   ```java
   public static boolean validQty(long qty) { return qty > 0; }
   ```
2. Thêm test:
   ```java
   check("SL 0 không hợp lệ", false, DisposalRules.validQty(0));
   check("SL 1 hợp lệ", true, DisposalRules.validQty(1));
   ```
3. `devcheck\run.bat` → thấy PASS.
4. Gọi `DisposalRules.validQty(...)` trong Activity.
5. Lớp quy tắc thêm file mới (ví dụ `rules/PriceRules.java`)? Thêm đường dẫn file đó vào dòng `javac` trong `run.bat` / `run.sh`.

Nên test **biên**: 99.999 / 100.000, 100 / 101 ký tự, cùng ngày, `null`, chuỗi rỗng.

## 4. Thử nhanh 1 đoạn code (không cần script)

Từ Java 11, chạy thẳng 1 file `.java` không cần `javac`. Hợp để thử 1 hàm format / tính toán trước khi đưa vào project:

```java
// D:\scratch\Try.java
public class Try {
    public static void main(String[] args) {
        System.out.println(String.format(java.util.Locale.US, "%,d원", 1234567));
        System.out.println("2026-09-30".compareTo("2026-10-01") <= 0);
    }
}
```

```bat
"C:\Program Files\Android\Android Studio\jbr\bin\java" D:\scratch\Try.java
```

Giới hạn: chỉ 1 file; muốn dùng lớp khác của project thì quay về cách ở mục 2.

## 5. Cái gì test được, cái gì không

| Test được bằng main | Không test được bằng main (cần emulator / máy thật) |
|---|---|
| Quy tắc nghiệp vụ: R1, R3, R5, R10, tính tiền, làm tròn | Giao diện: layout, màu, ẩn / hiện nút, cuộn ListView |
| Format: tiền `원`, ngày, nhãn trạng thái | Vòng đời Activity, Intent, dialog |
| Kiểm tra dữ liệu nhập (barcode đúng độ dài, check digit…) | Gọi API thật (`HttpURLConnection` chạy được trên JVM nhưng cần server, và `HttpTask` dùng `Handler` của Android) |
| Ghép URL / tham số query | Code dùng `org.json` (trên JVM không có thư viện này, trừ khi có file jar) |

Vì vậy khi viết code mới, **tách phần tính toán / kiểm tra ra lớp Java thuần** ngay từ đầu, Activity chỉ lấy dữ liệu, gọi lớp đó, rồi hiển thị.
Phần không test được bằng main: build APK ở máy remote rồi cài lên máy thật / PDA để thử.

## 6. Nếu có JUnit (máy remote hoặc sau khi có repo nội bộ)

Hàm `check(...)` tương đương `assertEquals(expected, actual)`. Khi Gradle tải được thư viện, chuyển sang JUnit rất dễ:

```java
// app/src/test/java/com/example/andemo/rules/DisposalRulesTest.java
@Test
public void r10_bien() {
    assertTrue(DisposalRules.needsSecondConfirm(100_000));
    assertFalse(DisposalRules.needsSecondConfirm(99_999));
}
```

`DisposalRules` không đổi gì: lớp Java thuần chạy được cả với `main` lẫn JUnit.
