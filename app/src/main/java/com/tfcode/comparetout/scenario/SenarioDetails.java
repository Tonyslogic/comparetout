package com.tfcode.comparetout.scenario;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tfcode.comparetout.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SenarioDetails#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SenarioDetails extends Fragment {

    private boolean mEdit = false;

    public SenarioDetails() {
        // Required empty public constructor
    }

    public static SenarioDetails newInstance() {
        SenarioDetails fragment = new SenarioDetails();
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
        return inflater.inflate(R.layout.fragment_senario_details, container, false);
    }

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
//            if (!(null == mEditFields)) for (View v : mEditFields) v.setEnabled(true);
            ScenarioActivity ppa = ((ScenarioActivity) getActivity());
            if (!(null == ppa)) ppa.setEdit();
        }
    }
}