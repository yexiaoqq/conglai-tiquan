package com.google.android.material.navigation;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;

/*
 * Compile-only facade for javac (NOT injected into the APK).
 * Binary signature matches the real class that already lives in the APK dex,
 * so emitted invoke-virtual instructions resolve against the real implementation.
 */
public abstract class NavigationBarView extends View {

    public NavigationBarView(Context context) { super(context); }

    public interface OnItemSelectedListener {
        boolean onNavigationItemSelected(MenuItem item);
    }

    public interface OnItemReselectedListener {
        void onNavigationItemReselected(MenuItem item);
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) { }

    public void setOnItemReselectedListener(OnItemReselectedListener listener) { }

    public void setSelectedItemId(int itemId) { }

    public int getSelectedItemId() { return 0; }
}
