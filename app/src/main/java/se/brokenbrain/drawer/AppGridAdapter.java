package se.brokenbrain.drawer;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import android.content.ContentValues;
import android.content.Context;

import android.database.Cursor;
import android.database.SQLException;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.DataSetObserver;

import android.graphics.Bitmap;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.os.AsyncTask;

//import android.os.Debug;

public class AppGridAdapter extends BaseAdapter {
	private final LayoutInflater layoutInflater;

	private long _currentFolder;

	public static final String KEY_ROWID = "_id";
	public static final String KEY_TITLE = "title";
	public static final String KEY_PARENT = "parent";
	public static final String KEY_ICON = "icon";
	public static final String KEY_ISFOLDER = "isFolder";
	public static final String KEY_PACKAGENAME = "packageName";
	public static final String KEY_ACTIVITYNAME = "activityName";
	public static final String KEY_ISINSTALLED = "isInstalled";
	public static final String KEY_CUSTOMICON = "customIcon";
	public static final String KEY_USECUSTOMICON = "useCustomIcon";
	public static final String KEY_CUSTOMTITLE = "customTitle";

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	private HashMap<Long, AppItem> cache;
	private HashMap<Long, Long> idcache;
	public Vector<AppItem> selectedItems;
	private int textColor;

	private static final String DATABASE_CREATE =
		"create table apps ("
		+ "_id integer primary key autoincrement, "
		+ "title text not null, "
		+ "parent integer, "
		+ "icon blob, "
		+ "packageName text not null, "
		+ "activityName text not null, "
		+ "isFolder boolean not null, "
		+ "isInstalled boolean not null default true, "
		+ "customIcon blob default null, "
		+ "useCustomIcon boolean not null default false, "
		+ "customTitle text default null);";

	private static final String DATABASE_NAME = "data";
	private static final String DATABASE_TABLE = "apps";
	private static final int DATABASE_VERSION = 10;

	private final Context mCtx;
	private SharedPreferences preferences;
	private BroadcastReceiver updateReceiver;
	private boolean registeredForEvents = false;

	private boolean isClosing = false;

	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
			db.execSQL("CREATE INDEX IF NOT EXISTS isInstalled ON apps(isInstalled);");
			db.execSQL("CREATE INDEX IF NOT EXISTS isFolder ON apps(isFolder);");
			db.execSQL("CREATE INDEX IF NOT EXISTS parent on apps(parent);");
			db.execSQL("CREATE INDEX IF NOT EXISTS title on apps(title);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w("AppGridAdapter", "Upgrading database from version " + oldVersion +
					" to " + newVersion);

