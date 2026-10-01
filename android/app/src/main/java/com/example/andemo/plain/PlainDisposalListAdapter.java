package com.example.andemo.plain;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.andemo.R;

import java.util.HashMap;
import java.util.List;

/**
 * Adapter tự viết cho ListView danh sách phiếu (thay SimpleAdapter khi cần tô màu, ẩn / hiện theo dữ liệu).
 * Dữ liệu: List&lt;HashMap&gt; do Activity giữ; Activity sửa list rồi gọi notifyDataSetChanged().
 *
 * Bản RecyclerView tương ứng: disposal/DisposalListAdapter.
 */
public class PlainDisposalListAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<HashMap<String, String>> rows;

    public PlainDisposalListAdapter(Context context, List<HashMap<String, String>> rows) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
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

    /** Giữ sẵn các TextView của 1 dòng: tránh findViewById mỗi lần cuộn */
    private static class ViewHolder {
        TextView txtNo;
        TextView txtStatus;
        TextView txtSummary;
        TextView txtMeta;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            // Lần đầu: tạo view mới từ XML, tìm các TextView 1 lần, cất vào tag
            convertView = inflater.inflate(R.layout.item_plain_disposal, parent, false);
            h = new ViewHolder();
            h.txtNo = convertView.findViewById(R.id.txtNo);
            h.txtStatus = convertView.findViewById(R.id.txtStatus);
            h.txtSummary = convertView.findViewById(R.id.txtSummary);
            h.txtMeta = convertView.findViewById(R.id.txtMeta);
            convertView.setTag(h);
        } else {
            // View cũ cuộn khỏi màn hình được đưa lại: dùng lại, chỉ gán dữ liệu mới
            h = (ViewHolder) convertView.getTag();
        }

        HashMap<String, String> r = rows.get(position);
        String status = r.get("status");
        h.txtNo.setText(r.get("disposalNo"));
        h.txtStatus.setText(JsonRows.statusLabel(status));
        h.txtStatus.setTextColor(statusColor(status));
        h.txtSummary.setText(r.get("lineCount") + " mặt hàng · SL " + r.get("totalQty")
                + " · giá vốn " + JsonRows.won(r.get("totalCostAmount")));
        h.txtMeta.setText("영업일자 " + r.get("businessDate") + " · " + r.get("registeredBy"));

        // View được dùng lại → nhánh nào cũng phải gán lại (không thì dòng khác bị "dính" màu cũ)
        h.txtNo.setTextColor("CANCELLED".equals(status) ? Color.GRAY : Color.BLACK);
        return convertView;
    }

    static int statusColor(String status) {
        if ("REGISTERED".equals(status)) return Color.rgb(0xE6, 0x51, 0x00);  // cam
        if ("CONFIRMED".equals(status)) return Color.rgb(0x2E, 0x7D, 0x32);   // xanh lá
        return Color.GRAY;
    }
}
