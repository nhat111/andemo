package com.example.andemo.disposal;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.model.DisposalDetailDto;

import java.util.ArrayList;
import java.util.List;

/** Các dòng mặt hàng của 1 phiếu hủy. Dòng thiếu tồn khả dụng tô đỏ. */
class DisposalLineAdapter extends RecyclerView.Adapter<DisposalLineAdapter.ViewHolder> {

    private final List<DisposalDetailDto.Line> items = new ArrayList<>();
    /** Chỉ tô đỏ khi phiếu còn chờ xác nhận; phiếu đã xác nhận thì tồn hiện tại không còn ý nghĩa so sánh */
    private boolean highlightShortage;

    void submit(List<DisposalDetailDto.Line> data, boolean highlightShortage) {
        this.highlightShortage = highlightShortage;
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_disposal_line, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        DisposalDetailDto.Line line = items.get(position);
        h.txtName.setText(line.getLineNo() + ". " + (line.getItemName() == null ? "(không có trong tồn kho)" : line.getItemName()));
        h.txtCode.setText(line.getItemCode() + " · Lý do: " + line.getReasonCode());

        String qty = "Hủy " + line.getQty();
        if (line.getAvailableQty() != null) {
            qty += "   ·   Khả dụng " + line.getAvailableQty() + " (tồn " + line.getOnHandQty() + ")";
        }
        h.txtQty.setText(qty);
        boolean shortage = highlightShortage && !line.isSufficient();
        h.txtQty.setTextColor(shortage ? Color.RED : Color.DKGRAY);
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
