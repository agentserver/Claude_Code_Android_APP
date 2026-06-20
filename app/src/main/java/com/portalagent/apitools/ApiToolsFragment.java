package com.portalagent.apitools;

import com.portalagent.settings.AppSettingsFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;

public class ApiToolsFragment extends Fragment implements ApiToolsController.Host {

    private ApiToolsController mController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_api_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean embeddedInAppSettings = getParentFragment() instanceof AppSettingsFragment;
        if (embeddedInAppSettings) {
            View header = view.findViewById(R.id.api_tools_header_container);
            View description = view.findViewById(R.id.api_tools_description);
            if (header != null) header.setVisibility(View.GONE);
            if (description != null) description.setVisibility(View.GONE);
        }

        View backButton = view.findViewById(R.id.api_tools_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Fragment parent = getParentFragment();
                if (parent instanceof AppSettingsFragment) {
                    ((AppSettingsFragment) parent).handleBackPressed();
                } else if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        mController = new ApiToolsController(this);
        mController.bind(view);
    }

    @Override
    public void onDestroyView() {
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
        super.onDestroyView();
    }

    @NonNull
    @Override
    public Context getApiToolsContext() {
        return requireContext();
    }

    @Override
    public void requestApiToolsPermissions(@NonNull String[] permissions, int requestCode) {
        requestPermissions(permissions, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mController != null) {
            mController.onRequestPermissionsResult(requestCode, grantResults);
        }
    }
}