			switch (oldVersion) {
				case 1:
				case 2:
				case 3:
				case 4:
					db.execSQL("ALTER TABLE apps ADD COLUMN isInstalled boolean not null default true;");
				case 5:
				case 6:
				case 7:
					db.execSQL("CREATE INDEX IF NOT EXISTS isInstalled ON apps(isInstalled);");
					db.execSQL("CREATE INDEX IF NOT EXISTS isFolder ON apps(isFolder);");
					db.execSQL("CREATE INDEX IF NOT EXISTS parent on apps(parent);");
					db.execSQL("CREATE INDEX IF NOT EXISTS title on apps(title);");
				case 8:
					db.execSQL("ALTER TABLE apps ADD COLUMN customIcon blob default null;");
					db.execSQL("ALTER TABLE apps ADD COLUMN useCustomIcon boolean not null default false;");
				case 9:
					db.execSQL("ALTER TABLE apps ADD COLUMN customTitle text default null;");
				default:
					break;
			}
		}
	}

	public AppGridAdapter(final Context ctx, boolean registerForEvents) {
		this.mCtx = ctx;

		layoutInflater = LayoutInflater.from(ctx);

		open();

		_currentFolder = 0;

		cache = new HashMap<Long, AppItem>();
		idcache = new HashMap<Long, Long>();
		selectedItems = new Vector<AppItem>();


		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

		PreloadItemsTask t = new PreloadItemsTask();
		t.execute();


		if (registerForEvents) {
			this.registerDataSetObserver(new DataSetObserver() {
				public void onChanged() {
					cache.clear();
					idcache.clear();
					PreloadItemsTask t = new PreloadItemsTask();
					t.execute();
				}
				public void onInvalidated() {
					cache.clear();
					idcache.clear();
					PreloadItemsTask t = new PreloadItemsTask();
					t.execute();
				}
			});

			this.registeredForEvents = true;
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction("se.brokenbrain.drawer.PACKAGE_REMOVED");
			intentFilter.addAction("se.brokenbrain.drawer.PACKAGE_ADDED_OR_CHANGED");
			intentFilter.addCategory("se.brokenbrain.drawer.DEFAULT");

			updateReceiver = new BroadcastReceiver() {
				public void onReceive(Context context, Intent intent) {
					notifyDataSetChanged();
					cache.clear();
				}
			};
			mCtx.registerReceiver(updateReceiver, intentFilter);
		}
	}


	/**
	 * Opens an appgridadapter for use in services.
	 */
	public AppGridAdapter(final Context ctx) {
		this.mCtx = ctx;
		layoutInflater = null;
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
		cache = new HashMap<Long, AppItem>();
		idcache = new HashMap<Long, Long>();
		open();

		if (getItem(-1) == null) {
			Log.d("Drawer", "Creating hidden folder");
			ContentValues initialValues = new ContentValues();
			initialValues.put(KEY_ROWID, -1);
			initialValues.put(KEY_TITLE, "Hidden");
			initialValues.put(KEY_ISFOLDER, true);
			initialValues.put(KEY_PARENT, 0);
			initialValues.put(KEY_PACKAGENAME, "");
			initialValues.put(KEY_ACTIVITYNAME, "");
			long result = mDb.insert(DATABASE_TABLE, null, initialValues);
			notifyDataSetChanged();
		}

	}

	public void unRegisterReceivers() {
		if (registeredForEvents) {
			mCtx.unregisterReceiver(updateReceiver);
		}
	}

	/**
	 * Open the apps database.
	 * If it cannot be opened, recreate it.
	 */
	public AppGridAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();

		return this;
	}

	public void close() {
		unRegisterReceivers();
		isClosing = true;
		try {
			Thread.sleep(250);
		} catch (Exception e) {
		}
		mDbHelper.close();
		//Debug.stopMethodTracing();
	}

	public void clearCache() {
		cache.clear();
		notifyDataSetChanged();
	}

	public void setTextColor(int textColor) {
		this.textColor = textColor;
	}

	public void deleteAll() {
		mDb.execSQL("DELETE FROM apps;");
		cache.clear();
		notifyDataSetChanged();
	}

	class PreloadItemsTask extends AsyncTask<Void, String, Void> { // {{{
		@Override
		protected void onPreExecute() {
		}

		@Override
		protected Void doInBackground(Void... v) {
			createItemIdCache(true);
			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
		}
	} // }}}

	public void updateCache(List<AppItem> items) {
		if (!mDb.isOpen()) return;
		cache.clear();
		mDb.execSQL("UPDATE " + DATABASE_TABLE + " set " + KEY_ISINSTALLED + "=0 where " + KEY_ISFOLDER + "=0;");
		for (AppItem ai : items) {
			insertApp(ai, false);
		}
	}

	public int getCount() {
		if (!mDb.isOpen()) return 0;
		Cursor c = mDb.rawQuery("SELECT COUNT(*) as c FROM " + DATABASE_TABLE + " WHERE parent=? AND (isInstalled=1 OR isFolder=1) AND _id>=0",
				new String[] { "" + _currentFolder });
		c.moveToFirst();
		int count = c.getInt(0);
		c.close();
		return count;
	}

	public boolean removeApp(long rowId) {
		if (!mDb.isOpen()) return false;
		boolean result = mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		return result;
	}

	/**
	 * Return a Cursor positioned at the app matching rowId
	 *
	 * @param rowId id of app to retrieve
	 * @return Cursor positioned at app, if found.
	 * @throws SQLException if not found
	 */
	public Cursor fetchApp(long rowId) throws SQLException {
		if (mDb == null || !mDb.isOpen()) return null;
		Cursor mCursor = mDb.query(true, DATABASE_TABLE, new String[]
				{KEY_ROWID, KEY_TITLE, KEY_PARENT, KEY_ICON, KEY_PACKAGENAME, KEY_ACTIVITYNAME, KEY_ISFOLDER,
				KEY_CUSTOMICON, KEY_USECUSTOMICON, KEY_CUSTOMTITLE},
				KEY_ROWID + "=" + rowId, null, null, null, null, null);
		if (mCursor == null || !mCursor.moveToFirst()) {
			return null;
		}
		return mCursor;
	}

	/**
	 * Return a Cursor positioned at beginning of set.
	 *
	 * @param rowId id of app to retrieve
	 * @return Cursor positioned at app, if found.
	 * @throws SQLException if not found
	 */
	public Cursor fetchAllApps() throws SQLException {
		if (!mDb.isOpen()) return null;
		Cursor mCursor = mDb.query(true, DATABASE_TABLE, new String[]
				{KEY_ROWID, KEY_TITLE, KEY_PARENT, KEY_ICON, KEY_PACKAGENAME, KEY_ACTIVITYNAME, KEY_ISFOLDER,
				KEY_CUSTOMICON, KEY_USECUSTOMICON, KEY_CUSTOMTITLE},
				null, null, null, null, null, null);
		if (mCursor == null || !mCursor.moveToFirst()) {
			return null;
		}
		return mCursor;
	}

	/**
	 * Adds an app.
	 *
	 * @param title the title
	 * @param icon the icon
	 * @param packageName the packageName
	 * @param activityName the activityName
	 * @return rowId or -1 if failed
	 */
	public long insertApp(String title, Drawable icon, String packageName, String activityName, boolean notifyChanges) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_TITLE, title);

		byte[] iconArray = null;
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			((BitmapDrawable)icon).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream);
			iconArray = stream.toByteArray();
		} catch (Exception e) {
			Log.d("Drawer", "Something went wrong with loading applications icon.\n"
					+ "App: " + packageName + " (" + title + ")\n"
					+ "Exception: " + e);
		}

		initialValues.put(KEY_ICON, iconArray);
		initialValues.put(KEY_PACKAGENAME, packageName);
		initialValues.put(KEY_ACTIVITYNAME, activityName);
		initialValues.put(KEY_ISINSTALLED, true);

		long rowId, result;
		if ((rowId = getIdForPackage(packageName, activityName)) != -1) {
			// Already exists in database,
			// update data.
			result = mDb.update(DATABASE_TABLE, initialValues, KEY_ROWID + "=" + rowId, null);
		} else {
			initialValues.put(KEY_PARENT, 0);
			initialValues.put(KEY_ISFOLDER, false);

			result = mDb.insert(DATABASE_TABLE, null, initialValues);
		}

		if (notifyChanges) {
			notifyDataSetChanged();
		}
		return result;
	}

	/**
	 * Adds an app.
	 *
	 * @param id rowId
	 * @param title the title
	 * @param packageName the packageName
	 * @param activityName the activityName
	 * @param parent parent id
	 * @param iconArray the icon
	 * @param useCustomIcon use custom icon
	 * @param customIconArray the custom icon
	 * @param isFolder if item is a folder
	 * @return rowId or -1 if failed
	 */
	public long insertApp(long id, String title, String packageName, String activityName, long parent,
			byte[] iconArray, boolean useCustomIcon, byte[] customIconArray, boolean isFolder) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_ROWID, id);
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_PACKAGENAME, packageName);
		initialValues.put(KEY_ACTIVITYNAME, activityName);
		initialValues.put(KEY_PARENT, parent);
		initialValues.put(KEY_ICON, iconArray);
		initialValues.put(KEY_USECUSTOMICON, useCustomIcon);
		initialValues.put(KEY_CUSTOMICON, customIconArray);
		initialValues.put(KEY_ISFOLDER, isFolder);
		initialValues.put(KEY_ISINSTALLED, true);

		long result = mDb.insert(DATABASE_TABLE, null, initialValues);

		notifyDataSetChanged();
		return result;
	}

	public long insertApp(AppItem ai, boolean notifyChanges) {
		return insertApp(ai.getName(), ai.getIconDrawable(), ai.getPackageName(), ai.getActivityName(), notifyChanges);
	}

	/**
	 * Change icon
	 *
	 * @param rowId id of app.
	 * @param iconArray byte[] of new icon.
	 * @return true if succesful, false otherwise.
	 */
	public boolean changeIcon(long rowId, byte[] iconArray) {
		ContentValues args = new ContentValues();
		args.put(KEY_CUSTOMICON, iconArray);
		if (iconArray == null) {
			args.put(KEY_USECUSTOMICON, false);
		} else {
			args.put(KEY_USECUSTOMICON, true);
		}

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		cache.remove(Long.valueOf(rowId));
		return result;
	}

	/**
	 * Change title
	 *
	 * @param rowId id of app.
	 * @param title string of new title.
	 * @return true if succesful, false otherwise.
	 */
	public boolean changeTitle(long rowId, String title) {
		ContentValues args = new ContentValues();
		args.put(KEY_CUSTOMTITLE, title);

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		cache.remove(Long.valueOf(rowId));
		return result;
	}


	/**
	 * Create folder
	 *
	 * @param title foldername
	 * @return rowId or -1 if failed
	 */
	public long createFolder(String title, byte[] iconArray) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_ICON, iconArray);
		initialValues.put(KEY_ISFOLDER, true);
		initialValues.put(KEY_PARENT, _currentFolder);
		initialValues.put(KEY_PACKAGENAME, "");
		initialValues.put(KEY_ACTIVITYNAME, "");

		long result = mDb.insert(DATABASE_TABLE, null, initialValues);
		notifyDataSetChanged();
		return result;
	}

	/**
	 * Updates folder
	 *
	 * @param rowId id of folder
	 * @param title new title
	 * @return true if succesful, false otherwise
	 */
	public boolean updateFolder(long rowId, String title) {
		ContentValues args = new ContentValues();
		args.put(KEY_TITLE, title);

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		cache.remove(Long.valueOf(rowId));
		return result;
	}

	/**
	 * Updates folder
	 *
	 * @param rowId id of folder.
	 * @param title new title.
	 * @param iconArray byte[] of new icon.
	 * @return true if succesful, false otherwise.
	 */
	public boolean updateFolder(long rowId, String title, byte[] iconArray) {
		ContentValues args = new ContentValues();
		args.put(KEY_TITLE, title);
		args.put(KEY_ICON, iconArray);

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		cache.remove(Long.valueOf(rowId));
		return result;
	}

	/**
	 * Delete the folder associated with id
	 *
	 * @param rowId id of folder to delete.
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteFolder(long rowId) {
		if (rowId < 0) {
			return false;
		}

		AppItem ai = (AppItem)getItem((int)rowId);

		ContentValues args = new ContentValues();
		args.put(KEY_PARENT, ai.getParent());

		mDb.update(DATABASE_TABLE, args, KEY_PARENT + "=" + rowId, null);
		mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null);
		notifyDataSetChanged();
		return true;
	}

	/**
	 * Returns id of current folder
	 *
	 * @return id of current folder
	 */
	public long getCurrentFolder() {
		return _currentFolder;
	}

	/**
	 * Goes up one folder, unless we already are at the root
	 *
	 * @return true if successful false otherwise
	 */
	public boolean moveUp() {
		AppItem ai = (AppItem)getItem((int)_currentFolder);
		long lastFolder = _currentFolder;
		if (ai == null) {
			// Current folder has gone missing
			_currentFolder = 0;
		} else {
			_currentFolder = ai.getParent();
		}
		notifyDataSetChanged();
		return _currentFolder != lastFolder;
	}

	/**
	 * Changes folder
	 *
	 * @param folderId folder id
	 * @return true if successful false otherwise.
	 */
	public boolean changeFolder(long id) {
		long lastFolder = _currentFolder;
		_currentFolder = id;
		notifyDataSetChanged();
		return _currentFolder != lastFolder;
	}

	public ArrayList<AppItem> getFolderList(long parent) {
		ArrayList<AppItem> list = new ArrayList<AppItem>();

		Cursor c = mDb.query(DATABASE_TABLE, new String[] {KEY_ROWID},
				"parent=? AND isFolder=1", new String[] {"" + parent}, null, null,
				KEY_TITLE + " COLLATE LOCALIZED ASC");

		c.moveToFirst();

		while (!c.isAfterLast()) {
			list.add((AppItem)getItem(c.getInt(0)));
			c.moveToNext();
		}

		c.close();
		return list;
	}

	/**
	 * Returns the title of the current folder
	 *
	 * @return title of current folder
	 */
	public String getCurrentFolderTitle() {
		AppItem ai = (AppItem)getItem((int)_currentFolder);
		return ai.getName();
	}

	/**
	 * Sets the parent for entry.
	 *
	 * @param rowId id of folder or app
	 * @param parent new parent
	 * @return true if succesful, false otherwise
	 */
	public boolean setParent(long rowId, long parent) {
		ContentValues args = new ContentValues();
		args.put(KEY_PARENT, parent);

		// return false if we try to create a circular dependency
		if (isChild(parent, rowId)) return false;

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
		notifyDataSetChanged();
		return result;
	}

	/**
	 * Checks if an item is a child of parent
	 *
	 * @param rowId id of folder or app
	 * @param parent id of parent
	 * @return true if parent is an ancestor to rowId
	 */
	private boolean isChild(long rowId, long searchedParent, long depth) {
		if (depth > 20) return false; // circular dependency

		// we consider a rowId to be the child of itself
		if (rowId == searchedParent) return true;

		// if rowId is the root folder or a special folder then it is not a child
		if (rowId <= 0) return false;

		Cursor c = fetchApp(rowId);

		// if rowId does not exist then it is not a child of searchedParent
		if (c == null) return false;

		long parent = c.getLong(c.getColumnIndexOrThrow(KEY_PARENT));
		c.close();

		// if rowId's parent is searchedParent then it is a direct child
		if (parent == searchedParent) return true;

		// check if rowId' parent is a child of searchedParent
		return isChild(parent, searchedParent, depth+1);
	}
	public boolean isChild(long rowId, long searchedParent) {
		return isChild(rowId, searchedParent, 0);
	}

	/**
	 * Sets the parent for all selected items.
	 *
	 * @param parent new parent
	 * @return true if succesful, false otherwise
	 */
	public boolean batchMove(long parent) {
		ContentValues args = new ContentValues();
		args.put(KEY_PARENT, parent);

		boolean result = true;
		for (AppItem ai : selectedItems) {
			if (isChild(parent, ai.getId())) {
				// Avoid creating circular dependencies
				continue;
			}

			if (!(mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + ai.getId(), null) > 0)) {
				result = false;
			}
		}
		clearSelections();
		notifyDataSetChanged();
		return result;
	}


	public boolean setUninstalled(String packageName) {
		ContentValues args = new ContentValues();
		args.put(KEY_ISINSTALLED, false);

		boolean result = mDb.update(DATABASE_TABLE, args, KEY_PACKAGENAME + "=\"" + packageName + "\"", null) > 0;
		notifyDataSetChanged();
		return result;
	}

	/**
	 * Returns the rowId for the activityname
	 *
	 * @param activityName
	 * @return rowId
	 */
	public long getIdForPackage(String packageName, String activityName) {
		Cursor c = mDb.query(DATABASE_TABLE,
				new String[] {KEY_ROWID},
				KEY_PACKAGENAME + "=? AND " + KEY_ACTIVITYNAME + "=?",
				new String[] {"" + packageName, "" + activityName}, null, null, null);
		if (c != null && c.moveToFirst()) {
			long id = c.getLong(0);
			c.close();
			return id;
		} else {
			if (c != null) { c.close(); }
			return -1;
		}
	}

	public Object getItem(int i) {
		return getItem(i, false);
	}

	public Object getItem(int i, boolean doCache) {
		AppItem ai = (AppItem)cache.get(Long.valueOf(i));
		long startTime = java.lang.System.currentTimeMillis();

		if (ai != null) {
			return ai;
		}

		Cursor c = fetchApp(i);
		if (c == null) {
			return null;
		}

		long id = c.getLong(c.getColumnIndexOrThrow(KEY_ROWID));
		String title = c.getString(c.getColumnIndexOrThrow(KEY_TITLE));
		String packageName = c.getString(c.getColumnIndexOrThrow(KEY_PACKAGENAME));
		String activityName = c.getString(c.getColumnIndexOrThrow(KEY_ACTIVITYNAME));
		long parent = c.getLong(c.getColumnIndexOrThrow(KEY_PARENT));
		boolean isFolder = c.getInt(c.getColumnIndexOrThrow(KEY_ISFOLDER)) > 0;


		Drawable icon = null;

		boolean useCustomIcon = c.getInt(c.getColumnIndexOrThrow(KEY_USECUSTOMICON)) > 0;
		if (useCustomIcon) {
			byte[] customIconArray = c.getBlob(c.getColumnIndexOrThrow(KEY_CUSTOMICON));
			if (customIconArray != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(customIconArray);
				icon = Drawable.createFromStream(is, "appIcon");
			}
		}

		if (icon == null) { // no custom icon, use theme icon
			if (preferences.getBoolean("useTheme", false) && !preferences.getString("themeName", "").equals("")) {
				String themeName = preferences.getString("themeName", "");
				String icon_activityName = (isFolder ? title : activityName).replace(".", "_").toLowerCase();
				try {
					Context themeCtx = mCtx.createPackageContext(themeName, Context.CONTEXT_IGNORE_SECURITY);
					int icon_id = themeCtx.getResources().getIdentifier(icon_activityName, "drawable", themeName);
					if (icon_id != 0) {
						icon = themeCtx.getResources().getDrawable(icon_id);
					}
				} catch (Exception ex) {
					Log.e("AppGridAdapter", "Unable to fetch icon from theme for: " + activityName);
				}
			}
		}

		if (icon == null) { // no custom icon, use app default
			byte[] iconArray = c.getBlob(c.getColumnIndexOrThrow(KEY_ICON));
			if (iconArray != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(iconArray);
				icon = Drawable.createFromStream(is, "appIcon");
			}
		}

		if (icon == null) { // no icon found in database, use default
			icon = mCtx.getResources().getDrawable(isFolder ? R.drawable.ic_menu_archive : R.drawable.icon);
		}

		String customTitle = c.getString(c.getColumnIndexOrThrow(KEY_CUSTOMTITLE));
		if (customTitle != null) {
			title = customTitle;
		}

		c.close();

		ai = new AppItem(id, title, packageName, activityName, parent, icon, isFolder, id);
		if (doCache) {
			cache.put(Long.valueOf(id), ai);
		}
		return ai;
	}

	/**
	 * Returns the id for the index.
	 * @return id of item, -1 if unsuccessful
	 */
	public long getItemId(int index) {
		long startTime = java.lang.System.currentTimeMillis();
		Long id = idcache.get(Long.valueOf(index));
		if (id != null) {
			return id;
		}

		Cursor mCursor = mDb.query(DATABASE_TABLE,
				new String[] {KEY_ROWID},
				"parent=? AND (isInstalled=1 OR isFolder=1) AND _id>=0", new String[] {"" + _currentFolder }, null, null,
				(preferences.getBoolean("foldersFirst", false) ? "isFolder DESC, " : "")
				+ "coalesce(" + KEY_CUSTOMTITLE + ", '')||coalesce(" + KEY_TITLE + ", '') COLLATE LOCALIZED ASC");

		id = Long.valueOf(-1);
		if (mCursor.getCount() > index) {
			mCursor.moveToPosition(index);
			id = mCursor.getLong(0);
		}
		mCursor.close();
		return id;
	}

	public void createItemIdCache(boolean doItemCache) {
		long curFolder = _currentFolder;
		idcache.clear();
		Cursor mCursor = mDb.query(DATABASE_TABLE,
				new String[] {KEY_ROWID},
				"parent=? AND (isInstalled=1 OR isFolder=1) AND _id>=0", new String[] {"" + _currentFolder }, null, null,
				(preferences.getBoolean("foldersFirst", false) ? "isFolder DESC, " : "")
				+ "coalesce(" + KEY_CUSTOMTITLE + ", '')||coalesce(" + KEY_TITLE + ", '') COLLATE LOCALIZED ASC");

		if (mCursor.getCount() <= 0) return;

		mCursor.moveToFirst();

		long index = 0;
		while (!isClosing && mDb.isOpen() && !mCursor.isAfterLast()) {
			Long id = mCursor.getLong(0);
			if (curFolder != _currentFolder) {
				break;
			}
			idcache.put(Long.valueOf(index), id);
			mCursor.moveToNext();
			index++;
			if (doItemCache) {
				getItem(id.intValue(), true);
			}
		}
		mCursor.close();
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		final AppItem item = (AppItem)getItem((int)getItemId(position));

		if (item == null) {
			Log.w("AppGridAdapter", "Item is null!");
			return convertView;
		}

		boolean hideLabels = preferences.getBoolean("hideLabels", false);
		boolean hideFolderLabels = preferences.getBoolean("hideFolderLabels", false);
		boolean hideLabel = (item.isFolder() && hideFolderLabels)
			|| (!item.isFolder() && hideLabels);


		AppGridViewContainer tag;
		if (convertView == null) {
			if (hideLabel) {
				convertView = layoutInflater.inflate(R.layout.appitem_without_labels, null);
			} else {
				convertView = layoutInflater.inflate(R.layout.appitem, null);
			}
			tag = new AppGridViewContainer(item,
					(TextView)convertView.findViewById(R.id.txt_appname),
					(ImageView)convertView.findViewById(R.id.appIcon));
		} else {
			tag = (AppGridViewContainer)convertView.getTag();
			if (hideLabel && tag.tv != null) {
				convertView = layoutInflater.inflate(R.layout.appitem_without_labels, null);
				tag = new AppGridViewContainer(item,
						(TextView)convertView.findViewById(R.id.txt_appname),
						(ImageView)convertView.findViewById(R.id.appIcon));
			} else if (!hideLabel && tag.tv == null) {
				convertView = layoutInflater.inflate(R.layout.appitem, null);
				tag = new AppGridViewContainer(item,
						(TextView)convertView.findViewById(R.id.txt_appname),
						(ImageView)convertView.findViewById(R.id.appIcon));
			}
		}

		tag.item = item;
		convertView.setTag(tag);

		if (!hideLabel) {
			tag.tv.setText(item.getName());
			tag.tv.setTextColor(textColor);
		}

		tag.iv.setImageDrawable(item.getIconDrawable());

		return convertView;
	}

	public void selectItem(AppItem item) {
		selectedItems.add(item);
		notifyDataSetChanged();
	}

	public void unselectItem(AppItem item) {
		selectedItems.remove(item);
		notifyDataSetChanged();
	}

	public void toggleSelect(AppItem item) {
		if (selectedItems.contains(item)) {
			selectedItems.remove(item);
		} else {
			selectedItems.add(item);
		}
		notifyDataSetChanged();
	}

	public void clearSelections() {
		selectedItems.clear();
		notifyDataSetChanged();
	}

	public void fixProblems() {
		Cursor c = fetchAllApps();
		if (c == null) {
			return;
		}
		while (!c.isAfterLast()) {
			long id = c.getLong(c.getColumnIndexOrThrow(AppGridAdapter.KEY_ROWID));
			if (!isChild(id, 0) && !isChild(id, -1)) {
				// If it is not a child of the default folder or the hidden folder then something is wrong
				ContentValues args = new ContentValues();
				// Put all inaccessible items in the default folder
				args.put(KEY_PARENT, 0);
				mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + id, null);
			}
			c.moveToNext();
		}
		c.close();
	}
}
