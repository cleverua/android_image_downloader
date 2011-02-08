package com.cleverua.test.thumbs;

import android.app.ListActivity;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;

public class ImagesActivity extends ListActivity {

    private static final BitmapCache bitmapCache = new BitmapCache();

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    
        ListAdapter adapter = (ListAdapter)getLastNonConfigurationInstance();
        if (adapter == null) {
        	Resources r = getResources();
			adapter = new ThumbnailAdapter(r.getStringArray(R.array.urls), r.getDrawable(R.drawable.icon), bitmapCache);
        }
        setListAdapter(adapter);
        
        
        if (savedInstanceState == null) {
        	getListView().setSelectionFromTop(adapter.getCount() - 1, 0);
        }
    }
    
    @Override
    protected void onSaveInstanceState (Bundle savedState) {
    	super.onSaveInstanceState(savedState);
    	savedState.putInt("FIRST_VISIBLE_POSITION", getListView().getFirstVisiblePosition());
    	View v = getListView().getChildAt(0);
    	int top = (v == null) ? 0 : v.getTop();
    	savedState.putInt("FIRST_VISIBLE_TOP_OFFSET", top);
    }
    
    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    	
    	int position = savedInstanceState.getInt("FIRST_VISIBLE_POSITION");
    	int top = savedInstanceState.getInt("FIRST_VISIBLE_TOP_OFFSET");
    	getListView().setSelectionFromTop(position, top);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance () {
    	return getListAdapter();
    }
    
}