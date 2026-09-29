package com.example.andemo.disposal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.model.DisposalSummaryDto;

import java.util.ArrayList;
import java.util.List;

/** Danh sách phiếu hủy (Grid của màn inquiry). */
class DisposalListAdapter extends RecyclerView.Adapter<DisposalListAdapter.ViewHolder> {

    interface OnClick {
        void onClick(DisposalSummaryDto disposal);
    }

    private final List<DisposalSummaryDto> items = new ArrayList<>();
    private final OnClick onClick;

    DisposalListAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    void submit(List<DisposalSummaryDto> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disposal, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        DisposalSummaryDto d = items.get(position);
        h.txtNo.setText(d.getDisposalNo());
        h.txtStatus.setText(DisposalUi.statusLabel(d.getStatus()));
        h.txtStatus.setTextColor(DisposalUi.statusColor(d.getStatus()));
        h.txtReason.setText(d.getReason());
        h.txtMeta.setText(d.getLineCount() + " mặt hàng · SL " + d.getTotalQty() + " · "
                + d.getRequestedBy() + " · " + DisposalUi.formatTime(d.getRequestedAt()));
        h.itemView.setOnClickListener(v -> onClick.onClick(d));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtNo, txtStatus, txtReason, txtMeta;

        ViewHolder(View v) {
            super(v);
            txtNo = v.findViewById(R.id.txtNo);
            txtStatus = v.findViewById(R.id.txtStatus);
            txtReason = v.findViewById(R.id.txtReason);
            txtMeta = v.findViewById(R.id.txtMeta);
        }
    }
}
