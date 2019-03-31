package se.brokenbrain.drawer;

import java.util.ArrayList;

import android.content.res.Configuration;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;

import android.view.View;

import android.widget.ListView;
import android.widget.Button;

import android.widget.ArrayAdapter;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import android.util.Log;

public class FolderSelect extends Activity {
	private long _currentFolder;
	private long rowId;

	private ListView mListView;
	private Button confirmButton;
	private Button upButton;

	private AppGridAdapter dbAdapter;

	private ArrayList<AppItem> folders;
	private ArrayAdapter<AppItem> adapter;

	private Context mCtx;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.folder_select);

		mCtx = this;

		mListView = (ListView)findViewById(R.id.folder_list);
		confirmButton = (Button)findViewById(R.id.confirm);
		upButton = (Button)findViewById(R.id.up);
		dbAdapter = new AppGridAdapter(this);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			_currentFolder = extras.getLong(AppGridAdapter.KEY_PARENT);
			rowId = extras.getLong(AppGridAdapter.KEY_ROWID);
		}

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Bundle bundle = new Bundle();
				bundle.putLong(AppGridAdapter.KEY_PARENT, _currentFolder);
				bundle.putLong(AppGridAdapter.KEY_ROWID, rowId);

				Intent mIntent = new Intent();
				mIntent.putExtras(bundle);
				setResult(RESULT_OK, mIntent);

				finish();
			}
		});

		folders = dbAdapter.getFolderList(_currentFolder);

		adapter = new ArrayAdapter<AppItem>(this,
				android.R.layout.simple_list_item_1, folders);
		mListView.setAdapter(adapter);

		mListView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				_currentFolder = folders.get(position).getId();
				folders = dbAdapter.getFolderList(_currentFolder);
				adapter = new ArrayAdapter<AppItem>(mCtx,
						android.R.layout.simple_list_item_1, folders);
				mListView.setAdapter(adapter);

				if (_currentFolder != 0) {
					setTitle("Organized Drawer - " + ((AppItem)dbAdapter.getItem((int)_currentFolder)).getName());
				} else {
					setTitle("Organized Drawer");
				}
			}
		});

		upButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (_currentFolder != 0) {
					_currentFolder = ((AppItem)dbAdapter.getItem((int)_currentFolder)).getParent();
					folders = dbAdapter.getFolderList(_currentFolder);
					adapter = new ArrayAdapter<AppItem>(mCtx,
							android.R.layout.simple_list_item_1, folders);
					mListView.setAdapter(adapter);

					if (_currentFolder != 0) {
						setTitle("Organized Drawer - " + ((AppItem)dbAdapter.getItem((int)_currentFolder)).getName());
					} else {
						setTitle("Organized Drawer");
					}
				}
			}
		});

	}

	@Override
	public void onDestroy() {
		dbAdapter.close();
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

}
