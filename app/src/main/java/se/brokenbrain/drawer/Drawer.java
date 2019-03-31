package se.brokenbrain.drawer;

/* {{{ Imports */
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;

import android.content.res.Configuration;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageInfo;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Build;

import android.util.Log;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import android.app.ProgressDialog;
import android.os.AsyncTask;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;

import android.net.Uri;
import android.view.WindowManager;
import android.view.Window;

import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
/* }}} */

public class Drawer extends Activity {
	private static final int ACTIVITY_ADDFOLDER = 0;
	private static final int ACTIVITY_EDITFOLDER = 1;
	private static final int ACTIVITY_SELECTFOLDER = 2;
	private static final int ACTIVITY_EDITAPP = 3;

	private static final int EDIT_FOLDER_ID = Menu.FIRST;
	private static final int SELECT_FOLDER_ID = EDIT_FOLDER_ID + 1;
	private static final int DELETE_FOLDER_ID = SELECT_FOLDER_ID + 1;
	private static final int EDIT_APP_ID = DELETE_FOLDER_ID + 1;
	private static final int ADD_SHORTCUT_ID = EDIT_APP_ID + 1;
	private static final int OPEN_MARKET_ID = ADD_SHORTCUT_ID + 1;
	private static final int CANCEL_BATCH_MOVE_ID = OPEN_MARKET_ID + 1;

	private BetterGridView gv;
	private ArrayList<AppItem> results = new ArrayList<AppItem>();

	private SharedPreferences prefs;
	private OnSharedPreferenceChangeListener prefsListener;

	private AppGridAdapter dbAdapter;

	private long deleteId = -1;

	private boolean batchMoveMode = false;

	private Activity mActivity;

