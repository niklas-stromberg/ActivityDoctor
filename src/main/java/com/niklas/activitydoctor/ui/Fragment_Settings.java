
package com.niklas.activitydoctor.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.NumberPicker;

import com.niklas.activitydoctor.R;
import com.niklas.activitydoctor.SensorListener;
import com.niklas.activitydoctor.util.PlaySettingsWrapper;

public class Fragment_Settings extends PreferenceFragment implements OnPreferenceClickListener {

    final static int DEFAULT_GOAL = 25;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);


        Preference account = findPreference("account");
        PlaySettingsWrapper
                .setupAccountSetting(account, savedInstanceState, (Activity_Main) getActivity());

        final SharedPreferences prefs =
                getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);

        Preference goal = findPreference("goal");
        goal.setOnPreferenceClickListener(this);
        goal.setSummary(getString(R.string.goal_summary, prefs.getInt("goal", DEFAULT_GOAL)));



        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        PlaySettingsWrapper.onSavedInstance(outState, (Activity_Main) getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_settings).setVisible(false);
        menu.findItem(R.id.action_pause).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return ((Activity_Main) getActivity()).optionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        AlertDialog.Builder builder;
        View v;
        final SharedPreferences prefs =
                getActivity().getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);
        switch (preference.getTitleRes()) {
            case R.string.goal:
                builder = new AlertDialog.Builder(getActivity());
                final NumberPicker np = new NumberPicker(getActivity());
                np.setMinValue(1);
                np.setMaxValue(1440);
                np.setValue(prefs.getInt("goal", DEFAULT_GOAL));
                builder.setView(np);
                builder.setTitle(R.string.set_goal);
                builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        np.clearFocus();
                        prefs.edit().putInt("goal", np.getValue()).commit();
                        preference.setSummary(getString(R.string.goal_summary, np.getValue()));
                        dialog.dismiss();
                        getActivity().startService(new Intent(getActivity(), SensorListener.class)
                                .putExtra("updateNotificationState", true));
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                Dialog dialog = builder.create();
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                dialog.show();
                break;


        }
        return false;
    }




}
