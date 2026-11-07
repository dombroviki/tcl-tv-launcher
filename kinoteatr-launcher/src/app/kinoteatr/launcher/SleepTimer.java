package app.kinoteatr.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Sleep timer. The alarm uses the elapsed-realtime clock, so changing the time or time zone
 * doesn't move it, and it disappears with a reboot (the TV was off anyway).
 * When it fires, the screen is turned off through the device-admin "lock now" policy or, on
 * firmware without device admin, the accessibility "lock screen" action. On Android TV both are
 * the same as the remote's power button (standby).
 */
public class SleepTimer extends BroadcastReceiver {

    private static final String PREFS = "sleep";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, SleepTimer.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    static boolean adminActive(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(admin(c));
    }

    /** Device admin where the firmware supports it, otherwise our accessibility service. */
    static boolean canSleep(Context c) {
        return adminActive(c) || SleepService.enabled(c);
    }

    /** Starts (or replaces) the timer. */
    static void set(Context c, int minutes) {
        long at = SystemClock.elapsedRealtime() + minutes * 60_000L;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c));
        prefs(c).edit().putLong("at", at).putInt("minutes", minutes)
                .putLong("wallAt", System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c));
        prefs(c).edit().clear().apply();
    }

    /** Milliseconds left, or 0 when no timer is running. */
    static long left(Context c) {
        long at = prefs(c).getLong("at", 0);
        long left = at - SystemClock.elapsedRealtime();
        // A saved time from before a reboot lies in the past or far in the future: treat it as off.
        if (at == 0 || left <= 0 || left > 3 * 3600_000L) return 0;
        return left;
    }

    static int chosenMinutes(Context c) {
        return left(c) > 0 ? prefs(c).getInt("minutes", 0) : 0;
    }

    /** Wall-clock time the TV will turn off, for display. */
    static long wallAt(Context c) {
        return prefs(c).getLong("wallAt", 0);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        prefs(c).edit().clear().apply();
        if (adminActive(c)) {
            ((DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE)).lockNow();
        } else {
            SleepService.sleepNow();
        }
    }
}