	private long baseFolderId = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) { // {{{
		super.onCreate(savedInstanceState);
		mActivity = this;


		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		if (prefs.getBoolean("fullscreen", false)) {
			//requestWindowFeature(Window.FEATURE_NO_TITLE);
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		setContentView(R.layout.main);

		gv = (BetterGridView)findViewById(R.id.appgrid);

		gv.setNumColumns(-1);

		// Fix for when upgrading to newer with slider
		convertPrefsStringToInt("portraitColumns");
		convertPrefsStringToInt("landscapeColumns");
		// End fix

		dbAdapter = new AppGridAdapter(this, true);
		gv.setAdapter(dbAdapter);
		gv.setOnItemClickListener(gridClickListener);
		registerForContextMenu(gv);

		Bundle extras = getIntent().getExtras();
		if (extras != null && extras.containsKey("folderId")) {
			long folderId = extras.getLong("folderId");
			dbAdapter.changeFolder(folderId);
			baseFolderId = folderId;
		} else {
			dbAdapter.changeFolder(0);
			baseFolderId = 0;
		}

		prefsListener = new OnSharedPreferenceChangeListener() {
			public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
				int orientation = getResources().getConfiguration().orientation;

				if (key.equals("portraitColumns") && orientation == Configuration.ORIENTATION_PORTRAIT) {
					int columns = prefs.getInt(key, 0);

					if (columns <= 0) columns = -1;
					gv.setNumColumns(columns);
				} else if (key.equals("landscapeColumns") && orientation == Configuration.ORIENTATION_LANDSCAPE) {
					int columns = prefs.getInt(key, 0);

					if (columns <= 0) columns = -1;
					gv.setNumColumns(columns);
				} else if (key.equals("foldersFirst")) {
					try {
						dbAdapter.notifyDataSetChanged();
					} catch (Throwable t) {
					}
				} else if (key.equals("hideLabels") || key.equals("hideFolderLabels")) {
					try {
						dbAdapter.notifyDataSetChanged();
					} catch (Throwable t) {
					}
				} else if (key.equals("backgroundType")) {
					setBackground();
				} else if (key.equals("backgroundDimming")) {
					setBackground();
				} else if (key.equals("backgroundDimmingColor")) {
					setBackground();
				} else if (key.equals("backgroundImage")) {
					setBackground();
				} else if (key.equals("backgroundColor")) {
					setBackground();
				} else if (key.equals("fullscreen")) {
					setFullscreen();
				} else if (key.equals("useTheme") || key.equals("themeName")) {
					dbAdapter.clearCache();
				} else if (key.equals("textColor")) {
					setTextColor();
				}
			}
		};
		setFullscreen();

		prefs.registerOnSharedPreferenceChangeListener(prefsListener);

		onConfigurationChanged(getResources().getConfiguration());


		if (dbAdapter.getCount() == 0) {
			RefreshAppsTask task = new RefreshAppsTask();
			task.execute();
		}

	} // }}}

	public void setFullscreen() { // {{{
		WindowManager.LayoutParams attrs = getWindow().getAttributes();
		if (prefs.getBoolean("fullscreen", false)) {
			attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
			getWindow().setAttributes(attrs);
		} else {
			attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().setAttributes(attrs);
		}
	} // }}}

	@Override
	public void onDestroy() { /* {{{ */
		dbAdapter.close();
		super.onDestroy();
	} /* }}} */

	public void convertPrefsStringToInt(String key) { // {{{
		try {
			prefs.getInt(key, 0);
		} catch (Throwable t) {
			SharedPreferences.Editor eprefs = prefs.edit();
			try {
				eprefs.putInt(key, Integer.parseInt(prefs.getString(key, "0")));
				eprefs.commit();
			} catch (Throwable t2) {
				eprefs.putInt(key, 0);
				eprefs.commit();
			}
		}
	} // }}}

	@Override
	public void onConfigurationChanged(Configuration newConfig) { // {{{
		super.onConfigurationChanged(newConfig);

		int orientation = newConfig.orientation;
		int columns = -1;

		switch (orientation) {
			case Configuration.ORIENTATION_PORTRAIT:
				columns = prefs.getInt("portraitColumns", 0);
				break;
			case Configuration.ORIENTATION_LANDSCAPE:
				columns = prefs.getInt("landscapeColumns", 0);
				break;
			default:
				break;
		}

		if (columns <= 0) columns = -1;
		gv.setNumColumns(columns);

		setBackground();
		setTextColor();
	} // }}}

	public void setBackground() { // {{{
		int FLAG_SHOW_WALLPAPER = 0x00100000; // To make it compatible with android < 5


		// Set defaults first, to reduce duplicated code.
		gv.setDefaults();
		WindowManager.LayoutParams attrs = getWindow().getAttributes();
		attrs.flags &= (~FLAG_SHOW_WALLPAPER);
		getWindow().setAttributes(attrs);



		int dim = prefs.getInt("backgroundDimming", 50);
		gv.setBackgroundDimming(dim);
		int dimColor = prefs.getInt("backgroundDimmingColor", 0x00000000);
		gv.setBackgroundDimmingColor(dimColor);

		String bgType = prefs.getString("backgroundType", "solid");
		if (bgType.equals("wallpaper")) {
			if (OS.getWallpaperInfo(mActivity) == null) {
				gv.setBackgroundDimming(dim);
				gv.bgv_setBackground(OS.getWallpaperDrawable(mActivity));
			} else { // Live wallpaper
				getWindow().setBackgroundDrawable(new ColorDrawable(0));
				getWindow().setFormat(PixelFormat.TRANSLUCENT);

				attrs = getWindow().getAttributes();
				attrs.flags |= FLAG_SHOW_WALLPAPER;
				getWindow().setAttributes(attrs);
			}
		} else if (bgType.equals("solid")) {
			gv.setSolidBackgroundColor(prefs.getInt("backgroundColor", 0xFF000000));
		} else if (bgType.equals("image")) {
		}
	} // }}}

	public void setTextColor() { // {{{
		int textColor = prefs.getInt("textColor", 0xffffffff);
		dbAdapter.setTextColor(textColor);
		dbAdapter.notifyDataSetChanged();
	} // }}}

	public OnItemClickListener gridClickListener = new OnItemClickListener() { /* {{{ */
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			handleClick(v);
		}
	}; /* }}} */

	public void handleClick(View v) { // {{{
		AppItem item = (AppItem)((AppGridViewContainer)v.getTag()).item;
		Context context = getApplicationContext();

		if (batchMoveMode) {
			dbAdapter.toggleSelect(item);
		} else {
			if (item.isFolder()) {
				long rowId = item.getId();
				dbAdapter.changeFolder(rowId);
				setTitle("Organized Drawer - " + item.getName());
			} else {
				try {
					startActivity(item.getIntent());
					try {
						if (prefs.getBoolean("closeOnLaunch", false)) {
							finish();
						}
					} catch (Throwable t2) {
					}
				} catch (Throwable t) {
					Toast.makeText(context, "Unable to start activity, try refreshing apps", Toast.LENGTH_SHORT).show();
				}
			}
		}
	} // }}}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) { // {{{
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR
				&& keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			onBackPressed();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	} // }}}

	@Override
	public void onBackPressed() { // {{{
		if (dbAdapter.getCurrentFolder() != baseFolderId) {
			dbAdapter.moveUp();
			if (dbAdapter.getCurrentFolder() > 0) {
				setTitle("Organized Drawer - " + dbAdapter.getCurrentFolderTitle());
			} else {
				setTitle("Organized Drawer");
			}
			return;
		}
		//super.onBackPressed();
		finish();
	} // }}}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) { /* {{{ */
		boolean result = super.onCreateOptionsMenu(menu);
		if (!batchMoveMode) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.optionsmenu, menu);
		} else {
			menu.add(0, SELECT_FOLDER_ID, 0, R.string.select_folder);
			menu.add(0, CANCEL_BATCH_MOVE_ID, 0, R.string.cancel_batch_move);
		}
		return result;
	} /* }}} */


	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { /* {{{ */
		menu.clear();
		onCreateOptionsMenu(menu);
		return super.onPrepareOptionsMenu(menu);
	} /* }}} */

	@Override
	public boolean onOptionsItemSelected(MenuItem item) { // {{{
		switch (item.getItemId()) {
			case R.id.new_folder:
				addFolder();
				return true;
			case R.id.refresh:
				RefreshAppsTask task = new RefreshAppsTask();
				task.execute();
				return true;
			case R.id.preferences:
				preferences();
				return true;
			case R.id.show_hidden:
				dbAdapter.changeFolder(-1);
				return true;
			case R.id.batch_move:
				batchMoveMode = true;
				OS.invalidateOptionsMenu(this);
				return true;
			case CANCEL_BATCH_MOVE_ID:
				dbAdapter.clearSelections();
				batchMoveMode = false;
				OS.invalidateOptionsMenu(this);
				return true;
			case SELECT_FOLDER_ID:
				Intent i = new Intent(getApplicationContext(), FolderSelect.class);
				i.putExtra(AppGridAdapter.KEY_ROWID, -2);
				i.putExtra(AppGridAdapter.KEY_PARENT, dbAdapter.getCurrentFolder());
				startActivityForResult(i, ACTIVITY_SELECTFOLDER);
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	} // }}}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) { // {{{
		super.onCreateContextMenu(menu, v, menuInfo);
		long id = ((AdapterContextMenuInfo)menuInfo).id;
		AppItem ai = (AppItem)dbAdapter.getItem((int)id);

		if (ai.isFolder()) {
			menu.add(0, EDIT_FOLDER_ID, 0, R.string.edit_folder);
			menu.add(0, DELETE_FOLDER_ID, 0, R.string.delete_folder);
			menu.add(0, ADD_SHORTCUT_ID, 0, R.string.add_shortcut);
		} else {
			menu.add(0, SELECT_FOLDER_ID, 0, R.string.select_folder);
			menu.add(0, EDIT_APP_ID, 0, R.string.edit_app);
			menu.add(0, ADD_SHORTCUT_ID, 0, R.string.add_shortcut);
			menu.add(0, OPEN_MARKET_ID, 0, R.string.open_market);
		}
	} // }}}

	@Override
	public boolean onContextItemSelected(MenuItem item) { // {{{
		Context context = getApplicationContext();
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		long id = info.id;

		switch (item.getItemId()) {
			case EDIT_FOLDER_ID:
				{
					Cursor c = dbAdapter.fetchApp(id);

					Intent i = new Intent(context, FolderEdit.class);
					i.putExtra(AppGridAdapter.KEY_ROWID,
							c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ROWID)));
					i.putExtra(AppGridAdapter.KEY_TITLE,
							c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_TITLE)));
					i.putExtra(AppGridAdapter.KEY_ICON,
							c.getBlob(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ICON)));
					c.close();
					startActivityForResult(i, ACTIVITY_EDITFOLDER);
					return true;
				}
			case SELECT_FOLDER_ID:
				{
					Cursor c = dbAdapter.fetchApp(id);

					Intent i = new Intent(context, FolderSelect.class);
					i.putExtra(AppGridAdapter.KEY_ROWID, id);
					i.putExtra(AppGridAdapter.KEY_PARENT,
							c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_PARENT)));
					c.close();
					startActivityForResult(i, ACTIVITY_SELECTFOLDER);
					return true;
				}
			case DELETE_FOLDER_ID:
				{
					deleteId = id;
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setMessage(this.getString(R.string.sure_delete_folder))
						.setCancelable(true)
						.setPositiveButton(this.getString(R.string.yes),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									dbAdapter.deleteFolder(deleteId);
									deleteId = -1;
								}
							}
						)
						.setNegativeButton(this.getString(R.string.no),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									// Do nothing
									deleteId = -1;
								}
							}
						);
					AlertDialog alert = builder.create();
					alert.show();
					return true;
				}
			case EDIT_APP_ID:
				{
					Cursor c = dbAdapter.fetchApp(id);

					Intent i = new Intent(context, AppEdit.class);
					i.putExtra(AppGridAdapter.KEY_ROWID,
							c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ROWID)));
					i.putExtra(AppGridAdapter.KEY_ICON,
							c.getBlob(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ICON)));
					i.putExtra(AppGridAdapter.KEY_CUSTOMICON,
							c.getBlob(c.getColumnIndexOrThrow(AppGridAdapter.KEY_CUSTOMICON)));
					i.putExtra(AppGridAdapter.KEY_TITLE,
							c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_TITLE)));
					i.putExtra(AppGridAdapter.KEY_CUSTOMTITLE,
							c.getString(c.getColumnIndexOrThrow(AppGridAdapter.KEY_CUSTOMTITLE)));
					c.close();
					startActivityForResult(i, ACTIVITY_EDITAPP);
					return true;
				}
			case ADD_SHORTCUT_ID:
				{
					AppItem ai = (AppItem)dbAdapter.getItem((int)id);

					Intent shortcutIntent = new Intent();
					if (ai.isFolder()) {
						shortcutIntent.setClassName("se.brokenbrain.drawer", "se.brokenbrain.drawer.Drawer");
						shortcutIntent.putExtra("folderId", ai.getId());
					} else {
						shortcutIntent.setClassName(ai.getPackageName(), ai.getActivityName());
					}
					shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

					Intent intent = new Intent();
					intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
					intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, ai.getName());

					int mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
					Bitmap icon;
					if (ai.getIconDrawable() instanceof BitmapDrawable) {
						BitmapDrawable bd = (BitmapDrawable)ai.getIconDrawable();
						icon = Bitmap.createScaledBitmap(bd.getBitmap(), mIconSize, mIconSize, true);

						intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
					}
					intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
					sendBroadcast(intent);
					finish();
					return true;
				}
			case OPEN_MARKET_ID:
				{
					try {
						AppItem ai = (AppItem)dbAdapter.getItem((int)id);

						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse("market://details?id=" + ai.getPackageName()));
						startActivity(intent);
					} catch (Throwable t) {
						Toast.makeText(context, "Unable to open market", Toast.LENGTH_SHORT).show();
					}
					return true;
				}
			default:
				break;
		}
		return super.onContextItemSelected(item);
	} // }}}

	class RefreshAppsTask extends AsyncTask<Void, String, Void> { // {{{
		private ProgressDialog pDialog;

		@Override
		protected void onPreExecute() {
			pDialog = new ProgressDialog(Drawer.this);
			pDialog.setMessage(Drawer.this.getString(R.string.refreshing_apps));
			pDialog.setIndeterminate(true);
			pDialog.setCancelable(false);
			pDialog.show();
		}

		@Override
		protected Void doInBackground(Void... v) {
			refreshAppsNew();

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			dbAdapter.notifyDataSetChanged();
			pDialog.dismiss();
		}

		@Override
		protected void onProgressUpdate(String... message) {
			pDialog.setMessage(message[0]);
		}

		private void refreshApps() {
			results.clear();

			PackageManager pm = Drawer.this.getPackageManager();

			Intent intent = new Intent(Intent.ACTION_MAIN, null);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);

			int count = 0;
			List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED);
			for (ResolveInfo rInfo : list) {
				if (rInfo.activityInfo.applicationInfo.enabled) {
					Context context = getApplicationContext();
					AppItem item = new AppItem(rInfo, pm, context);
					if (item.getActivityName().startsWith("se.brokenbrain.drawer")) {
						continue;
					}
					results.add(item);

					count += 1;
					publishProgress(Drawer.this.getString(R.string.refreshing_apps) + " (" + count + ")");
				}
			}

			publishProgress(Drawer.this.getString(R.string.updating_cache));
			dbAdapter.updateCache(results);

			publishProgress(Drawer.this.getString(R.string.fixing_problems));
			dbAdapter.fixProblems();

			results.clear();
		}

		private void refreshAppsNew() {
			results.clear();

			int flags = 0;
			PackageManager pm = Drawer.this.getPackageManager();
			List<PackageInfo> applications = pm.getInstalledPackages(flags);
			if (applications == null) {
				applications = new ArrayList<PackageInfo>();
			}

			final Context context = getApplicationContext();
			int count = 0;
			for (PackageInfo pkgInfo : applications) {
				List<ResolveInfo> list = getLaunchResolveInfosForPackage(pkgInfo.packageName);
				for (ResolveInfo rInfo : list) {
					if (!rInfo.activityInfo.applicationInfo.enabled) continue; // app not enabled

					AppItem item = new AppItem(rInfo, pm, context);

					if (item.getActivityName().startsWith("se.brokenbrain.drawer")) {
						continue;
					}
					results.add(item);

					count += 1;
					publishProgress(Drawer.this.getString(R.string.refreshing_apps) + " (" + count + ")");
				}
			}

			publishProgress(Drawer.this.getString(R.string.updating_cache));
			dbAdapter.updateCache(results);

			publishProgress(Drawer.this.getString(R.string.fixing_problems));
			dbAdapter.fixProblems();

			results.clear();
		}

		private List<ResolveInfo> getLaunchResolveInfosForPackage(String packageName) {
			PackageManager pm = getPackageManager();
			Intent intentToResolve = new Intent(Intent.ACTION_MAIN, null);
			intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
			intentToResolve.setPackage(packageName);
			return pm.queryIntentActivities(intentToResolve, PackageManager.PERMISSION_GRANTED);
		}
	} // }}}

	private void addFolder() { /* {{{ */
		Intent i = new Intent(this, FolderEdit.class);
		startActivityForResult(i, ACTIVITY_ADDFOLDER);
	} /* }}} */

	private void preferences() { /* {{{ */
		Intent i = new Intent(this, Preferences.class);
		startActivity(i);
	} /* }}} */

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) { // {{{
		super.onActivityResult(requestCode, resultCode, intent);
		if (intent == null) {
			return;
		}

		Bundle extras = intent.getExtras();

		switch (requestCode) {
			case ACTIVITY_ADDFOLDER:
				{
					String title = extras.getString(AppGridAdapter.KEY_TITLE);
					byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_ICON);
					dbAdapter.createFolder(title, iconArray);
					break;
				}
			case ACTIVITY_EDITFOLDER:
				{
					Long rowId = extras.getLong(AppGridAdapter.KEY_ROWID);
					String title = extras.getString(AppGridAdapter.KEY_TITLE);
					byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_ICON);
					dbAdapter.updateFolder(rowId, title, iconArray);
					break;
				}
			case ACTIVITY_SELECTFOLDER:
				{
					Long rowId = extras.getLong(AppGridAdapter.KEY_ROWID);
					Long parent = extras.getLong(AppGridAdapter.KEY_PARENT);
					if (batchMoveMode) {
						dbAdapter.batchMove(parent);
						batchMoveMode = false;
					} else {
						dbAdapter.setParent(rowId, parent);
					}
					break;
				}
			case ACTIVITY_EDITAPP:
				{
					Long rowId = extras.getLong(AppGridAdapter.KEY_ROWID);
					String title = extras.getString(AppGridAdapter.KEY_CUSTOMTITLE);
					byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_CUSTOMICON);
					if (iconArray == null) {
						Log.w("Drawer", "iconArray is null");
					}
					dbAdapter.changeIcon(rowId, iconArray);
					dbAdapter.changeTitle(rowId, title);
					break;
				}
			default:
				break;
		}
	} // }}}
}
