package se.brokenbrain.drawer;

import java.util.List;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;

import android.view.View;

import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import android.util.Log;

import java.io.File;
import java.io.ByteArrayOutputStream;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class IconPicker extends Activity {
	private class FileItem { // {{{
		private Drawable icon;
		private File file;
		private final static int MAXSIZE = 144;

		public FileItem(File file) {
			this.file = file;
		}

		public File getFile() {
			return file;
		}

		public Drawable getIcon() {
			return icon;
		}

		public String toString() {
			if (file.equals(curDir.getFile().getParentFile())) {
				return "..";
			}
			return file.getName();
		}

		public void createDrawable() {
			if (file.isDirectory()) {
				icon = mCtx.getResources().getDrawable(R.drawable.ic_menu_archive);
			} else {
				BitmapFactory.Options options = new BitmapFactory.Options();
				// Query dimensions
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(file.getAbsolutePath(), options);
				if (options.outWidth == -1) {
					// Not an image, or other error
					icon = new BitmapDrawable();
					return;
				}

				int width = options.outWidth;
				int height = options.outHeight;
				if (!(width <= MAXSIZE && height <= MAXSIZE)) {
					int sampleSize = Math.round((float)width/(float)MAXSIZE);
					options.inSampleSize = sampleSize;
				}
				options.inJustDecodeBounds = false;

				// Load image for real this time.
				Bitmap bmp = null;
				try {
					bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
					icon = new BitmapDrawable(mCtx.getResources(), bmp);
				} catch (OutOfMemoryError ome) {
					icon = new BitmapDrawable();
				}
			}
		}
	} // }}}

	private class FileListAdapter extends ArrayAdapter<FileItem> { // {{{
		private Context mCtx;
		private List<FileItem> elems;
		private LayoutInflater layoutInflater;

		public FileListAdapter(Context context, List<FileItem> elems) {
			super(context, R.layout.icon_list_item, elems);
			this.mCtx = context;
			this.elems = elems;
			if (elems.size() > 0) {
				quicksort(0, elems.size() - 1);
			}
			this.layoutInflater = LayoutInflater.from(context);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final FileItem item = (FileItem)elems.get(position);
			if (convertView == null) {
				convertView = layoutInflater.inflate(R.layout.icon_list_item, null);
			}

			if (item == null) {
				Log.w("IconPicker.FileListAdapter", "Item is null!");
				return convertView;
			}

			convertView.setTag(item);

			TextView tv = (TextView)convertView.findViewById(R.id.txt_filename);
			ImageView iv = (ImageView)convertView.findViewById(R.id.icon);

			tv.setText(item.toString());

			if (item.getIcon() == null) {
				item.createDrawable();
			}
			iv.setImageDrawable(item.getIcon());

			return convertView;
		}

		private void quicksort(int low, int high) { // {{{
			//int pivot = low + (high-low)/2;
			String pivot = elems.get(low + (high-low)/2).getFile().getName().toLowerCase();
			int i = low, j = high;
			while (i <= j) {
				while (elems.get(i).getFile().getName().toLowerCase().compareTo(pivot) < 0) {
					i++;
				}

				while (elems.get(j).getFile().getName().toLowerCase().compareTo(pivot) > 0) {
					j--;
				}

				if (i <= j) {
					FileItem tmp = elems.get(i);
					elems.set(i, elems.get(j));
					elems.set(j, tmp);
					i++;
					j--;
				}
			}

			if (low < j)
				quicksort(low, j);
			if (i < high)
				quicksort(i, high);
		} // }}}
	} // }}}

	private ListView mListView;
	private Button upButton;

	private FileItem curDir;
	private List<FileItem> dirEntries;
	private FileListAdapter adapter;

	private Context mCtx;
	private LayoutInflater layoutInflater;

	private SharedPreferences prefs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.icon_picker);

		mCtx = this;
		this.layoutInflater = LayoutInflater.from(mCtx);

		mListView = (ListView)findViewById(R.id.folder_list);
		upButton = (Button)findViewById(R.id.up);

		prefs = PreferenceManager.getDefaultSharedPreferences(mCtx);

		File f = new File(prefs.getString("lastIconPath", "/sdcard"));
		if (f.canRead()) {
			curDir = new FileItem(f);
		} else {
			f = new File("/sdcard");
			if (f.canRead()) {
				curDir = new FileItem(f);
			} else {
				curDir = new FileItem(new File("/"));
			}
		}

		dirEntries = new ArrayList<FileItem>();
		browse(curDir);

		mListView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				browse(dirEntries.get(position));
			}
		});

		upButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (curDir.getFile().getParent() != null) {
					browse(new FileItem(curDir.getFile().getParentFile()));
				}
			}
		});
	}

	private void browse(final FileItem dir) {
		if (dir.getFile().isDirectory()) {
			this.curDir = dir;

			this.dirEntries.clear();

			File[] files = curDir.getFile().listFiles();
			if (files != null) {
				for (File file : curDir.getFile().listFiles()) {
					if (file.canRead()) {
						dirEntries.add(new FileItem(file));
					}
				}
			}

			adapter = new FileListAdapter(this, dirEntries);
			mListView.setAdapter(adapter);
		} else {
			View confirm_pick_icon_dialog = layoutInflater.inflate(R.layout.confirm_pick_icon_dialog, null);
			ImageView image = (ImageView)confirm_pick_icon_dialog.findViewById(R.id.image);
			image.setImageDrawable(dir.getIcon());

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(this.getString(R.string.use_this_icon))
				.setCancelable(true)
				.setView(confirm_pick_icon_dialog)
				.setPositiveButton(this.getString(R.string.yes),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Remember this dir
							SharedPreferences.Editor editPrefs = prefs.edit();
							editPrefs.putString("lastIconPath", curDir.getFile().getAbsolutePath());
							editPrefs.commit();

							// Change icon
							byte[] iconArray = null;
							if (dir.getIcon() != null) {
								ByteArrayOutputStream stream = new ByteArrayOutputStream();
								Bitmap bmp = ((BitmapDrawable)dir.getIcon()).getBitmap();
								if (bmp != null) {
									bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
									iconArray = stream.toByteArray();
								}
							}

							Bundle bundle = new Bundle();
							bundle.putByteArray(AppGridAdapter.KEY_ICON, iconArray);

							Intent mIntent = new Intent();
							mIntent.putExtras(bundle);
							setResult(RESULT_OK, mIntent);
							finish();
						}
					}
				)
				.setNegativeButton(this.getString(R.string.no),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing
						}
					}
				);
			AlertDialog alert = builder.create();
			alert.show();
		}
	}
}
