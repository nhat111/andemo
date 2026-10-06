# Cell sửa được của Grid Nexacro → Android

Tình huống: grid Nexacro cho sửa thẳng trong ô (`<Cell edittype="normal" | "mask" ...>`),
còn màn Android dùng ListView với `TextView`, nên chưa sửa được.

Code mẫu (màn hủy hàng bản Java thuần, phiếu đang 등록):
- **Cách A**: `plain/PlainDisposalDetailActivity.java` + `PlainDisposalLineAdapter.java`: bấm 1 dòng → dialog.
- **Cách B**: `plain/PlainDisposalBulkEditActivity.java` + `PlainBulkQtyAdapter.java` (RecyclerView):
  nút "Sửa nhiều dòng" trên màn chi tiết → mỗi dòng 1 ô số, Next để sang dòng sau.

Có thể làm **cả hai**: A để sửa nhanh 1–2 dòng, B khi phải sửa nhiều dòng liên tục.

## Chọn cách nào

| | **A. Bấm dòng → dialog nhập (đã chọn)** | B. `EditText` trong từng dòng ListView |
|---|---|---|
| Thao tác | Bấm dòng → nhập số → OK (thêm 1 bước) | Bấm thẳng vào ô, giống grid |
| Độ ổn định | Ổn: dữ liệu chỉ đổi khi bấm OK | Dễ lỗi: ListView dùng lại view khi cuộn → mất focus, số nhảy sang dòng khác, bàn phím làm vẽ lại list |
| Kiểm tra nhập | 1 chỗ, trước khi ghi (1–9999, bắt buộc) | Phải kiểm tra theo từng ký tự gõ / khi mất focus |
| PDA (màn nhỏ, găng tay, bàn phím số) | Ô nhập to, chỉ 1 ô, bàn phím số tự bật | Ô nhỏ trong dòng, dễ bấm nhầm dòng, bàn phím che dòng đang sửa |
| Quét barcode | Không bị ảnh hưởng | Scanner "gõ" vào EditText đang focus → có thể ghi mã vạch vào ô số lượng |
| Công sức + rủi ro test | Thấp | Cao (nhiều máy / Android version cư xử khác nhau) |

**Đề xuất: A.** Chỉ chọn B khi người dùng thật sự phải sửa liên tục rất nhiều dòng (khi đó dùng
RecyclerView, không dùng ListView). Nếu bên Nexacro người dùng hay sửa nhiều ô một lượt: làm cả A và B.

Điểm cộng: bản Nexacro mẫu `frm_disposal_reg.xfdl` cũng sửa dòng bằng popup
(`grd_detail_oncellclick` → `div_line` → `btn_lineOk_onclick`), nên A là cùng cách làm.

## Luồng của cách A

```
Bấm dòng (onItemClick)                  ← chỉ khi server trả actions.edit = true
 └─ dialog: EditText số, chọn sẵn số cũ, bàn phím số
      └─ OK: kiểm tra 1..9999 (sai → báo lỗi, dialog không đóng)
           └─ adapter.setQty(position, qty)   ← như ds.setColumn(): ghi vào HashMap của dòng
                ├─ lưu số gốc (_orgQty)       ← như getOrgColumn()
                ├─ _rowType = "U"             ← như ROWTYPE_UPDATE (sửa về số cũ → hết "U")
                └─ notifyDataSetChanged → dòng nền vàng + "(sửa từ X, chưa lưu)"
           └─ hiện nút "Lưu", khóa nút "Xác nhận" (xác nhận phải theo số đã lưu)

Bấm "Lưu" → PUT /api/disposals/{no} {version, remark, items[]}
 ├─ 200 → render(response): version mới, thiếu tồn tính lại, hết dòng vàng
 ├─ 409 STALE_DATA (người khác vừa sửa) → báo, giữ dòng đã sửa, có nút "Tải lại"
 └─ mất mạng → báo, giữ dòng đã sửa để lưu lại

Back khi còn dòng chưa lưu → hỏi "Bỏ thay đổi và thoát?"   ← như canrowposchange return false
```

## Cách B: sửa nhiều dòng bằng RecyclerView

