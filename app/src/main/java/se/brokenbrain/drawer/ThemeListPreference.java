package se.brokenbrain.drawer;

import android.preference.ListPreference;
import android.view.View;
import android.widget.TextView;
import android.widget.ListView;
import android.content.Context;
import android.util.Log;
import android.util.AttributeSet;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import java.util.List;

public class ThemeListPreference extends ListPreference {
	private Context mCtx;
	private CharSequence[] entries; // human readable entries
	private CharSequence[] entryValues; // values

	final private PackageManager pm;

	public ThemeListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mCtx = context;

		pm = context.getPackageManager();


		Intent adwThemeIntent = new Intent("org.adw.launcher.THEMES", null);
		List<ResolveInfo> adwThemes = pm.queryIntentActivities(adwThemeIntent, PackageManager.MATCH_DEFAULT_ONLY);

		Intent lpThemeIntent = new Intent(Intent.ACTION_MAIN, null);
		lpThemeIntent.addCategory("com.fede.launcher.THEME_ICONPACK");
		List<ResolveInfo> lpThemes = pm.queryIntentActivities(lpThemeIntent, 0);

		entries = new CharSequence[adwThemes.size() + lpThemes.size()];
		entryValues = new CharSequence[adwThemes.size() + lpThemes.size()];

		int i = 0;
		for (ResolveInfo ri : adwThemes) {
			entries[i] = "[ADW] " + ri.activityInfo.loadLabel(pm).toString();
			entryValues[i] = ri.activityInfo.packageName;
			Log.d("ThemeListPreference", entryValues[i].toString());
			i++;
		}
		for (ResolveInfo ri : lpThemes) {
			entries[i] = "[LP] " + ri.activityInfo.loadLabel(pm).toString();
			entryValues[i] = ri.activityInfo.packageName;
			Log.d("ThemeListPreference", entryValues[i].toString());
			i++;
		}

		setEntries(entries);
		setEntryValues(entryValues);

	}
}
