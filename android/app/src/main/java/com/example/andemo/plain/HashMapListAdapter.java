package com.example.andemo.plain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;

/**
 * Khung adapter dùng chung cho ListView với dữ liệu List&lt;HashMap&lt;String,String&gt;&gt;.
 * Các bước dễ quên (getCount / getItem / getItemId, convertView, ViewHolder, inflate đúng tham số)
 * đã làm sẵn ở đây. Adapter mới chỉ cần 2 bước:
 *
 * <pre>
 * public class XxxAdapter extends HashMapListAdapter {
 *     public XxxAdapter(Context c, List&lt;HashMap&lt;String, String&gt;&gt; rows) {
 *         super(c, rows, R.layout.item_xxx);                 // 1) layout của 1 dòng
 *     }
 *     protected void bind(RowViews v, HashMap&lt;String, String&gt; row, int position) {
 *         v.text(R.id.txtName).setText(row.get("name"));    // 2) gán dữ liệu vào view
 *     }
 * }
 * </pre>
 *
 * Ví dụ dùng: PlainDisposalListAdapter. Bản viết tay đủ mọi bước (để hiểu bên trong): PlainDisposalLineAdapter.
 */
public abstract class HashMapListAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<HashMap<String, String>> rows;
    private final int layoutId;

    protected HashMapListAdapter(Context context, List<HashMap<String, String>> rows, int layoutId) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
        this.layoutId = layoutId;
    }

    /** Gán dữ liệu của 1 dòng. Nhớ: view được dùng lại, nhánh if nào cũng phải gán lại (màu, ẩn / hiện). */
    protected abstract void bind(RowViews v, HashMap<String, String> row, int position);

    /** Gọi 1 lần khi tạo view mới cho 1 dòng: chỗ gắn listener cho nút trong dòng. Mặc định không làm gì. */
    protected void onCreateRow(RowViews v) {
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

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        RowViews v;
        if (convertView == null) {
            convertView = inflater.inflate(layoutId, parent, false);
            v = new RowViews(convertView);
            convertView.setTag(v);
            onCreateRow(v);
        } else {
            v = (RowViews) convertView.getTag();
        }
        bind(v, rows.get(position), position);
        return convertView;
    }

    /**
     * ViewHolder chung: cache view của 1 dòng theo id (HashMap), chỉ findViewById lần đầu.
     * Id không có trong layout dòng → báo lỗi rõ ràng thay vì NullPointerException khó tìm.
     */
    public static class RowViews {
        private final View root;
        private final HashMap<Integer, View> cache = new HashMap<>();

        RowViews(View root) {
            this.root = root;
        }

        public View view(int id) {
            View v = cache.get(id);
            if (v == null) {
                v = root.findViewById(id);
                if (v == null) {
                    throw new IllegalStateException("Layout dòng không có view id "
                            + root.getResources().getResourceEntryName(id));
                }
                cache.put(id, v);
            }
            return v;
        }

        public TextView text(int id) {
            return (TextView) view(id);
        }

        public View root() {
            return root;
        }
    }
}
