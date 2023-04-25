package com.tfcode.comparetout.scenario;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScenarioDetails extends Fragment {

    private Long mScenarioID;
    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;

    private BarChart mBarChart;
    private LineChart mLineChart;
    private TextView mTextView;
    private TextView mCurrentDateTV;
    private int mDayOfYear = 174;

    private List<ScenarioLineGraphData> mLineData;
    private List<ScenarioBarChartData> mBarData;

    public ScenarioDetails() {
        // Required empty public constructor
    }

    public static ScenarioDetails newInstance() {
        return new ScenarioDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
//        if (!(null == getActivity())) getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((ScenarioActivity) requireActivity()).getScenarioID();
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllComparisons().observe(this, costings -> {
            System.out.println("Observed a change in the costings");
            updateKPIs();
        });
    }

    private void updateKPIs() {
        new Thread(() -> {
            mBarData = mViewModel.getBarData(mScenarioID, mDayOfYear);
            System.out.println("mBarData has " + mBarData.size() + " entries.");
            mLineData = mViewModel.getLineData(mScenarioID, mDayOfYear);
            mMainHandler.post(this::updateView);
        }).start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_senario_details, container, false);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());
        mBarChart = view.findViewById((R.id.scenario_detail_chart));
        mLineChart = view.findViewById((R.id.scenario_detail_graph));
        mTextView = view.findViewById((R.id.no_simulation_data));
        ImageButton mPreviousButton = view.findViewById((R.id.previous));
        mPreviousButton.setOnClickListener(v -> {
            mDayOfYear--;
            if (mDayOfYear == 0) mDayOfYear = 365;
            LocalDate localDate = LocalDate.ofYearDay(2001, mDayOfYear);
            mCurrentDateTV.setText(String.format("%s/%s", String.format("%02d", localDate.getDayOfMonth()), String.format("%02d", localDate.getMonthValue())));
            updateKPIs();
        });
        ImageButton mNextButton = view.findViewById((R.id.next));
        mNextButton.setOnClickListener(v -> {
            mDayOfYear++;
            if (mDayOfYear == 366) mDayOfYear = 1;
            LocalDate localDate = LocalDate.ofYearDay(2001, mDayOfYear);
            mCurrentDateTV.setText(String.format("%s/%s", String.format("%02d", localDate.getDayOfMonth()), String.format("%02d", localDate.getMonthValue())));
            updateKPIs();
        });
        mCurrentDateTV = view.findViewById((R.id.date));
        ImageButton mDatePickerButton = view.findViewById((R.id.pick_date));
        @SuppressLint("DefaultLocale") DatePickerDialog.OnDateSetListener date = (view1, year, month, day) -> {
            LocalDate localDate = LocalDate.of(2001, month +1, day);
            mDayOfYear = localDate.getDayOfYear();
            mCurrentDateTV.setText(String.format("%s/%s", String.format("%02d", localDate.getDayOfMonth()), String.format("%02d", localDate.getMonthValue())));
            updateKPIs();
        };
        mDatePickerButton.setOnClickListener(v -> {
            LocalDate localDate = LocalDate.ofYearDay(2001, mDayOfYear);
            new DatePickerDialog(getActivity(), date, 2001 ,localDate.getMonth().getValue() - 1, localDate.getDayOfMonth()).show();
        });

        ImageButton mFilterButton = view.findViewById((R.id.filter));
    }

    private void updateView() {
        System.out.println("updateView");
        boolean showText;
        if (!(null == mBarChart) && (!(null == mBarData)) && !mBarData.isEmpty()) {
            mBarChart.setVisibility(View.VISIBLE);
            mBarChart.clear();
            mTextView.setVisibility(View.INVISIBLE);

            final String[] xLabels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                    "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",};
            final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
            XAxis xAxis = mBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return xLabel.get((int) value);
                }
            });

            mBarChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
            mBarChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
            mBarChart.getXAxis().setTextColor(Color.DKGRAY);
            mBarChart.getLegend().setTextColor(Color.DKGRAY);
            mBarChart.getDescription().setTextColor(Color.DKGRAY);

            ArrayList<BarEntry> loadEntries = new ArrayList<>();
            ArrayList<BarEntry> feedEntries = new ArrayList<>();
            ArrayList<BarEntry> buyEntries = new ArrayList<>();
            ArrayList<BarEntry> pvEntries = new ArrayList<>();
            ArrayList<BarEntry> pv2BatteryEntries = new ArrayList<>();
            ArrayList<BarEntry> pv2LoadEntries = new ArrayList<>();
            ArrayList<BarEntry> battery2LoadEntries = new ArrayList<>();
            ArrayList<BarEntry> evScheduleEntries = new ArrayList<>();
            ArrayList<BarEntry> hwScheduleEntries = new ArrayList<>();
            ArrayList<BarEntry> evDivertEntries = new ArrayList<>();
            ArrayList<BarEntry> hwDivertEntries = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                loadEntries.add(new BarEntry(i, (float) mBarData.get(i).load));
                feedEntries.add(new BarEntry(i, (float) mBarData.get(i).feed));
                buyEntries.add(new BarEntry(i, (float) mBarData.get(i).buy));
                pvEntries.add(new BarEntry(i, (float) mBarData.get(i).pv));
                pv2BatteryEntries.add(new BarEntry(i, (float) mBarData.get(i).pv2Battery));
                pv2LoadEntries.add(new BarEntry(i, (float) mBarData.get(i).pv2Load));
                battery2LoadEntries.add(new BarEntry(i, (float) mBarData.get(i).battery2Load));
                evScheduleEntries.add(new BarEntry(i, (float) mBarData.get(i).evSchedule));
                hwScheduleEntries.add(new BarEntry(i, (float) mBarData.get(i).hwSchedule));
                evDivertEntries.add(new BarEntry(i, (float) mBarData.get(i).evDivert));
                hwDivertEntries.add(new BarEntry(i, (float) mBarData.get(i).hwDivert));
            }

            BarDataSet loadSet;
            BarDataSet feedSet;
            BarDataSet buySet;
            BarDataSet pvSet;
            BarDataSet pv2BatterySet;
            BarDataSet pv2LoadSet;
            BarDataSet battery2LoadSet;
            BarDataSet evScheduleSet;
            BarDataSet hwScheduleSet;
            BarDataSet evDivertSet;
            BarDataSet hwDivertSet;

