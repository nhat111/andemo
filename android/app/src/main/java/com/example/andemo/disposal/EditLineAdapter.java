package com.example.andemo.disposal;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;

import java.util.List;

/** Các dòng đang soạn. Chạm: sửa; giữ lâu: xóa. Dùng lại layout item_disposal_line. */
class EditLineAdapter extends RecyclerView.Adapter<EditLineAdapter.ViewHolder> {

    interface Listener {
        void onClick(int position);

        void onLongClick(int position);
    }

    private final List<EditLine> items;
    private final Listener listener;

    EditLineAdapter(List<EditLine> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_disposal_line, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        EditLine line = items.get(position);
        h.txtName.setText((position + 1) + ". " + line.itemName);
        h.txtCode.setText(line.itemCode + " · " + line.reasonLabel);
        String qty = "Hủy " + line.qty + " · giá vốn " + DisposalUi.won(line.qty * line.costPrice);
        boolean over = line.availableQty != null && line.qty > line.availableQty;
        if (line.availableQty != null) {
            qty += " · khả dụng " + line.availableQty;
        }
        h.txtQty.setText(qty);
        // Chỉ cảnh báo: vẫn cho lưu, server chặn lúc 확정 nếu còn thiếu
        h.txtQty.setTextColor(over ? Color.RED : Color.DKGRAY);
        h.itemView.setOnClickListener(v -> listener.onClick(h.getAdapterPosition()));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(h.getAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtName, txtCode, txtQty;

        ViewHolder(View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtCode = v.findViewById(R.id.txtCode);
            txtQty = v.findViewById(R.id.txtQty);
        }
    }
}
