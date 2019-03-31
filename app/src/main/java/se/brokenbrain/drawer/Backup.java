package se.brokenbrain.drawer;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.AsyncTask;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.util.Log;

public class Backup extends Activity {
	private TextView txt_status;
	private ProgressBar pb;
	private AppGridAdapter dbAdapter;
	private Button confirmButton;

	private Context mCtx;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.backup);

		mCtx = this;

		txt_status = (TextView)findViewById(R.id.txt_status);
		pb = (ProgressBar)findViewById(R.id.progressbar);
		pb.setIndeterminate(false);

		confirmButton = (Button)findViewById(R.id.confirm);

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});

		dbAdapter = new AppGridAdapter(mCtx);

		BackupTask task = new BackupTask();
		task.execute();
	}

	@Override
	public void onDestroy() { /* {{{ */
		dbAdapter.close();
		super.onDestroy();
	} /* }}} */


	class BackupTask extends AsyncTask<Void, String, Void> { // {{{
		@Override
		protected void onPreExecute() {
			confirmButton.setEnabled(false);
		}

		@Override
		protected Void doInBackground(Void... v) {
			doBackup();

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			confirmButton.setEnabled(true);
		}

		@Override
		protected void onProgressUpdate(String... message) {
			txt_status.setText(message[0]);
		}

		public void doBackup() {
			publishProgress("Backing up");
			try {
				String state = Environment.getExternalStorageState();

				boolean mExternalStorageWriteable = false;
				boolean mExternalStorageAvailable = false;

				if (Environment.MEDIA_MOUNTED.equals(state)) {
					mExternalStorageAvailable = mExternalStorageWriteable = true;
				} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
					mExternalStorageAvailable = true;
					mExternalStorageWriteable = false;
				} else {
					mExternalStorageAvailable = mExternalStorageWriteable = false;
				}

				if (!mExternalStorageAvailable) {
					// Show messagebox with error text "External storage not available"
					publishProgress("External storage not available");
					return;
				}

				if (!mExternalStorageWriteable) {
					// Show messagebox with error text "External storage not writeable"
					publishProgress("Unable to write to storage");
					return;
				}


				DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmm");
				Date today = Calendar.getInstance().getTime();

				File sd = Environment.getExternalStorageDirectory();
				File output = new File(sd.getAbsolutePath() + "/drawer-" + df.format(today) + ".bak");
				FileOutputStream fstream = new FileOutputStream(output);
				BufferedOutputStream out = new BufferedOutputStream(fstream);

				int count = 0;
				Cursor c = dbAdapter.fetchAllApps();
				pb.setMax(c.getCount());
				while (!c.isAfterLast()) {
					// Format
					// # <column><len><type><data> (for example: title5HHello )
					// Types:
					//   A: Byte Array
					//   B: Boolean
					//   H: String
					//   I: Integer
					//   L: Long
					long id = c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ROWID));
					String title = c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_TITLE));
					String packageName = c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_PACKAGENAME));
					String activityName = c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ACTIVITYNAME));
					long parent = c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_PARENT));
					boolean isFolder = c.getInt(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ISFOLDER)) > 0;

					byte[] iconArray = c.getBlob(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ICON));

					boolean useCustomIcon = c.getInt(c.getColumnIndexOrThrow(AppGridAdapter.KEY_USECUSTOMICON)) > 0;
					byte[] customIconArray = c.getBlob(c.getColumnIndexOrThrow(AppGridAdapter.KEY_CUSTOMICON));

					out.write(("id" + ((Long)id).toString().length() + "L" + id).getBytes());
					if (title.length() > 0) {
						byte[] titleBytes = title.getBytes("UTF8");
						out.write(("title" + titleBytes.length + "H").getBytes());
						out.write(titleBytes);
					}
					if (packageName.length() > 0) {
						byte[] pkgNameBytes = packageName.getBytes("UTF8");
						out.write(("packageName" + pkgNameBytes.length + "H").getBytes());
						out.write(pkgNameBytes);
					}
					if (activityName.length() > 0) {
						byte[] actNameBytes = activityName.getBytes("UTF8");
						out.write(("activityName" + actNameBytes.length + "H").getBytes());
						out.write(actNameBytes);
					}

					out.write(("parent" + ((Long)parent).toString().length() + "L" + parent).getBytes());
					out.write(("isFolder1B" + (isFolder ? 1 : 0)).getBytes());

					if (iconArray != null) {
						out.write(("icon" + iconArray.length + "A").getBytes());
						out.write(iconArray, 0, iconArray.length);
					}
					if (customIconArray != null) {
						out.write(("customIcon" + customIconArray.length + "A").getBytes());
						out.write(customIconArray, 0, customIconArray.length);
					}
					out.write(("useCustomIcon1B" + (useCustomIcon ? 1 : 0)).getBytes());
					out.write((byte)'\n');

					c.moveToNext();

					pb.setProgress(++count);
				}

				out.close();
				c.close();

				publishProgress("Backup saved to: " + output.getAbsolutePath());

			} catch (Exception e) {
				Log.d("Error", "Error: " + e.getMessage());
				publishProgress(e.getMessage());
				e.printStackTrace();
				// Messagebox: "Unable to backup (e.getMessage())"
			}
		}
	} // }}}
}
