package com.termux.app.automation;

import android.content.Context;

import java.util.List;

public final class AutomationRuntime {

    private final Context context;
    private final AutomationStore store;
    private final ToolTraceStore traceStore;
    private final BoostExecutor executor;

    public AutomationRuntime(Context context, AutomationStore store, ToolTraceStore traceStore, BoostExecutor executor) {
        this.context = context == null ? null : context.getApplicationContext();
        this.store = store;
        this.traceStore = traceStore;
        this.executor = executor;
    }

    public long markTurnStarted() {
        return System.currentTimeMillis();
    }

    public void generateCandidateForCompletedTurn(String prompt, String sourceTaskId, long startMs, long endMs) {
        if (store == null || traceStore == null) return;

        List<ToolTraceEvent> traces = traceStore.loadBetween(startMs, endMs);
        final ActionRecipe candidate = AutomationCandidateGenerator.fromSuccessfulTurn(prompt, sourceTaskId, traces);
        if (candidate == null) return;

        store.editCandidates(candidates -> {
            for (int i = 0; i < candidates.size(); i++) {
                ActionRecipe existing = candidates.get(i);
                if (RecipeDedupe.shouldMerge(existing, candidate)) {
                    try {
                        candidates.set(i, RecipeDedupe.mergeAsVersion(existing, candidate));
                    } catch (Exception e) {
                        throw new IllegalStateException("Unable to merge automation candidate", e);
                    }
                    return;
                }
            }
            candidates.add(candidate);
        });
    }

    public boolean tryStartBoost(String prompt, BoostExecutor.Callback callback) {
        return false;
    }
}
