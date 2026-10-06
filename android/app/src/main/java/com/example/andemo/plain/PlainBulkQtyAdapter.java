package com.example.andemo.plain;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.rules.DisposalRules;

import java.util.HashMap;
import java.util.List;

/**
 * RecyclerView cho màn "sửa nhiều dòng": mỗi dòng có 1 EditText số lượng, sửa liên tục như ô grid
 * Nexacro (edittype="normal"). Dữ liệu vẫn là List&lt;HashMap&gt; như các màn Java thuần khác.
 *
 * 4 điểm phải đúng để EditText trong list không lỗi:
 * 1. Giá trị ghi NGAY vào HashMap của dòng (afterTextChanged), không giữ trên view: view bị dùng
 *    lại khi cuộn.
 * 2. Mỗi ViewHolder có 1 TextWatcher, gắn 1 lần ở onCreateViewHolder. Lúc bind thì bật cờ
 *    {@code binding} để setText không bị coi là người dùng gõ (nếu không: số dòng mới ghi đè dòng cũ).
 * 3. Vị trí lấy bằng getAdapterPosition() lúc gõ, không dùng position của onBind (đã cũ).
 * 4. Khi gõ chỉ cập nhật màu / lỗi của chính dòng đó, KHÔNG notifyItemChanged / notifyDataSetChanged:
 *    vẽ lại dòng làm EditText mất focus, bàn phím đóng.
 */
public class PlainBulkQtyAdapter extends RecyclerView.Adapter<PlainBulkQtyAdapter.Holder> {

    public static final long MAX_QTY = 9_999; // = MAX_QTY_PER_LINE của server

    /** Activity cài để chuyển focus sang dòng sau khi bấm Next trên bàn phím */
    public interface Listener {
        void onNext(int position);

        /** Có dòng vừa đổi số: cập nhật nút Lưu / tổng */
        void onChanged();
    }

    private final List<HashMap<String, String>> rows;
    private final Listener listener;

    public PlainBulkQtyAdapter(List<HashMap<String, String>> rows, Listener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    /** Lỗi nhập của 1 dòng, null nếu hợp lệ (dùng cả khi gõ và khi bấm Lưu) */
    public static String qtyError(String text) {
        if (text == null || text.isEmpty()) {
            return "Nhập số lượng";
        }
        long qty;
        try {
            qty = Long.parseLong(text);
        } catch (NumberFormatException e) {
            return "Không phải số";
        }
        return qty < 1 || qty > MAX_QTY ? "Từ 1 đến " + MAX_QTY : null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plain_line_edit, parent, false);
        Holder h = new Holder(view);

        h.edtQty.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                int position = h.getAdapterPosition();
                if (h.binding || position == RecyclerView.NO_POSITION) {
                    return; // setText lúc bind, hoặc dòng vừa bị gỡ khỏi list
                }
                HashMap<String, String> row = rows.get(position);
                PlainDisposalLineAdapter.putQty(row, s.toString().trim());
                h.showState(row); // chỉ dòng này, không notify (giữ focus + bàn phím)
                listener.onChanged();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // Next trên bàn phím số: nhảy xuống ô của dòng sau (kể cả dòng đang khuất màn hình)
        h.edtQty.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                listener.onNext(h.getAdapterPosition());
                return true;
            }
            return false; // Done ở dòng cuối: để hệ thống đóng bàn phím
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        HashMap<String, String> row = rows.get(position);
        h.txtName.setText(row.get("lineNo") + ". " + row.get("itemName"));
        h.txtInfo.setText(row.get("itemCode") + " · " + row.get("reasonName")
                + " · khả dụng " + row.get("availableQty"));

        h.binding = true;
        h.edtQty.setText(row.get("qty"));
        h.binding = false;
        // Dòng cuối: nút Done (đóng bàn phím); các dòng khác: Next
        h.edtQty.setImeOptions(position == rows.size() - 1 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        h.showState(row);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView txtName;
        final TextView txtInfo;
        final TextView txtState;
        final EditText edtQty;
        /** true trong lúc onBindViewHolder gọi setText */
        boolean binding;

        Holder(View view) {
            super(view);
            txtName = view.findViewById(R.id.txtName);
            txtInfo = view.findViewById(R.id.txtInfo);
            txtState = view.findViewById(R.id.txtState);
            edtQty = view.findViewById(R.id.edtQty);
        }

        /** Màu nền, lỗi nhập, thiếu tồn của 1 dòng theo dữ liệu trong HashMap (gán đủ mọi nhánh) */
        void showState(HashMap<String, String> row) {
            String qtyText = row.get("qty");
            String error = qtyError(qtyText);
            boolean modified = PlainDisposalLineAdapter.isModified(row);

            edtQty.setError(error);
            itemView.setBackgroundColor(modified ? 0xFFFFF8E1 : Color.TRANSPARENT);

            if (error == null && DisposalRules.isShortage(Long.parseLong(qtyText), parseLongOrNull(row.get("availableQty")))) {
                txtState.setText("⚠ thiếu tồn");
                txtState.setTextColor(Color.RED);
                txtState.setVisibility(View.VISIBLE);
            } else if (modified) {
                txtState.setText("sửa từ " + PlainDisposalLineAdapter.orgQty(row) + ", chưa lưu");
                txtState.setTextColor(Color.DKGRAY);
                txtState.setVisibility(View.VISIBLE);
            } else {
                txtState.setVisibility(View.GONE);
            }
        }

        private static Long parseLongOrNull(String value) {
            try {
                return value == null ? null : Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
