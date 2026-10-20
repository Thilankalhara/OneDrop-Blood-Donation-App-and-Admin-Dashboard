package com.example.onedrop.Activity.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.onedrop.R;

public class QuoteAdapter extends RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder> {

    private final String[] quotes;

    public QuoteAdapter(String[] quotes) {
        this.quotes = quotes;
    }

    @NonNull @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        holder.quoteText.setText(quotes[position]);
    }

    @Override public int getItemCount() { return quotes.length; }

    static class QuoteViewHolder extends RecyclerView.ViewHolder {
        TextView quoteText;
        QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            quoteText = itemView.findViewById(R.id.quoteText);
        }
    }
}