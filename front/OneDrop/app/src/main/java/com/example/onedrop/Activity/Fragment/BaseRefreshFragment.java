package com.example.onedrop.Activity.Fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.graphics.Color;
import com.example.onedrop.R;

/**
 * Base class for all fragments that support swipe-to-refresh.
 * Subclasses must:
 *  1. Have a SwipeRefreshLayout with id R.id.swipeRefreshLayout in their XML
 *  2. Override onRefresh() with their refresh logic
 *  3. Call stopRefreshing() when done
 */
public abstract class BaseRefreshFragment extends Fragment {

    protected SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            // OneDrop brand colors for the spinner
            swipeRefreshLayout.setColorSchemeColors(
                    Color.parseColor("#DC143C"),
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#B71C1C")
            );
            swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.WHITE);
            swipeRefreshLayout.setOnRefreshListener(this::onRefresh);
        }
    }

    /** Override this in each fragment to implement refresh logic */
    protected abstract void onRefresh();

    /** Call this when your async refresh is complete */
    protected void stopRefreshing() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}