//            if (mBarChart.getData() != null &&
//                    mBarChart.getData().getDataSetCount() > 0) {
//                loadSet = (BarDataSet) mBarChart.getData().getDataSetByIndex(0);
//                loadSet.setValues(loadEntries);
//
//                mBarChart.getData().notifyDataChanged();
//                mBarChart.notifyDataSetChanged();
//            } else {
                loadSet = new BarDataSet(loadEntries, "Hourly load");
                loadSet.setColor(Color.BLUE);
                feedSet = new BarDataSet(feedEntries, "Hourly feed");
                feedSet.setColor(Color.YELLOW);
                buySet = new BarDataSet(buyEntries, "Hourly buy");
                buySet.setColor(Color.GREEN);
                pvSet = new BarDataSet(pvEntries, "Hourly PV");
                pvSet.setColor(Color.RED);
                pv2BatterySet = new BarDataSet(pv2BatteryEntries, "Hourly PV to battery");
                pv2BatterySet.setColor(Color.BLACK);
                pv2LoadSet = new BarDataSet(pv2LoadEntries, "Hourly PV to load");
                pv2LoadSet.setColor(Color.parseColor("#3ca567"));
                battery2LoadSet = new BarDataSet(battery2LoadEntries, "Hourly battery to load");
                battery2LoadSet.setColor(Color.parseColor("#309967"));
                evScheduleSet = new BarDataSet(evScheduleEntries, "Hourly EV charging");
                evScheduleSet.setColor(Color.parseColor("#476567"));
                hwScheduleSet = new BarDataSet(hwScheduleEntries, "Hourly water heating");
                hwScheduleSet.setColor(Color.parseColor("#890567"));
                evDivertSet = new BarDataSet(evDivertEntries, "Hourly EV diversion");
                evDivertSet.setColor(Color.parseColor("#a35567"));
                hwDivertSet = new BarDataSet(hwDivertEntries, "Hourly hot water diversion");
                hwDivertSet.setColor(Color.parseColor("#ff5f67"));
//            colors.add(Color.parseColor("#304567"));

                ArrayList<IBarDataSet> dataSets = new ArrayList<>();
                dataSets.add(loadSet);
                dataSets.add(feedSet);
                dataSets.add(buySet);
