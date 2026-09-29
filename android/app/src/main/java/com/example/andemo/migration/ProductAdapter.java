package com.example.andemo.migration;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.model.ProductDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid grd_list (nexacro-sample/frm_product_search.xfdl) → RecyclerView.Adapter.
 *
 * - items           ~ Dataset ds_list
 * - onBindViewHolder ~ Band body: gán giá trị cột vào từng Cell
 * - OnItemClick      ~ sự kiện oncellclick
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(ProductDto product);
    }

    private final List<ProductDto> items = new ArrayList<>();
    private final OnItemClick onItemClick;

    public ProductAdapter(OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
    }

    /** Nexacro: server trả ds_list mới → Grid tự vẽ lại. Android: phải báo Adapter. */
    public void submit(List<ProductDto> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductDto p = items.get(position);                        // ~ ds_list.getColumn(position, ...)
        holder.txtName.setText(p.getName());
        holder.txtBarcode.setText(p.getBarcode());
        holder.txtStock.setText("Tồn: " + p.getStockQuantity());
        // ~ color="expr:stockQuantity == 0 ? 'red' : 'black'"
        // Phải gán cả 2 nhánh: ViewHolder được tái sử dụng cho dòng khác khi cuộn
        holder.txtStock.setTextColor(p.getStockQuantity() == 0 ? Color.RED : Color.DKGRAY);
        holder.itemView.setOnClickListener(v -> onItemClick.onClick(p));
    }

    @Override
    public int getItemCount() {                                     // ~ ds_list.getRowCount()
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtName;
        final TextView txtBarcode;
        final TextView txtStock;

        ViewHolder(View view) {
            super(view);
            txtName = view.findViewById(R.id.txtName);
            txtBarcode = view.findViewById(R.id.txtBarcode);
            txtStock = view.findViewById(R.id.txtStock);
        }
    }
}
