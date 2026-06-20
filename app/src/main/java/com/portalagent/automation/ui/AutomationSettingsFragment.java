package com.portalagent.automation.ui;

import com.termux.app.TermuxActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.termux.R;
import com.portalagent.automation.ActionRecipe;
import com.portalagent.automation.AutomationSettingsStore;
import com.portalagent.automation.AutomationStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AutomationSettingsFragment extends Fragment {

    private AutomationStore store;
    private AutomationSettingsStore settingsStore;
    private final List<ActionRecipe> candidates = new ArrayList<>();
    private final List<ActionRecipe> recipes = new ArrayList<>();
    private AutomationRecipeAdapter candidatesAdapter;
    private AutomationRecipeAdapter recipesAdapter;
    private SwitchMaterial enabledSwitch;
    private TextView runtimeSummary;
    private TextView whitelistSummary;
    private TextView candidatesEmpty;
    private TextView recipesEmpty;
    private TextView failuresEmpty;
    private LinearLayout failuresContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_automation_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        store = new AutomationStore(requireContext());
        settingsStore = new AutomationSettingsStore(requireContext());

        enabledSwitch = view.findViewById(R.id.automation_boost_enabled);
        runtimeSummary = view.findViewById(R.id.automation_runtime_summary);
        whitelistSummary = view.findViewById(R.id.automation_whitelist_summary);
        candidatesEmpty = view.findViewById(R.id.automation_candidates_empty);
        recipesEmpty = view.findViewById(R.id.automation_recipes_empty);
        failuresEmpty = view.findViewById(R.id.automation_failures_empty);
        failuresContainer = view.findViewById(R.id.automation_failures_container);

        view.findViewById(R.id.automation_back_button).setOnClickListener(v -> {
            if (getActivity() instanceof TermuxActivity) {
                ((TermuxActivity) getActivity()).navigateBackToSettingsHub();
            } else if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        enabledSwitch.setChecked(settingsStore.isBoostEnabled());
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsStore.setBoostEnabled(isChecked);
            updateSummary();
        });

        RecyclerView candidatesRecycler = view.findViewById(R.id.automation_candidates_recycler);
        candidatesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        candidatesRecycler.setNestedScrollingEnabled(false);
        candidatesAdapter = new AutomationRecipeAdapter(candidates,
            AutomationRecipeAdapter.Mode.CANDIDATE, settingsStore, new AutomationRecipeAdapter.Listener() {
                @Override
                public void onPrimary(ActionRecipe recipe) {
                    enableCandidate(recipe);
                }

                @Override
                public void onSecondary(ActionRecipe recipe) {
                    deleteCandidate(recipe);
                }
            });
        candidatesRecycler.setAdapter(candidatesAdapter);

        RecyclerView recipesRecycler = view.findViewById(R.id.automation_recipes_recycler);
        recipesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recipesRecycler.setNestedScrollingEnabled(false);
        recipesAdapter = new AutomationRecipeAdapter(recipes,
            AutomationRecipeAdapter.Mode.RECIPE, settingsStore, new AutomationRecipeAdapter.Listener() {
                @Override
                public void onPrimary(ActionRecipe recipe) {
                    toggleRecipeWhitelist(recipe);
                }

                @Override
                public void onSecondary(ActionRecipe recipe) {
                    disableRecipe(recipe);
                }
            });
        recipesRecycler.setAdapter(recipesAdapter);

        refresh();
    }

    private void enableCandidate(ActionRecipe candidate) {
        final ActionRecipe[] moved = new ActionRecipe[1];
        store.editCandidates(list -> {
            for (int i = list.size() - 1; i >= 0; i--) {
                ActionRecipe recipe = list.get(i);
                if (sameId(recipe, candidate)) {
                    moved[0] = recipe;
                    list.remove(i);
                    break;
                }
            }
        });
        if (moved[0] != null) {
            ActionRecipe enabled = copyWithEnabled(moved[0], true, true);
            store.editRecipes(list -> {
                removeById(list, enabled.id);
                list.add(enabled);
            });
        }
        refresh();
    }

    private void deleteCandidate(ActionRecipe candidate) {
        store.editCandidates(list -> removeById(list, candidate.id));
        refresh();
    }

    private void toggleRecipeWhitelist(ActionRecipe recipe) {
        boolean enable = !settingsStore.isRecipeWhitelisted(recipe.id);
        settingsStore.setRecipeWhitelisted(recipe.id, enable);
        if (!isEmpty(recipe.targetPackage)) {
            if (enable) {
                settingsStore.setAppWhitelisted(recipe.targetPackage, true);
            } else {
                removeAppWhitelistIfUnused(recipe.targetPackage, recipe.id);
            }
        }
        refresh();
    }

    private void disableRecipe(ActionRecipe recipe) {
        store.editRecipes(list -> {
            for (int i = 0; i < list.size(); i++) {
                if (sameId(list.get(i), recipe)) {
                    list.set(i, copyWithEnabled(list.get(i), false, list.get(i).autoBoostEnabled));
                    break;
                }
            }
        });
        settingsStore.setRecipeWhitelisted(recipe.id, false);
        if (!isEmpty(recipe.targetPackage)) {
            removeAppWhitelistIfUnused(recipe.targetPackage, recipe.id);
        }
        refresh();
    }

    private void refresh() {
        candidates.clear();
        candidates.addAll(store.loadCandidates());
        recipes.clear();
        for (ActionRecipe recipe : store.loadRecipes()) {
            if (recipe.enabled) recipes.add(recipe);
        }
        candidatesAdapter.notifyDataSetChanged();
        recipesAdapter.notifyDataSetChanged();

        candidatesEmpty.setVisibility(candidates.isEmpty() ? View.VISIBLE : View.GONE);
        recipesEmpty.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
        updateSummary();
        updateWhitelistSummary();
        renderFailures();
    }

    private void updateSummary() {
        if (runtimeSummary == null) return;
        int failureCount = store == null ? 0 : store.loadFailures().size();
        String enabled = settingsStore != null && settingsStore.isBoostEnabled() ? "已开启" : "已关闭";
        runtimeSummary.setText("Boost " + enabled
            + " · 候选 " + candidates.size() + " 个"
            + " · 已启用 " + recipes.size() + " 个"
            + " · 失败 " + failureCount + " 次");
    }

    private void updateWhitelistSummary() {
        int appCount = settingsStore.appWhitelist().size();
        int recipeCount = settingsStore.recipeWhitelist().size();
        whitelistSummary.setText("应用白名单：" + appCount + " 个 · 配方白名单：" + recipeCount + " 个");
    }

    private void renderFailures() {
        failuresContainer.removeAllViews();
        List<JSONObject> failures = store.loadFailures();
        failuresEmpty.setVisibility(failures.isEmpty() ? View.VISIBLE : View.GONE);
        int start = Math.max(0, failures.size() - 10);
        for (int i = failures.size() - 1; i >= start; i--) {
            JSONObject failure = failures.get(i);
            TextView row = new TextView(requireContext());
            row.setText("配方：" + failure.optString("recipe_id", "-")
                + " · 步骤：" + failure.optString("step_id", "-")
                + " · 原因：" + failure.optString("reason", "-"));
            row.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_text_secondary));
            row.setTextSize(12);
            row.setBackgroundResource(R.drawable.bg_status_chip);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(6), 0, 0);
            failuresContainer.addView(row, params);
        }
    }

    private ActionRecipe copyWithEnabled(ActionRecipe recipe, boolean enabled, boolean autoBoostEnabled) {
        return new ActionRecipe(recipe.id, recipe.name, enabled, autoBoostEnabled, recipe.riskLevel,
            recipe.intentPatterns, recipe.targetPackage, recipe.targetActivity, recipe.startConditions,
            recipe.endConditions, recipe.steps, recipe.source, recipe.sourceTaskIds, recipe.stats,
            recipe.currentVersionId, recipe.versionsCopy());
    }

    private void removeAppWhitelistIfUnused(String packageName, String excludingRecipeId) {
        if (isEmpty(packageName)) return;
        if (!hasOtherEnabledWhitelistedRecipeForPackage(packageName, excludingRecipeId)) {
            settingsStore.setAppWhitelisted(packageName, false);
        }
    }

    private boolean hasOtherEnabledWhitelistedRecipeForPackage(String packageName, String excludingRecipeId) {
        if (isEmpty(packageName)) return false;
        for (ActionRecipe recipe : store.loadRecipes()) {
            if (recipe == null || !recipe.enabled) continue;
            if (recipe.id.equals(excludingRecipeId)) continue;
            if (packageName.equals(recipe.targetPackage) && settingsStore.isRecipeWhitelisted(recipe.id)) {
                return true;
            }
        }
        return false;
    }

    private void removeById(List<ActionRecipe> list, String id) {
        for (int i = list.size() - 1; i >= 0; i--) {
            ActionRecipe recipe = list.get(i);
            if (recipe != null && recipe.id.equals(id)) {
                list.remove(i);
            }
        }
    }

    private boolean sameId(ActionRecipe first, ActionRecipe second) {
        return first != null && second != null && first.id.equals(second.id);
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
