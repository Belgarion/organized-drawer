package se.brokenbrain.drawer;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;

import android.view.View;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;

import java.io.ByteArrayInputStream;
import android.graphics.drawable.Drawable;

import android.util.Log;

public class AppEdit extends Activity {
	private EditText mTitleText;
	private long mRowId;
	private Drawable mIcon;
	private Drawable mDefaultIcon;
	private ImageButton iconButton;
	private Button defaultIconButton;
	private Context mCtx;
	private byte[] mIconArray;
	private String title;

	private final static int ACTIVITY_PICKICON = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_edit);

		mCtx = this;

		mTitleText = (EditText)findViewById(R.id.title);
		iconButton = (ImageButton)findViewById(R.id.folderIconButton);

		Button confirmButton = (Button)findViewById(R.id.confirm);
		defaultIconButton = (Button)findViewById(R.id.defaultIcon);

		mRowId = -1;
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mRowId = extras.getLong(AppGridAdapter.KEY_ROWID);

			title = extras.getString(AppGridAdapter.KEY_TITLE);
			String customtitle = extras.getString(AppGridAdapter.KEY_CUSTOMTITLE);
			if (customtitle != null) {
				mTitleText.setText(customtitle);
			} else if (title != null) {
				mTitleText.setText(title);
			}

			byte[] iconArray = extras.getByteArray(AppGridAdapter.KEY_CUSTOMICON);
			mIconArray = iconArray;
			if (iconArray != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(iconArray);
				mIcon = Drawable.createFromStream(is, "appIcon");
			} else {
				//mIcon = this.getResources().getDrawable(R.drawable.icon);
				mIcon = null;
			}

			byte[] defaultIconArray = extras.getByteArray(AppGridAdapter.KEY_ICON);
			if (defaultIconArray != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(defaultIconArray);
				mDefaultIcon = Drawable.createFromStream(is, "appIcon");
			} else {
				mDefaultIcon = this.getResources().getDrawable(R.drawable.icon);
			}

			if (mIcon != null) {
				iconButton.setImageDrawable(mIcon);
			} else {
				iconButton.setImageDrawable(mDefaultIcon);
			}
		}

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Bundle bundle = new Bundle();


				if (!mTitleText.getText().toString().equals(title)) {
					bundle.putString(AppGridAdapter.KEY_CUSTOMTITLE, mTitleText.getText().toString());
				}

				if (mRowId > 0) {
					bundle.putLong(AppGridAdapter.KEY_ROWID, mRowId);
				}

				bundle.putByteArray(AppGridAdapter.KEY_CUSTOMICON, mIconArray);

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
				mIcon = mCtx.getResources().getDrawable(R.drawable.icon);
				iconButton.setImageDrawable(mDefaultIcon);
				mIconArray = null;
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
						mIconArray = iconArray;
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