//                dataSets.add(pvSet);
//            dataSets.add(pv2BatterySet);
//            dataSets.add(pv2LoadSet);
//                dataSets.add(battery2LoadSet);
//            dataSets.add(evScheduleSet);
//            dataSets.add(hwScheduleSet);
//            dataSets.add(evDivertSet);
//            dataSets.add(hwDivertSet);

                BarData data = new BarData(dataSets);
                data.setValueTextSize(10f);
                data.setDrawValues(false);
                mBarChart.getDescription().setEnabled(false);
                mBarChart.setData(data);

                //data
                float groupSpace = 0.04f;
                float barSpace; // x2 dataset
                float barWidth; // x2 dataset
                // (0.46 + 0.02) * 2 + 0.04 = 1.00 -> interval per "group"
                // (0.22 + 0.02) * 4 + 0.05
                // (barWidth + barSpace) * elementsInGroup + groupSpace = 1

                float count = 3f;
                float section = 0.96f / count;
                barSpace = section - section / count;
                barWidth = section - barSpace;

                data.setBarWidth(barWidth);
                mBarChart.groupBars(0, groupSpace, barSpace);
//            }
            mBarChart.invalidate();
            mBarChart.refreshDrawableState();
        }

        if (!(null == mLineChart) && (!(null == mLineData)) && !mLineData.isEmpty())  {
            showText = false;
            mLineChart.setVisibility(View.VISIBLE);

            mLineChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
            mLineChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
            mLineChart.getXAxis().setEnabled(false);//setTextColor(Color.DKGRAY);
            mLineChart.getLegend().setTextColor(Color.DKGRAY);
            mLineChart.getDescription().setTextColor(Color.DKGRAY);

            ArrayList<Entry> socValues = new ArrayList<>();
            ArrayList<Entry> tempValues = new ArrayList<>();
            for (ScenarioLineGraphData lgd : mLineData) {
                socValues.add(new Entry(lgd.mod, (float) lgd.soc));
                tempValues.add(new Entry(lgd.mod, (float) lgd.waterTemperature));
            }

            LineDataSet socSet;
            LineDataSet tempSet;
            if (mLineChart.getData() != null &&
                    mLineChart.getData().getDataSetCount() > 0) {
                socSet = (LineDataSet) mLineChart.getData().getDataSetByIndex(0);
                socSet.setValues(socValues);
                if (mLineChart.getData().getDataSetCount() > 1) {
                    tempSet = (LineDataSet) mLineChart.getData().getDataSetByIndex(1);
                    tempSet.setValues(tempValues);
                }
                mLineChart.getData().notifyDataChanged();
                mLineChart.notifyDataSetChanged();
            } else {
                socSet = new LineDataSet(socValues, "State of Charge");
                socSet.setDrawIcons(false);
                socSet.enableDashedLine(10f, 5f, 0f);
                socSet.enableDashedHighlightLine(10f, 5f, 0f);
                socSet.setColor(Color.YELLOW);
                socSet.setCircleColor(Color.YELLOW);
                socSet.setLineWidth(1f);
                socSet.setCircleRadius(3f);
                socSet.setDrawCircleHole(false);
                socSet.setValueTextSize(9f);
                socSet.setDrawFilled(true);
                socSet.setFormLineWidth(1f);
                socSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
                socSet.setFormSize(15.f);
                socSet.setFillColor(Color.YELLOW);

                tempSet = new LineDataSet(tempValues, "Temperature of Water");
                tempSet.setDrawIcons(false);
                tempSet.enableDashedLine(10f, 5f, 0f);
                tempSet.enableDashedHighlightLine(10f, 5f, 0f);
                tempSet.setColor(Color.BLUE);
                tempSet.setCircleColor(Color.BLUE);
                tempSet.setLineWidth(1f);
                tempSet.setCircleRadius(3f);
                tempSet.setDrawCircleHole(false);
                tempSet.setValueTextSize(9f);
                tempSet.setDrawFilled(true);
                tempSet.setFormLineWidth(1f);
                tempSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
                tempSet.setFormSize(15.f);
                tempSet.setFillColor(Color.BLUE);

                ArrayList<ILineDataSet> dataSets = new ArrayList<>();
                dataSets.add(socSet);
                dataSets.add(tempSet);
                LineData data = new LineData(dataSets);
                mLineChart.setData(data);
            }
            mLineChart.getDescription().setEnabled(false);

            mLineChart.invalidate();
            mLineChart.refreshDrawableState();
        }
        else showText = true;

        if (showText) {
            if (mBarChart != null) {
                mBarChart.setVisibility(View.INVISIBLE);
            }
            if (mLineChart != null) {
                mLineChart.setVisibility(View.INVISIBLE);
            }
            mTextView.setVisibility(View.VISIBLE);
            mTextView.setText(R.string.NoChartData);
        }
    }
}
