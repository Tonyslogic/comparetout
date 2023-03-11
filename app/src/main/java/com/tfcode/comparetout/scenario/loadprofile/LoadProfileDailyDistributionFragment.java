package com.tfcode.comparetout.scenario.loadprofile;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tfcode.comparetout.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link LoadProfileDailyDistributionFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LoadProfileDailyDistributionFragment extends Fragment {
    private boolean mEdit = false;

    public LoadProfileDailyDistributionFragment() {
        // Required empty public constructor
    }

    public static LoadProfileDailyDistributionFragment newInstance() {
        LoadProfileDailyDistributionFragment fragment = new LoadProfileDailyDistributionFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_load_profile_daily_distribution, container, false);
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
    }
}