package se.brokenbrain.drawer;

import android.widget.SeekBar;
import android.preference.DialogPreference;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.util.Log;

public class SliderPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
	private static final String androidns = "http://schemas.android.com/apk/res/android";

	private SeekBar seekBar;
	private TextView txtValue, txtTitle;
	private Context mCtx;

	private String dialogMessage, suffix;
	private int mDefault, mMax, mValue = 0;

	public SliderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mCtx = context;

		dialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
		suffix = attrs.getAttributeValue(androidns, "text");
		mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
		mMax = attrs.getAttributeIntValue(androidns, "max", 100);
	}

	@Override
	protected View onCreateDialogView() {
		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(mCtx);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(5,5,5,5);

		txtTitle = new TextView(mCtx);
		if (dialogMessage != null) {
			txtTitle.setText(dialogMessage);
		}
		layout.addView(txtTitle);

		txtValue = new TextView(mCtx);
		txtValue.setGravity(Gravity.CENTER_HORIZONTAL);
		txtValue.setTextSize(32);
		params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(txtValue, params);

		seekBar = new SeekBar(mCtx);
		seekBar.setOnSeekBarChangeListener(this);
		layout.addView(seekBar, new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.FILL_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));

		if (shouldPersist()) {
			try {
				mValue = getPersistedInt(mDefault);
			} catch (Throwable t) {
				Log.d("SliderPreference", "Unable to load persisted value, using " + mDefault + " instead");
			}
		}

		seekBar.setMax(mMax);
		seekBar.setProgress(mValue);
		return layout;
	}

	@Override
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		seekBar.setMax(mMax);
		seekBar.setProgress(mValue);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue) {
		super.onSetInitialValue(restore, defaultValue);
		if (restore) {
			try {
				mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
			} catch (Throwable t) {
				if (defaultValue != null) {
					mValue = (Integer)defaultValue;
				} else {
					mValue = 0;
				}
			}
		} else if (defaultValue != null) {
			mValue = (Integer)defaultValue;
		} else {
			mValue = 0;
		}
	}

	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
		String t = String.valueOf(value);
		txtValue.setText(suffix == null ? t : t.concat(suffix));
		if (shouldPersist()) {
			persistInt(value);
		}
		callChangeListener(new Integer(value));
	}

	public void onStartTrackingTouch(SeekBar seek) {}
	public void onStopTrackingTouch(SeekBar seek) {}

	public void setMax(int max) { mMax = max; }
	public int getMax() { return mMax; }

	public void setProgress(int progress) {
		mValue = progress;
		if (seekBar != null) {
			seekBar.setProgress(progress);
		}
	}

	public int getProgress() { return mValue; }
}
