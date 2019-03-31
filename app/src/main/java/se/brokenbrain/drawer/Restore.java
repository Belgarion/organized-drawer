package se.brokenbrain.drawer;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.util.Log;

public class Restore extends Activity {
	private TextView txt_status;
	private ProgressBar pb;
	private AppGridAdapter dbAdapter;
	private Button confirmButton;

	private Context mCtx;

	private final int ACTIVITY_PICKFILE = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.restore);

		mCtx = this;

		txt_status = (TextView)findViewById(R.id.txt_status);
		pb = (ProgressBar)findViewById(R.id.progressbar);

		confirmButton = (Button)findViewById(R.id.confirm);

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});

		dbAdapter = new AppGridAdapter(mCtx);


		Intent i = new Intent(getApplicationContext(), RestorePicker.class);
		startActivityForResult(i, ACTIVITY_PICKFILE);
	}

	@Override
	public void onDestroy() { /* {{{ */
		dbAdapter.close();
		super.onDestroy();
	} /* }}} */


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) { // {{{
		super.onActivityResult(requestCode, resultCode, intent);
		Bundle extras = new Bundle();
		if (intent != null) {
			extras = intent.getExtras();
		}

		switch (requestCode) {
			case ACTIVITY_PICKFILE:
				{
					if (resultCode == Activity.RESULT_CANCELED) {
						finish();
						break;
					}
					String path = extras.getString("path");
					RestoreTask task = new RestoreTask();
					task.path = path;
					task.execute();
					break;
				}
			default:
				break;
		}
	} // }}}

	class RestoreTask extends AsyncTask<Void, String, Void> { // {{{
		public String path = "";
		private boolean success = false;

		@Override
		protected void onPreExecute() {
			confirmButton.setEnabled(false);
			pb.setVisibility(View.VISIBLE);
		}

		@Override
		protected Void doInBackground(Void... v) {
			success = false;
			if (doRestore(path)) {
				success = true;
				refreshApps();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction("se.brokenbrain.drawer.PACKAGE_ADDED_OR_CHANGED");
			broadcastIntent.addCategory("se.brokenbrain.drawer.DEFAULT");
			mCtx.sendBroadcast(broadcastIntent);
			pb.setVisibility(View.GONE);
			if (success) {
				txt_status.setText("Restore completed.");
			}
			confirmButton.setEnabled(true);
		}

		@Override
		protected void onProgressUpdate(String... message) {
			txt_status.setText(message[0]);
		}

		private void refreshApps() {
			ArrayList<AppItem> results = new ArrayList<AppItem>();

			PackageManager pm = mCtx.getPackageManager();

			Intent intent = new Intent(Intent.ACTION_MAIN, null);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);

			int count = 0;
			List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED);
			for (ResolveInfo rInfo : list) {
				if (rInfo.activityInfo.applicationInfo.enabled) {
					AppItem item = new AppItem(rInfo, pm, mCtx);
					if (item.getActivityName().startsWith("se.brokenbrain.drawer")) {
						continue;
					}
					results.add(item);

					count += 1;
					publishProgress(mCtx.getString(R.string.refreshing_apps) + " (" + count + ")");
				}
			}

			publishProgress(mCtx.getString(R.string.updating_cache));
			dbAdapter.updateCache(results);
		}

		public boolean doRestore(String path) {
			publishProgress("Restoring");

			try {
				FileInputStream fstream = new FileInputStream(path);
				BufferedInputStream in = new BufferedInputStream(fstream);

				dbAdapter.deleteAll();

				HashMap<String, byte[]> item = new HashMap<String, byte[]>();
				while (in.available() > 0) {
					// Check if newline
					{
						in.mark(1);
						char c = (char)in.read();
						if (c == '\n') {

							Long id = Long.parseLong(new String(item.get("id")));
							String title = "";
							if (item.containsKey("title")) {
								title = new String(item.get("title"), "UTF8");
							}

							String packageName = "";
							if (item.containsKey("packageName")) {
								packageName = new String(item.get("packageName"), "UTF8");
							}

							String activityName = "";
							if (item.containsKey("activityName")) {
								activityName = new String(item.get("activityName"), "UTF8");
							}

							Long parent = Long.parseLong(new String(item.get("parent")));

							byte[] iconArray = null;
							if (item.containsKey("icon")) {
								iconArray = item.get("icon");
							}

							boolean useCustomIcon = ((char)item.get("useCustomIcon")[0]) == '1' ? true : false;

							byte[] customIconArray = null;
							if (item.containsKey("customIcon")) {
								customIconArray = item.get("customIcon");
							}

							boolean isFolder = ((char)item.get("isFolder")[0]) == '1' ? true : false;


							dbAdapter.insertApp(id, title, packageName, activityName,
									parent, iconArray, useCustomIcon, customIconArray, isFolder);

							//Log.d("Drawer", "Next item");
							item = new HashMap<String, byte[]>();
						} else {
							in.reset();
						}
					}
					if (in.available() == 0) {
						//Log.d("Drawer", "No more entries to restore.");
						break;
					}

					// Read column name
					String column = "";
					while (in.available() > 0) {
						in.mark(1);
						char c = (char)in.read();
						if (c >= '0' && c <= '9') {
							in.reset();
							break;
						}
						column += c;
					}
					//Log.d("Drawer", "Restore column: " + column);

					// Read data length
					int len = 0;
					String lenString = "";
					for (int i = 0; i < 10 && in.available() > 0; i++) {
						in.mark(1);
						char c = (char)in.read();
						if (!(c >= '0' && c <= '9')) {
							in.reset();
							len = Integer.parseInt(lenString);
							break;
						}
						lenString += c;
					}

					if (len == 0) {
						Log.d("Drawer", "Unable to read data, length == 0");
						break;
					}

					//Log.d("Drawer", "Restore length: " + len);

					// Read data type
					char type = (char)in.read();
					//Log.d("Drawer" , "Type: " + type);

					byte[] data = new byte[len];
					in.read(data, 0, len);

					/*
					if (type == 'H') {
						Log.d("Drawer", "Data: " + new String(data));
					} else if (type == 'B') {
						Log.d("Drawer", "Data: " + (char)data[0]);
					} else if (type == 'I') {
						Log.d("Drawer", "Data: " + Integer.parseInt(new String(data)));
					} else if (type == 'L') {
						Log.d("Drawer", "Data: " + Long.parseLong(new String(data)));
					}*/

					item.put(column, data);
				}

				in.close();

			} catch (Exception e) {
				Log.d("Error", "Error: " + e.getMessage());
				e.printStackTrace();
				publishProgress("Restore failed");
				return false;
			}
			return true;
		}
	} // }}}

}
