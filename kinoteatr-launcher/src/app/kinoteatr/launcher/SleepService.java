package app.kinoteatr.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sleep-timer fallback for TVs without device-admin support (the TCL C825 has none).
 * It reads nothing from the screen and subscribes to no events; its only job is the
 * "lock screen" global action, which on Android TV puts the set into standby.
 */
public class SleepService extends AccessibilityService {

    private static SleepService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    static boolean enabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(c.getPackageName() + "/");
    }

    static boolean sleepNow() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
