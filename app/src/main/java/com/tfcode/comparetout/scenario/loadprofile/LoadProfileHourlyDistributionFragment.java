package com.tfcode.comparetout.scenario.loadprofile;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tfcode.comparetout.R;

public class LoadProfileHourlyDistributionFragment extends Fragment {
    private boolean mEdit = false;

    public LoadProfileHourlyDistributionFragment() {
        // Required empty public constructor
    }

    public static LoadProfileHourlyDistributionFragment newInstance() {
        LoadProfileHourlyDistributionFragment fragment = new LoadProfileHourlyDistributionFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_load_profile_hourly_distribution, container, false);
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
    }
}