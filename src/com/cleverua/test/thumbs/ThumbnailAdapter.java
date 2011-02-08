package com.cleverua.test.thumbs;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

public class ThumbnailAdapter extends BaseAdapter {
	
	private Drawable placeHolder;
	
	private String[] urls;
	private ImageDownloader imageDownloader;

	public ThumbnailAdapter(String[] urls, Drawable placeHolder, BitmapCache bitmapCache) {
		this.urls = urls;
		this.placeHolder = placeHolder;
		this.imageDownloader = new ImageDownloader(bitmapCache);
	}
	
	@Override
	public int getCount() {
		return urls.length;
	}

	@Override
	public Object getItem(int position) {
		return urls[position];
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).hashCode();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = new ImageView(parent.getContext());
			convertView.setPadding(6, 6, 6, 6);
        }

        imageDownloader.download(urls[position], (ImageView) convertView, placeHolder);
        
        return convertView;
    }
}
