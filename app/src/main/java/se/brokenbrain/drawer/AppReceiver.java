package se.brokenbrain.drawer;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;

import android.util.Log;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;
import java.util.ArrayList;

public class AppReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i("[01;32mDrawer[00m", "Received broadcast: " + intent);
		String packageName = intent.getData().getSchemeSpecificPart();

		PackageManager pm = context.getPackageManager();

		Intent i = new Intent(Intent.ACTION_MAIN, null);
		i.addCategory(Intent.CATEGORY_LAUNCHER);

		List<ResolveInfo> list = pm.queryIntentActivities(i, PackageManager.PERMISSION_GRANTED);

		AppGridAdapter dbAdapter = new AppGridAdapter(context);
		if (intent.getAction().equals("android.intent.action.PACKAGE_REMOVED")) {
			dbAdapter.setUninstalled(packageName);

			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction("se.brokenbrain.drawer.PACKAGE_REMOVED");
			broadcastIntent.addCategory("se.brokenbrain.drawer.DEFAULT");
			context.sendBroadcast(broadcastIntent);
		} else {
			for (ResolveInfo rInfo : list) {
				if (rInfo.activityInfo.packageName.equals(packageName)) {
					AppItem item = new AppItem(rInfo, pm, context);
					if (item.getActivityName().startsWith("se.brokenbrain.drawer")) {
						continue;
					}
					dbAdapter.insertApp(item, true);

					Intent broadcastIntent = new Intent();
					broadcastIntent.setAction("se.brokenbrain.drawer.PACKAGE_ADDED_OR_CHANGED");
					broadcastIntent.addCategory("se.brokenbrain.drawer.DEFAULT");
					context.sendBroadcast(broadcastIntent);
				}
			}
		}

		dbAdapter.close();

	}
}
