package com.example.onedrop.Activity.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.onedrop.Activity.Model.OnboardingItem;
import com.example.onedrop.R;
import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private List<OnboardingItem> onboardingItems;

    public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
        this.onboardingItems = onboardingItems;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new OnboardingViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_onboarding, parent, false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.setOnboardingData(onboardingItems.get(position));
    }

    @Override
    public int getItemCount() {
        return onboardingItems.size();
    }

    class OnboardingViewHolder extends RecyclerView.ViewHolder {

        private ImageView imageOnboarding;
        private TextView titleOnboarding;
        private TextView descriptionOnboarding;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageOnboarding = itemView.findViewById(R.id.onboardingImage);
            titleOnboarding = itemView.findViewById(R.id.onboardingTitle);
            descriptionOnboarding = itemView.findViewById(R.id.onboardingDescription);
        }

        void setOnboardingData(OnboardingItem onboardingItem) {
            imageOnboarding.setImageResource(onboardingItem.getImage());
            titleOnboarding.setText(onboardingItem.getTitle());
            descriptionOnboarding.setText(onboardingItem.getDescription());
        }
    }
}