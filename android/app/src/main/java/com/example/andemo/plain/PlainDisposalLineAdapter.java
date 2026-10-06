package com.example.andemo.plain;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.example.andemo.R;
import com.example.andemo.rules.DisposalRules;

import java.util.HashMap;
import java.util.List;

/**
 * Adapter tự viết cho dòng hàng trong màn chi tiết. Mỗi dòng có 1 nút "Tồn" (nút nằm TRONG dòng):
 * adapter không tự xử lý mà báo ngược về Activity qua interface OnRowButtonListener.
 *
 * Bản RecyclerView tương ứng: disposal/DisposalLineAdapter.
 */
public class PlainDisposalLineAdapter extends BaseAdapter implements View.OnClickListener {

    /** Activity cài interface này để biết dòng nào được bấm nút */
    public interface OnRowButtonListener {
        void onStockClick(int position, HashMap<String, String> row);
    }

    private final LayoutInflater inflater;
    private final List<HashMap<String, String>> rows;
    private final OnRowButtonListener listener;
    /** true khi phiếu đang ở trạng thái đăng ký: chỉ lúc đó mới cảnh báo thiếu tồn */
    private boolean checkStock;

    // Trạng thái dòng, giống getRowType() của Dataset Nexacro: "" = như server trả về, "U" = đã sửa.
    // Lưu ngay trong HashMap của dòng (không lưu trên view: view được dùng lại khi cuộn).
    public static final String COL_ROW_TYPE = "_rowType";
    public static final String ROW_TYPE_UPDATE = "U";
    /** Giá trị gốc từ server, giống getOrgColumn(): sửa về đúng số cũ thì dòng hết "đã sửa" */
    private static final String COL_ORG_QTY = "_orgQty";

    public PlainDisposalLineAdapter(Context context, List<HashMap<String, String>> rows,
                                    OnRowButtonListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
        this.listener = listener;
    }

    public void setCheckStock(boolean checkStock) {
        this.checkStock = checkStock;
    }

    /** Như ds.setColumn(row, "DISP_QTY", qty): đổi số lượng 1 dòng và đánh dấu dòng đã sửa. */
    public void setQty(int position, long qty) {
        HashMap<String, String> r = rows.get(position);
        if (!r.containsKey(COL_ORG_QTY)) {
            r.put(COL_ORG_QTY, r.get("qty"));
        }
        String value = String.valueOf(qty);
        r.put("qty", value);
        r.put(COL_ROW_TYPE, value.equals(r.get(COL_ORG_QTY)) ? "" : ROW_TYPE_UPDATE);
        notifyDataSetChanged();
    }

    public static boolean isModified(HashMap<String, String> row) {
        return ROW_TYPE_UPDATE.equals(row.get(COL_ROW_TYPE));
    }

    /** Còn dòng sửa chưa lưu không (giống kiểm tra rowtype trước khi rời màn hình) */
    public boolean hasModified() {
        for (HashMap<String, String> r : rows) {
            if (isModified(r)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public HashMap<String, String> getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        TextView txtName;
        TextView txtQty;
        TextView txtCode;
        Button btnStock;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_plain_line, parent, false);
            h = new ViewHolder();
            h.txtName = convertView.findViewById(R.id.txtName);
            h.txtQty = convertView.findViewById(R.id.txtQty);
            h.txtCode = convertView.findViewById(R.id.txtCode);
            h.btnStock = convertView.findViewById(R.id.btnStock);
            h.btnStock.setOnClickListener(this); // gắn 1 lần khi tạo view, không gắn lại mỗi lần cuộn
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        HashMap<String, String> r = rows.get(position);
        String name = r.get("itemName");
        h.txtName.setText(r.get("lineNo") + ". " + (name == null || name.isEmpty() ? "(không có trong tồn kho)" : name));
        h.txtCode.setText(r.get("itemCode") + " · " + r.get("reasonName")
                + " · giá vốn " + JsonRows.won(r.get("costAmount")));

        boolean modified = isModified(r);
        boolean shortage;
        if (modified) {
            // Dòng đã sửa: "sufficient" của server là theo số cũ → tự kiểm tra theo số mới (R3)
            shortage = checkStock && DisposalRules.isShortage(parseLong(r.get("qty")), parseLongOrNull(r.get("availableQty")));
        } else {
            shortage = checkStock && !"true".equals(r.get("sufficient")); // server đã tính R3 (DisposalRules.isShortage)
        }
        h.txtQty.setText("Hủy " + r.get("qty") + " · khả dụng " + r.get("availableQty")
                + (shortage ? "  ⚠ thiếu tồn" : "")
                + (modified ? "  (sửa từ " + r.get(COL_ORG_QTY) + ", chưa lưu)" : ""));
        h.txtQty.setTextColor(shortage ? Color.RED : Color.DKGRAY); // gán cả 2 nhánh (view dùng lại)
        convertView.setBackgroundColor(modified ? 0xFFFFF8E1 : Color.TRANSPARENT); // dòng đã sửa: nền vàng nhạt

        // Nút trong dòng: ghi vị trí hiện tại vào tag (view dùng lại nên vị trí đổi theo lần gán)
        h.btnStock.setTag(position);
        return convertView;
    }

    private static long parseLong(String value) {
        Long parsed = parseLongOrNull(value);
        return parsed == null ? 0 : parsed;
    }

    private static Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nút "Tồn" của dòng nào cũng vào đây; lấy vị trí từ tag */
    @Override
    public void onClick(View v) {
        int position = (Integer) v.getTag();
        if (listener != null && position < rows.size()) {
            listener.onStockClick(position, rows.get(position));
        }
    }
}
