# Cell sửa được của Grid Nexacro → Android

Tình huống: grid Nexacro cho sửa thẳng trong ô (`<Cell edittype="normal" | "mask" ...>`),
còn màn Android dùng ListView với `TextView`, nên chưa sửa được.

Code mẫu: `android/.../plain/PlainDisposalDetailActivity.java` + `PlainDisposalLineAdapter.java`
(màn chi tiết phiếu hủy, phiếu đang 등록 thì bấm 1 dòng để sửa số lượng).

## Chọn cách nào

| | **A. Bấm dòng → dialog nhập (đã chọn)** | B. `EditText` trong từng dòng ListView |
|---|---|---|
| Thao tác | Bấm dòng → nhập số → OK (thêm 1 bước) | Bấm thẳng vào ô, giống grid |
| Độ ổn định | Ổn: dữ liệu chỉ đổi khi bấm OK | Dễ lỗi: ListView dùng lại view khi cuộn → mất focus, số nhảy sang dòng khác, bàn phím làm vẽ lại list |
| Kiểm tra nhập | 1 chỗ, trước khi ghi (1–9999, bắt buộc) | Phải kiểm tra theo từng ký tự gõ / khi mất focus |
| PDA (màn nhỏ, găng tay, bàn phím số) | Ô nhập to, chỉ 1 ô, bàn phím số tự bật | Ô nhỏ trong dòng, dễ bấm nhầm dòng, bàn phím che dòng đang sửa |
| Quét barcode | Không bị ảnh hưởng | Scanner "gõ" vào EditText đang focus → có thể ghi mã vạch vào ô số lượng |
| Công sức + rủi ro test | Thấp | Cao (nhiều máy / Android version cư xử khác nhau) |

**Đề xuất: A.** Chỉ chọn B khi người dùng thật sự phải sửa liên tục rất nhiều dòng (khi đó nên
dùng RecyclerView, không dùng ListView).

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
> 여러 행을 연속으로 빠르게 수정해야 하는 업무라면 행 내 직접 입력 방식도 가능하지만, 개발·테스트 공수가 더 듭니다.
