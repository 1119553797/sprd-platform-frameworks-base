package android.theme;

import android.theme.ThemeInfo;
/**
 * Central application service that provides theme management.
 */
interface IThemeManager {
    List<ThemeInfo> getThemes();
    boolean setTheme(in ThemeInfo info);
    boolean unsetTheme(in ThemeInfo info);
    ThemeInfo getAppliedTheme(String resDir);
}
