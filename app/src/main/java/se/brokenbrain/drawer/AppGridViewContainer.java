package se.brokenbrain.drawer;

import android.widget.ImageView;
import android.widget.TextView;

public class AppGridViewContainer {
	public AppItem item;
	public TextView tv;
	public ImageView iv;

	public AppGridViewContainer(AppItem item, TextView tv, ImageView iv) {
		this.item = item;
		this.tv = tv;
		this.iv = iv;
	}
}
