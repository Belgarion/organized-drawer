package se.brokenbrain.drawer;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;

import android.view.View;

import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;

import android.util.Log;

public class FolderEdit extends Activity {
	private EditText mTitleText;
	private long mRowId;
	private Drawable mIcon;
	private ImageButton iconButton;
	private Button defaultIconButton;
	private Context mCtx;

	private final static int ACTIVITY_PICKICON = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.folder_edit);

		mCtx = this;

		mTitleText = (EditText)findViewById(R.id.title);
		iconButton = (ImageButton)findViewById(R.id.folderIconButton);

		Button confirmButton = (Button)findViewById(R.id.confirm);
		defaultIconButton = (Button)findViewById(R.id.defaultIcon);

		mRowId = -1;
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String title = extras.getString(AppGridAdapter.KEY_TITLE);
			mRowId = extras.getLong(AppGridAdapter.KEY_ROWID);

			if (title != null) {
				mTitleText.setText(title);
			}

			byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_ICON);
			if (iconArray != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(iconArray);
				mIcon = Drawable.createFromStream(is, "appIcon");
			} else {
				mIcon = this.getResources().getDrawable(R.drawable.ic_menu_archive);
			}

			if (mIcon != null) {
				iconButton.setImageDrawable(mIcon);
			}
		}

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Bundle bundle = new Bundle();

				bundle.putString(AppGridAdapter.KEY_TITLE, mTitleText.getText().toString());

				if (mRowId > 0) {
					bundle.putLong(AppGridAdapter.KEY_ROWID, mRowId);
				}

				byte[] iconArray = null;

				if (mIcon != null) {
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					((BitmapDrawable)mIcon).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream);
					iconArray = stream.toByteArray();
				}

				bundle.putByteArray(AppGridAdapter.KEY_ICON, iconArray);

				Intent mIntent = new Intent();
				mIntent.putExtras(bundle);
				setResult(RESULT_OK, mIntent);
				finish();
			}
		});

		iconButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), IconPicker.class);
				startActivityForResult(i, ACTIVITY_PICKICON);
			}
		});

		defaultIconButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mIcon = mCtx.getResources().getDrawable(R.drawable.ic_menu_archive);
				iconButton.setImageDrawable(mIcon);
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) { // {{{
		super.onActivityResult(requestCode, resultCode, intent);
		if (intent == null) {
			return;
		}

		Bundle extras = intent.getExtras();

		switch (requestCode) {
			case ACTIVITY_PICKICON:
				{
					byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_ICON);
					if (iconArray != null) {
						ByteArrayInputStream is = new ByteArrayInputStream(iconArray);
						mIcon = Drawable.createFromStream(is, "appIcon");
						iconButton.setImageDrawable(mIcon);
					}
					break;
				}
			default:
				break;
		}
	} // }}}
}
