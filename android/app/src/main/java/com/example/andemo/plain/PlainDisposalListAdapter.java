package com.example.andemo.plain;

import android.content.Context;
import android.graphics.Color;

import com.example.andemo.R;

import java.util.HashMap;
import java.util.List;

/**
 * Adapter danh sách phiếu, dựng trên khung HashMapListAdapter: chỉ còn chọn layout dòng + gán dữ liệu.
 * (Bản viết tay đủ mọi bước của BaseAdapter: PlainDisposalLineAdapter.)
 *
 * Bản RecyclerView tương ứng: disposal/DisposalListAdapter.
 */
public class PlainDisposalListAdapter extends HashMapListAdapter {

    public PlainDisposalListAdapter(Context context, List<HashMap<String, String>> rows) {
        super(context, rows, R.layout.item_plain_disposal);
    }

    @Override
    protected void bind(RowViews v, HashMap<String, String> r, int position) {
        String status = r.get("status");
        v.text(R.id.txtNo).setText(r.get("disposalNo"));
        v.text(R.id.txtStatus).setText(JsonRows.statusLabel(status));
        v.text(R.id.txtStatus).setTextColor(statusColor(status));
        v.text(R.id.txtSummary).setText(r.get("lineCount") + " mặt hàng · SL " + r.get("totalQty")
                + " · giá vốn " + JsonRows.won(r.get("totalCostAmount")));
        v.text(R.id.txtMeta).setText("영업일자 " + r.get("businessDate") + " · " + r.get("registeredBy"));

        // View được dùng lại → nhánh nào cũng phải gán lại (không thì dòng khác bị "dính" màu cũ)
        v.text(R.id.txtNo).setTextColor("CANCELLED".equals(status) ? Color.GRAY : Color.BLACK);
    }

    static int statusColor(String status) {
        if ("REGISTERED".equals(status)) return Color.rgb(0xE6, 0x51, 0x00);  // cam
        if ("CONFIRMED".equals(status)) return Color.rgb(0x2E, 0x7D, 0x32);   // xanh lá
        return Color.GRAY;
    }
}