```
Màn chi tiết → "Sửa nhiều dòng" (chỉ khi actions.edit; còn dòng sửa bằng dialog chưa lưu thì bắt lưu trước)
 └─ PlainDisposalBulkEditActivity: GET phiếu → mỗi dòng 1 EditText số (bàn phím số, chọn sẵn số cũ)
      ├─ gõ số → afterTextChanged ghi ngay vào HashMap của dòng (putQty: _rowType "U", _orgQty)
      │          → chỉ đổi màu / lỗi / "thiếu tồn" của chính dòng đó (không notify → không mất focus)
      │          → cập nhật "đã sửa N dòng", bật nút Lưu
      ├─ Next trên bàn phím → cuộn + focus ô của dòng sau (Done ở dòng cuối đóng bàn phím)
      ├─ Lưu → kiểm tra mọi dòng (1..9999), sai thì cuộn tới dòng sai → PUT (tất cả dòng + version)
      │     ├─ 200 → RESULT_OK → màn chi tiết tải lại
      │     └─ lỗi / 409 → giữ số đã nhập, có "Tải lại"
      └─ Back / Đóng khi còn dòng chưa lưu → hỏi "Bỏ thay đổi và thoát?"
```

4 điểm bắt buộc (đã làm trong `PlainBulkQtyAdapter`), thiếu 1 điểm là lỗi ngay:

| Điểm | Không làm thì |
|---|---|
| Ghi giá trị vào HashMap trong `afterTextChanged`, không giữ trên view | Cuộn xong số biến mất / hiện ở dòng khác |
| 1 `TextWatcher` mỗi ViewHolder (gắn ở `onCreateViewHolder`) + cờ `binding` khi `setText` lúc bind | Watcher chồng nhau, số dòng mới ghi đè dòng cũ |
| Lấy vị trí bằng `getAdapterPosition()` lúc gõ | Ghi nhầm dòng sau khi list đổi |
| Khi gõ không gọi `notifyItemChanged` / `notifyDataSetChanged` | Mỗi lần gõ 1 số là mất focus, bàn phím đóng |

Thêm: activity để `windowSoftInputMode="adjustResize"` (bàn phím không che ô đang gõ).

Lưu ý với khách khi chọn B:
- Máy quét kiểu "keyboard wedge" gõ mã vạch vào ô đang focus. Màn B không có chức năng quét, nhưng
  nếu sau này thêm quét vào màn này thì phải nhận barcode qua Intent (DataWedge), không qua bàn phím.
- Test trên đúng model PDA của khách: bàn phím số / nút Next của từng hãng khác nhau.

## Đối chiếu Nexacro ↔ code

| Nexacro | Android (file mẫu) |
|---|---|
| `<Cell edittype="mask" ...>` / `Edit inputtype="number" maxlength="4"` | `InputType.TYPE_CLASS_NUMBER` + `LengthFilter(4)` trong `editQty()` |
| `ds_detail.setColumn(row, "DISP_QTY", qty)` | `lineAdapter.setQty(position, qty)` |
| `getOrgColumn` / `getRowType() == ROWTYPE_UPDATE` | `_orgQty` / `_rowType = "U"` trong HashMap của dòng |
| `oncolumnchanged` (kiểm tra) | Kiểm tra trong nút OK của dialog |
| transaction `ds_detail=ds_detail:U` | `save()`: PUT, gửi **tất cả** dòng (API thay toàn bộ dòng của phiếu) |
| Cảnh báo rời màn khi chưa lưu | `OnBackPressedCallback` |

## Giải thích cho khách (tiếng Hàn)

> 넥사크로 그리드는 셀에서 바로 수정(edittype)이 가능하지만, 안드로이드 ListView는 스크롤 시 행 화면을
> 재사용하기 때문에 행 안에 입력칸을 두면 포커스 유실, 다른 행에 값이 입력되는 문제가 생기기 쉽습니다.
> 그래서 **행을 터치하면 수량 입력 팝업을 띄우는 방식**을 제안드립니다.
>
> - 기존 넥사크로 등록 화면(div_line 팝업)과 같은 방식이라 사용자 혼란이 적습니다.
> - 입력칸이 크고 숫자 키패드가 바로 떠서 PDA(작은 화면, 장갑 착용)에서 오입력이 적습니다.
> - 확인 버튼에서 한 번에 검증(1~9999)하므로 잘못된 값이 저장되지 않습니다.
> - 바코드 스캔 값이 수량 칸에 잘못 들어가는 문제가 없습니다.
> - 수정된 행은 색상으로 표시하고, 저장 전에는 확정 버튼을 막습니다. 저장하지 않고 나가면 확인 메시지를 띄웁니다.
>
> 여러 행을 연속으로 빠르게 수정해야 하는 업무라면 **"여러 행 수정" 화면**을 별도로 제공할 수 있습니다.
> 각 행에 수량 입력칸이 있고, 키패드의 "다음" 버튼으로 다음 행으로 바로 이동합니다(그리드 연속 입력과 유사).
> 다만 기종별 키패드 동작 차이가 있어 실제 PDA 기종에서 테스트가 필요하고, 개발·테스트 공수가 더 듭니다.
> 1~2개 행 수정은 팝업 방식, 여러 행 일괄 수정은 별도 화면 방식으로 함께 제공하는 것을 권장드립니다.
