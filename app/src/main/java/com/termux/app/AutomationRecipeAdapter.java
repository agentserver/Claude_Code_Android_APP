package com.termux.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.automation.ActionRecipe;
import com.termux.app.automation.AutomationSettingsStore;
import com.termux.app.automation.RecipeStats;

import java.util.List;

public class AutomationRecipeAdapter extends RecyclerView.Adapter<AutomationRecipeAdapter.ViewHolder> {

    public enum Mode {
        CANDIDATE,
        RECIPE
    }

    public interface Listener {
        void onPrimary(ActionRecipe recipe);
        void onSecondary(ActionRecipe recipe);
    }

    private final List<ActionRecipe> recipes;
    private final Mode mode;
    private final AutomationSettingsStore settingsStore;
    private final Listener listener;

    public AutomationRecipeAdapter(List<ActionRecipe> recipes, Mode mode,
                                   AutomationSettingsStore settingsStore, Listener listener) {
        this.recipes = recipes;
        this.mode = mode;
        this.settingsStore = settingsStore;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_automation_recipe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActionRecipe recipe = recipes.get(position);
        holder.title.setText(title(recipe));
        holder.summary.setText(summary(recipe));

        if (mode == Mode.CANDIDATE) {
            holder.primary.setText("启用");
            holder.secondary.setText("删除");
        } else {
            boolean whitelisted = settingsStore != null && settingsStore.isRecipeWhitelisted(recipe.id);
            holder.primary.setText(whitelisted ? "取消白名单" : "白名单");
            holder.secondary.setText("禁用");
        }

        holder.primary.setOnClickListener(v -> {
            if (listener != null) listener.onPrimary(recipe);
        });
        holder.secondary.setOnClickListener(v -> {
            if (listener != null) listener.onSecondary(recipe);
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    private String title(ActionRecipe recipe) {
        if (recipe.name != null && !recipe.name.trim().isEmpty()) return recipe.name;
        if (recipe.id != null && !recipe.id.trim().isEmpty()) return recipe.id;
        return "(unnamed recipe)";
    }

    private String summary(ActionRecipe recipe) {
        String packageName = emptyToDash(recipe.targetPackage);
        String risk = recipe.riskLevel == null ? "-" : recipe.riskLevel.name();
        int stepCount = recipe.steps == null ? 0 : recipe.steps.size();
        RecipeStats stats = recipe.stats == null ? RecipeStats.empty() : recipe.stats;
        return "package: " + packageName
            + " · risk: " + risk
            + " · steps: " + stepCount
            + " · success/failure: " + stats.successCount + "/" + stats.failureCount;
    }

    private String emptyToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final MaterialButton primary;
        final MaterialButton secondary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.automation_recipe_title);
            summary = itemView.findViewById(R.id.automation_recipe_summary);
            primary = itemView.findViewById(R.id.automation_recipe_primary);
            secondary = itemView.findViewById(R.id.automation_recipe_secondary);
        }
    }
}
