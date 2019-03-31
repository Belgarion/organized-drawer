package se.brokenbrain.drawer;

import android.preference.PreferenceActivity;
import android.os.Bundle;

public class Preferences extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.preferences);
	}
}
