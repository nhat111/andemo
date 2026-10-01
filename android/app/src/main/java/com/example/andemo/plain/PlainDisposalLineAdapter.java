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

    public PlainDisposalLineAdapter(Context context, List<HashMap<String, String>> rows,
                                    OnRowButtonListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
        this.listener = listener;
    }

    public void setCheckStock(boolean checkStock) {
        this.checkStock = checkStock;
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

        boolean shortage = checkStock && !"true".equals(r.get("sufficient"));
        h.txtQty.setText("Hủy " + r.get("qty") + " · khả dụng " + r.get("availableQty")
                + (shortage ? "  ⚠ thiếu tồn" : ""));
        h.txtQty.setTextColor(shortage ? Color.RED : Color.DKGRAY); // gán cả 2 nhánh (view dùng lại)

        // Nút trong dòng: ghi vị trí hiện tại vào tag (view dùng lại nên vị trí đổi theo lần gán)
        h.btnStock.setTag(position);
        return convertView;
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
