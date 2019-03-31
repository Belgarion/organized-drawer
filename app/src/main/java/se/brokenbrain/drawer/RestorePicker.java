package se.brokenbrain.drawer;

import java.util.List;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import android.view.View;

import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;


import android.util.Log;

import java.io.File;


import android.app.AlertDialog;
import android.content.DialogInterface;

public class RestorePicker extends Activity {
	private class FileItem {
		private File file;

		public FileItem(File file) {
			this.file = file;
		}

		public File getFile() {
			return file;
		}

		public String toString() {
			if (file.equals(curDir.getFile().getParentFile())) {
				return "..";
			}
			return file.getName();
		}
	}

	private class FileListAdapter extends ArrayAdapter<FileItem> {
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
				convertView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
			}

			if (item == null) {
				Log.w("RestorePicker.FileListAdapter", "Item is null!");
				return convertView;
			}

			convertView.setTag(item);

			TextView tv = (TextView)convertView.findViewById(android.R.id.text1);

			tv.setText(item.toString());

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
	}

	private String path;

	private ListView mListView;
	private Button upButton;

	private FileItem curDir;
	private List<FileItem> dirEntries;
	private FileListAdapter adapter;

	private Context mCtx;
	private LayoutInflater layoutInflater;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.icon_picker);

		mCtx = this;
		this.layoutInflater = LayoutInflater.from(mCtx);

		mListView = (ListView)findViewById(R.id.folder_list);
		upButton = (Button)findViewById(R.id.up);

		File f = new File("/sdcard");
		if (f.canRead()) {
			curDir = new FileItem(f);
		} else {
			curDir = new FileItem(new File("/"));
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
			File[] files = curDir.getFile().listFiles();
			// files is null if file is not a directory or IO error occured
			if (files == null) return;

			this.curDir = dir;

			this.dirEntries.clear();

			for (File file : curDir.getFile().listFiles()) {
				if (file.canRead()) {
					dirEntries.add(new FileItem(file));
				}
			}

			adapter = new FileListAdapter(this, dirEntries);
			mListView.setAdapter(adapter);
		} else {
			path = dir.getFile().getAbsolutePath();
			View confirm_restore_dialog = layoutInflater.inflate(R.layout.confirm_restore_dialog, null);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(this.getString(R.string.restore_this))
				.setCancelable(true)
				.setView(confirm_restore_dialog)
				.setPositiveButton(this.getString(R.string.yes),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Bundle bundle = new Bundle();
							bundle.putString("path", path);

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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) { // {{{
		Log.d("Resotre picker", "onKeyDown");
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR
				&& keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			onBackPressed();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	} // }}}

	@Override
	public void onBackPressed() { // {{{
		Log.d("Restore picker", "onbackpressed");
		setResult(RESULT_CANCELED);
		finish();
	} // }}}
}
