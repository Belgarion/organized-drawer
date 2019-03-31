package se.brokenbrain.drawer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.SoundEffectConstants;
import android.view.animation.GridLayoutAnimationController;
import android.widget.ListAdapter;
import android.widget.GridView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.view.Display;
import android.app.Activity;
import android.graphics.Color;

import android.util.Log;


public class BetterGridView extends GridView {

	private Bitmap background = null;
	private Bitmap dimmedBackground = null;
	private int backgroundDimming = 0;
	private Display display = null;
	private int backgroundColor = 0xFF000000;
	private int backgroundDimmingColor = 0x00000000;

    public BetterGridView(Context context) {
        super(context);
    }

    public BetterGridView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.gridViewStyle);
    }

    public BetterGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		display = ((Activity)context).getWindowManager().getDefaultDisplay();
    }

	public void setDefaults() {
		backgroundDimming = 0;
		background = null;
		dimmedBackground = null;
		backgroundColor = 0xFF000000;
		backgroundDimmingColor = 0x00000000;
		setCacheColorHint(0x00000000);
	}

	public boolean bgv_setBackground(Drawable drawable) {
		if (drawable instanceof BitmapDrawable) {
			bgv_setBackground( ((BitmapDrawable)drawable).getBitmap());
			return true;
		}
		return false;
	}

	public boolean bgv_setBackground(Bitmap background) {
		this.background = background;
		setBackgroundDimming(backgroundDimming);
		backgroundColor = 0xFF000000;
		return true;
	}

	public boolean setSolidBackground() {
		this.background = null;
		this.dimmedBackground = null;
		return true;
	}

	public boolean setSolidBackgroundColor(int color) {
		backgroundColor = color;
		setCacheColorHint(color);
		return true;
	}

	/**
	 * Run this before setBackground
	 */
	public void setBackgroundDimming(int dim) {
		backgroundDimming = dim;
		if (dim > 100) {
			backgroundDimming = 100;
		}

		if (background == null || background != null) return;
		if (background != null) {
			dimmedBackground = Bitmap.createBitmap(background.getWidth(), background.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(dimmedBackground);
			c.drawBitmap(background, 0, 0, null);
			int color = backgroundDimmingColor & 0x00FFFFFF;
			color |= ((int)(2.55 * backgroundDimming)) << 24;
			c.drawColor(color);
			background = null;
		}
	}

	public void setBackgroundDimmingColor(int color) {
		backgroundDimmingColor = color;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (dimmedBackground != null) {
			canvas.drawBitmap(dimmedBackground, 0, 0, null);
		} else if (backgroundColor != 0xFF000000) {
			canvas.drawColor(backgroundColor);
		} else {
			if (background != null) canvas.drawBitmap(background, 0, 0, null);
			int color = backgroundDimmingColor & 0x00FFFFFF;
			color |= ((int)(2.55 * backgroundDimming)) << 24;
			canvas.drawColor(color);
		}


		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.WHITE);
		for (int i = 0; i < getChildCount(); i++) {
			AppItem item = (((AppGridViewContainer)getChildAt(i).getTag()).item);
			if (((AppGridAdapter)getAdapter()).selectedItems.contains(((AppGridViewContainer)getChildAt(i).getTag()).item)) {
				View child = getChildAt(i);
				canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), paint);
			}
		}
		super.dispatchDraw(canvas);
	}

}

