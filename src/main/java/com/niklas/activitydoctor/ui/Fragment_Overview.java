
package com.niklas.activitydoctor.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.niklas.activitydoctor.BuildConfig;
import com.niklas.activitydoctor.Database;
import com.niklas.activitydoctor.SensorListener;
import com.niklas.activitydoctor.util.Logger;

import org.eazegraph.lib.charts.BarChart;
import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.BarModel;
import org.eazegraph.lib.models.PieModel;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.niklas.activitydoctor.R;
import com.niklas.activitydoctor.util.Util;

public class Fragment_Overview extends Fragment {

    private TextView minutesView, totalView, averageView;

    private PieModel sliceGoal, sliceModerate, sliceVigorous;
    private PieChart pg;

    private int todayMinutesModerate, todayMinutesVigorous, goal;
    public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());


    private Runnable updateUItimer = new Runnable() {
        @Override
        public void run() {
            {
                /* Updates the UI every 10 seconds. */
                updatePie();
                handler.postDelayed(this, 1000 * 10);
            }
        }
    };
    private Handler handler = new Handler();


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);


    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_overview, null);
        minutesView = (TextView) v.findViewById(R.id.minutes);
        totalView = (TextView) v.findViewById(R.id.total);
        averageView = (TextView) v.findViewById(R.id.average);

        pg = (PieChart) v.findViewById(R.id.graph);


        pg.setDrawValueInPie(false);
        pg.setUsePieRotation(true);
        pg.startAnimation();
        changePieColor();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);


        Database db = Database.getInstance(getActivity());
        SharedPreferences prefs =
                getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);

        if (BuildConfig.DEBUG) db.logState();
        // read today's minutes.
        todayMinutesVigorous = prefs.getInt("todayMinutesVigorous", 0) * 2;
        todayMinutesModerate = prefs.getInt("todayMinutesModerate", 0);

        goal = prefs.getInt("goal", Fragment_Settings.DEFAULT_GOAL);


        handler.postDelayed(updateUItimer, 1000 * 3);

        db.close();

        updatePie();
        updateBars();
    }


    @Override
    public void onPause() {
        super.onPause();

        handler.removeCallbacks(updateUItimer);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);

        MenuItem pause = menu.getItem(0);
        Drawable d;
        if (getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE)
                .contains("pauseCount")) { // currently paused
            pause.setTitle(R.string.resume);
            d = getResources().getDrawable(R.drawable.ic_resume);
        } else {
            pause.setTitle(R.string.pause);
            d = getResources().getDrawable(R.drawable.ic_pause);
        }
        d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        pause.setIcon(d);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_pause:
                Drawable d;
                if (getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE)
                        .contains("pauseCount")) { // currently paused -> now resumed
                    item.setTitle(R.string.pause);
                    d = getResources().getDrawable(R.drawable.ic_pause);
                } else {
                    item.setTitle(R.string.resume);
                    d = getResources().getDrawable(R.drawable.ic_resume);
                }
                d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                item.setIcon(d);
                getActivity().startService(new Intent(getActivity(), SensorListener.class)
                        .putExtra("action", SensorListener.ACTION_PAUSE));
                return true;
            default:
                return ((Activity_Main) getActivity()).optionsItemSelected(item);
        }
    }


    /**
     * Updates the pie graph to show today's minutes as well as the
     * yesterday and total values.
     */
    private void updatePie() {
        if (BuildConfig.DEBUG)
            Logger.log("UI - update minutes: " + (todayMinutesVigorous + todayMinutesModerate));
        SharedPreferences prefs = getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);

        todayMinutesVigorous = prefs.getInt("todayMinutesVigorous", 0) * 2;
        todayMinutesModerate = prefs.getInt("todayMinutesModerate", 0);
        sliceModerate.setValue(todayMinutesModerate);
        sliceVigorous.setValue(todayMinutesVigorous);
        if (goal > (todayMinutesModerate + (todayMinutesVigorous))) {
            // goal not reached yet
            if (pg.getData().size() == 1) {
                // can happen if the goal value was changed: old goal value was
                // reached but now there are some minutes missing for the new goal
                pg.addPieSlice(sliceGoal);
            }
            sliceGoal.setValue(goal - (todayMinutesModerate + (todayMinutesVigorous)));
        } else {
            // goal reached
            pg.clearChart();
            pg.addPieSlice(sliceVigorous);
            pg.addPieSlice(sliceModerate);
        }
        pg.update();

        minutesView.setText(formatter.format((todayMinutesModerate + (todayMinutesVigorous))));
        totalView.setText(formatter.format((todayMinutesVigorous)));
        updateTimeLeftPerDay();
    }

    private void updateTimeLeftPerDay() {
        int todayMinutesTotal = todayMinutesModerate + (todayMinutesVigorous);
        Database db = Database.getInstance(getActivity());
        int activeDays = db.getDays();
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(Util.getToday());

        date.add(Calendar.DATE, -6);

        int thisWeek = db.getMinutes(date.getTimeInMillis(), System.currentTimeMillis());
        thisWeek += (todayMinutesTotal);
        int timeLeft = 150 - thisWeek;
        if (timeLeft > 0) {  //If weekly goal not reached
            if (activeDays > 6) {
                averageView.setText(formatter.format(timeLeft));
            } else {
                if (22 - todayMinutesTotal > 0) {
                    averageView.setText(formatter.format(22 - todayMinutesTotal));
                } else {
                    averageView.setText((R.string.optional));
                }
            }
        } else {  //If weekly goal reached.
            averageView.setText(R.string.optional);
        }
        db.close();
    }

    private boolean weekGoalReached() {

        Database db = Database.getInstance(getActivity());
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(Util.getToday());

        date.add(Calendar.DATE, -7);

        int thisWeekTime = db.getMinutes(date.getTimeInMillis(), System.currentTimeMillis());

        if (150 > thisWeekTime) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Updates the bar graph to show the minutes of the last week.
     */
    private void updateBars() {
        SimpleDateFormat df = new SimpleDateFormat("E", Locale.getDefault());
        BarChart barChart = (BarChart) getView().findViewById(R.id.bargraph);
        if (barChart.getData().size() > 0) barChart.clearChart();
        int minutes;
        //boolean weekGoalReached = weekGoalReached();

        BarModel bm;
        Database db = Database.getInstance(getActivity());
        List<Pair<Long, Integer>> last = db.getLastEntries(8);
        db.close();
        for (int i = last.size() - 1; i > 0; i--) {
            Pair<Long, Integer> current = last.get(i);
            minutes = current.second;
            if (minutes > 0) {

               // if (!weekGoalReached) {  //Week goal not reached.
                    bm = new BarModel(df.format(new Date(current.first)), 0,
                            minutes > 22 ? Color.parseColor("#99CC00") : Color.parseColor("#0099cc"));
               /* } else   //Week goal reached.
                {
                    bm = new BarModel(df.format(new Date(current.first)), 0,
                            Color.parseColor("#99CC00"));
                }*/


                bm.setValue(minutes);
                barChart.addBar(bm);
            }
        }
        if (barChart.getData().size() > 0) {
            barChart.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    Dialog_Statistics.getDialog(getActivity(), (todayMinutesVigorous + todayMinutesModerate)).show();
                }
            });
            barChart.startAnimation();
        } else {
            barChart.setVisibility(View.GONE);
        }
    }

    /**
     * Changes pie color. The pie color changes when user changes intensity in options menu.
     */
    private void changePieColor() {
        pg.clearChart();

        sliceVigorous = new PieModel("", 0, Color.parseColor("#c800e78f"));
        sliceModerate = new PieModel("", 0, Color.parseColor("#9c0eac00"));
        sliceGoal = new PieModel("", Fragment_Settings.DEFAULT_GOAL * 5, Color.parseColor("#b1ff0000"));
        //sliceGoal = new PieModel("", Fragment_Settings.DEFAULT_GOAL * 5, Color.parseColor("#97bbfa"));
        pg.addPieSlice(sliceVigorous);
        pg.addPieSlice(sliceModerate);
        pg.addPieSlice(sliceGoal);


    }

}
