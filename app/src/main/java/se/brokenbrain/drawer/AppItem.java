package se.brokenbrain.drawer;

import android.content.ComponentName;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.VectorDrawable;

import android.util.Log;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.NinePatchDrawable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.content.Context;

public class AppItem {
	private String _name;
	private Drawable _iconDrawable;
	private Intent _intent;
	private String _packageName;
	private String _activityName;
	private boolean _isFolder = false;
	private long _parent = 0;
	private long _rowId;


	Bitmap drawableToBitmap(Drawable drawable) {
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable)drawable).getBitmap();
		} else if (drawable instanceof NinePatchDrawable) {
			Bitmap bitmap = Bitmap.createBitmap(
					drawable.getIntrinsicWidth(),
					drawable.getIntrinsicHeight(),
					drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(bitmap);
			drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
			drawable.draw(canvas);
			return bitmap;
		} else if (OS.isAndroidOreo() && drawable instanceof AdaptiveIconDrawable || drawable instanceof VectorDrawable) {
			final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(bmp);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
			return bmp;
			/*Drawable backgroundDr = ((AdaptiveIconDrawable) drawable).getBackground();
			Drawable foregroundDr = ((AdaptiveIconDrawable) drawable).getForeground();

			Drawable[] drr = new Drawable[2];
			drr[0] = backgroundDr;
			drr[1] = foregroundDr;

			LayerDrawable layerDrawable = new LayerDrawable(drr);

			int width = layerDrawable.getIntrinsicWidth();
			int height = layerDrawable.getIntrinsicHeight();

			Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

			Canvas canvas = new Canvas(bitmap);

			layerDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			layerDrawable.draw(canvas);

			return bitmap;*/
		}

		Log.d("Drawer", "drawableToBitmap failed for drawable type: " + drawable.getClass().getName());
		return null;
	}

	public AppItem(ResolveInfo rInfo, PackageManager pm, Context ctx) {
		_name = rInfo.activityInfo.loadLabel(pm).toString();
		_packageName = rInfo.activityInfo.packageName;
		_activityName = rInfo.activityInfo.name;
		//_iconDrawable = rInfo.activityInfo.loadIcon(pm);
		_iconDrawable = rInfo.activityInfo.applicationInfo.loadIcon(pm);
		{ // check size of icon drawable
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			Bitmap bitmap = drawableToBitmap(_iconDrawable);
			if (bitmap == null) {
				_iconDrawable = new BitmapDrawable();
			} else {
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

				ByteArrayInputStream istream = new ByteArrayInputStream(stream.toByteArray());

				BitmapFactory.Options options = new BitmapFactory.Options();
				// Query dimensions
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(istream, null, options);
				if (options.outWidth == -1) {
					Log.d("Drawer", "Could not get icon dimensions, title: " + _name);
				} else {
					int MAXSIZE = 144; // maximum icon size
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
						istream = new ByteArrayInputStream(stream.toByteArray());
						bmp = BitmapFactory.decodeStream(istream, null, options);
						_iconDrawable = new BitmapDrawable(ctx.getResources(), bmp);
					} catch (OutOfMemoryError ome) {
						_iconDrawable = new BitmapDrawable();
					}
				}
			}
		}

		ComponentName className = new ComponentName(
				rInfo.activityInfo.packageName,
				rInfo.activityInfo.name);

		_intent = new Intent(Intent.ACTION_MAIN);
		_intent.addCategory(Intent.CATEGORY_LAUNCHER);
		_intent.setComponent(className);
		_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
	}

	public AppItem(long id, String title, String packageName,
			String activityName, long parent, Drawable icon,
			boolean isFolder, long rowId) {
		_name = title;
		_packageName = packageName;
		_activityName = activityName;
		_iconDrawable = icon;
		_isFolder = isFolder;
		_parent = parent;
		_rowId = rowId;

		ComponentName className = new ComponentName(packageName, activityName);

		_intent = new Intent(Intent.ACTION_MAIN);
		_intent.addCategory(Intent.CATEGORY_LAUNCHER);
		_intent.setComponent(className);
		_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
	}


	public String getName() {
		return _name;
	}

	public Drawable getIconDrawable() {
		return _iconDrawable;
	}

	public Intent getIntent() {
		return _intent;
	}

	public String getPackageName() {
		return _packageName;
	}

	public String getActivityName() {
		return _activityName;
	}

	public boolean isFolder() {
		return _isFolder;
	}

	public long getParent() {
		return _parent;
	}

	public long getId() {
		return _rowId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || o.getClass() != this.getClass()) {
			return false;
		}

		if (!this.isFolder() && !((AppItem)o).isFolder() &&
				((AppItem)o).getPackageName().equals(this.getPackageName()) &&
				((AppItem)o).getActivityName().equals(this.getActivityName())) {
			return true;
		}
		if ( (this.isFolder() && ((AppItem)o).isFolder()) &&
				this.getId() ==((AppItem)o).getId()) {
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return this.getName();
	}
}
