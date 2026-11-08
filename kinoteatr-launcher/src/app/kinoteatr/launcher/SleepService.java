package app.kinoteatr.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
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

    /**
     * TCL firmware switches accessibility services off at every boot. If the launcher was given
     * WRITE_SECURE_SETTINGS once (adb shell pm grant ...), it switches its sleep service back on
     * by itself right when a timer is chosen. Returns true when the service is (now) enabled.
     */
    static boolean ensureEnabled(Context c) {
        if (enabled(c)) return true;
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return false;
        try {
            String svc = c.getPackageName() + "/" + SleepService.class.getName();
            String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String value = TextUtils.isEmpty(cur) || "null".equals(cur) ? svc : cur + ":" + svc;
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return enabled(c);
        } catch (SecurityException e) {
            return false;
        }
    }

    static boolean sleepNow() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
