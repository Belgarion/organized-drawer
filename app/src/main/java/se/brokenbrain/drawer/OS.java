package se.brokenbrain.drawer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.app.WallpaperManager;
import android.graphics.drawable.Drawable;

public class OS {
	private static int sdkVersion;
	static {
		try {
			sdkVersion = Integer.parseInt(android.os.Build.VERSION.SDK);
		} catch (Exception ex) {
		}
	}


	public static boolean isAndroid20() {
		return sdkVersion >= 5;
	}

	public static boolean isAndroid21() {
		return sdkVersion >= 7;
	}

	public static boolean isAndroid22() {
		return sdkVersion >= 8;
	}

	public static boolean isAndroid30() {
		return sdkVersion >= 11;
	}

	public static boolean isAndroidOreo() { return sdkVersion >= 26; }

	public static void invalidateOptionsMenu(Activity activity) {
		if (!isAndroid30()) {
			return;
		}

		try {
			Method m = activity.getClass().getMethod("invalidateOptionsMenu");
			m.invoke(activity);
		} catch (Exception ex) {
			Log.e("Drawer", "invalidateOptionsMenu", ex);
		}
	}

	public static Drawable getWallpaperDrawable(Activity activity) {
		try {
			if (OS.isAndroid20()) {
				Class classWallpaperManager = Class.forName("android.app.WallpaperManager");
				if (classWallpaperManager != null) {
					Method mGetInstance = classWallpaperManager.getDeclaredMethod("getInstance", Context.class);
					Object objWallpaperManager = mGetInstance.invoke(classWallpaperManager, activity);
					Method mGetDrawable = objWallpaperManager.getClass().getMethod("getDrawable");
					return (Drawable)mGetDrawable.invoke(objWallpaperManager);
				}
			} else {
				Method m = activity.getClass().getMethod("getWallpaper");
				return (Drawable)m.invoke(activity);
			}
		} catch (Exception ex) {
			Log.e("Drawer", "getWallpaperDrawable", ex);
		}
		return null;
	}

	public static Object getWallpaperInfo(Activity activity) {
		try {
			if (OS.isAndroid21()) {
				Class classWallpaperManager = Class.forName("android.app.WallpaperManager");
				if (classWallpaperManager != null) {
					Method mGetInstance = classWallpaperManager.getDeclaredMethod("getInstance", Context.class);
					Object objWallpaperManager = mGetInstance.invoke(classWallpaperManager, activity);
					Method mGetDrawable = objWallpaperManager.getClass().getMethod("getWallpaperInfo");
					return mGetDrawable.invoke(objWallpaperManager);
				}
			}
		} catch (Exception ex) {
			Log.e("Drawer", "getWallpaperInfo", ex);
		}
		return null;
	}
}
