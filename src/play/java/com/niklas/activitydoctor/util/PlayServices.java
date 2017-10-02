

package com.niklas.activitydoctor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.niklas.activitydoctor.Database;
import com.niklas.activitydoctor.R;

/**
 * Class to manage the Google Play achievements
 */
public abstract class PlayServices {

    /**
     * Updates the 'most minutes walked' leaderboard score
     *
     * @param gc           the GamesClient
     * @param c            the Context
     * @param totalminutes the new score = total minutes walked
     */
    private static void updateTotalLeaderboard(final GoogleApiClient gc, final Context c, int totalminutes) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_total_activity), totalminutes);
    }

    /**
     * Updates the 'most minutes walked in one day' leaderboard score
     *
     * @param gc      the GamesClient
     * @param c       the Context
     * @param minutes the new score = max number of minutes walked in one day
     */
    private static void updateOneDayLeaderboard(final GoogleApiClient gc, final Context c, int minutes) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_one_day_activity),
                        minutes);
    }

    /**
     * Updates the 'hightest average' leaderboard score
     *
     * @param gc  the GamesClient
     * @param c   the Context
     * @param avg the new score = current average
     */
    private static void updateAverageLeaderboard(final GoogleApiClient gc, final Context c, float avg) {
        // some cheat detection needed?
        Games.Leaderboards
                .submitScore(gc, c.getString(R.string.leaderboard_average_activity), (long) avg);
    }

    /**
     * Check the conditions for not-yet-unlocked achievements and unlock them if
     * the condition is met and updates the leaderboard
     *
     * @param gc      the GamesClient
     * @param context the Context
     */
    public static void achievementsAndLeaderboard(final GoogleApiClient gc, final Context context) {
        if (gc.isConnected()) {
            Database db = Database.getInstance(context);
            db.removeInvalidEntries();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("achievement_boot_are_made_for_walking", false)) {
                Cursor c = db.query(new String[]{"minutes"}, "minutes >= 7500 AND date > 0", null, null,
                        null, null, "1");
                if (c.getCount() >= 1) {
                    //    unlockAchievement(gc,
                    //         context.getString(R.string.achievement_boots_made_for_walking));
                    prefs.edit().putBoolean("achievement_boot_are_made_for_walking", true).apply();
                }
                c.close();
            }


            Cursor c = db.query(new String[]{"COUNT(*)"}, "minutes >= 10000 AND date > 0", null, null,
                    null, null, null);
            c.moveToFirst();
            int daysForStamina = c.getInt(0);
            c.close();

            if (!prefs.getBoolean("achievement_the_flame_is_still_burning", false)) {
                if (daysForStamina >= 21) {
                         unlockAchievement(gc, context.getString(R.string.achievement_the_flame_is_still_burning));
                    prefs.edit().putBoolean("achievement_the_flame_is_still_burning", true).apply();
                }
            }


            int totalminutes = db.getTotalWithoutToday();

            if (!prefs.getBoolean("achievement_good_stamina", false)) {
                if (totalminutes >= 500) {
                    unlockAchievement(gc, context.getString(R.string.achievement_good_stamina));
                    prefs.edit().putBoolean("achievement_good_stamina", true).apply();
                }
            }


            int days = db.getDaysWithoutToday();
            float average = totalminutes / (float) days;

                if (!prefs.getBoolean("achievement_healthy", false)) {
                    if (average >= 150 && days > 6) {
                           unlockAchievement(gc, context.getString(R.string.achievement_healthy));
                        prefs.edit().putBoolean("achievement_healthy", true).apply();
                    }
                }

            int recordActivityOneDay = db.getRecord();

            if (!prefs.getBoolean("achievement_its_a_sprint_not_a_marathon", false)) {
                if (recordActivityOneDay >= 150) {
                    unlockAchievement(gc, context.getString(R.string.achievement_its_a_sprint_not_a_marathon));
                    prefs.edit().putBoolean("achievement_its_a_sprint_not_a_marathon", true).apply();
                }
            }
            if (!prefs.getBoolean("achievement_maniac", false)) {
                if (recordActivityOneDay >= 720) {
                    unlockAchievement(gc, context.getString(R.string.achievement_maniac));
                    prefs.edit().putBoolean("achievement_maniac", true).apply();
                }
            }

            updateAverageLeaderboard(gc, context, average);


            updateTotalLeaderboard(gc, context, totalminutes);

            updateOneDayLeaderboard(gc, context, recordActivityOneDay);

            db.close();
        }
    }

    private static void unlockAchievement(GoogleApiClient gc, String achivmentName) {
        Games.Achievements.unlock(gc, achivmentName);
    }
}
