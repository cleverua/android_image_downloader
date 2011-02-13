/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cleverua.test.thumbs;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 *
 * <p>It requires the INTERNET permission, which should be added to your application's manifest
 * file.</p>
 *
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class ImageDownloader {
    private static final String LOG_TAG = "ImageDownloader";

    BitmapCache bitmapCache;
    
    /**
     * Current running task
     */
    private BitmapDownloaderTask loaderTask;
    private LinkedList<BitmapDownloaderTask> pendingTasks = new LinkedList<BitmapDownloaderTask>();
    
    public ImageDownloader(BitmapCache bitmapCache) {
    	this.bitmapCache = bitmapCache;
    }
    
    /**
     * Download the specified image from the Internet and binds it to the provided ImageView. The
     * binding is immediate if the image is found in the cache and will be done asynchronously
     * otherwise. A null bitmap will be associated to the ImageView if an error occurs.
     *
     * @param url The URL of the image to download.
     * @param imageView The ImageView to bind the downloaded image to.
     */
    public void download(String url, ImageView imageView, Drawable placeHolder) {
    	
        Bitmap bitmap = bitmapCache.getBitmapFromCache(url);

        if (bitmap == null) {
            forceDownload(url, imageView, placeHolder);
        } else {
            removePotentialDownload(url, imageView);
            imageView.setImageBitmap(bitmap);
        }
    }

    /**
     * Same as download but the image is always downloaded and the cache is not used.
     * Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(String url, ImageView imageView, Drawable placeHolder) {
        // State sanity: url is guaranteed to never be null in DownloadedDrawable and cache keys.
        if (url == null) {
            imageView.setImageDrawable(null);
            return;
        }

        if (removePotentialDownload(url, imageView)) {
            BitmapDownloaderTask task = new BitmapDownloaderTask(url, imageView);
            imageView.setImageDrawable(placeHolder);
            imageView.setTag(new WeakReference<BitmapDownloaderTask>(task));

            imageView.setMinimumHeight(156);

            pendingTasks.offerLast(task);
            if (loaderTask == null || loaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                loaderTask = pendingTasks.poll();
                loaderTask.execute();
            }
        }
    }

    private boolean removePotentialDownload(String url, ImageView imageView) {
        BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

        if (bitmapDownloaderTask != null) {
            String bitmapUrl = bitmapDownloaderTask.url;
            if ((bitmapUrl == null) || (!bitmapUrl.equals(url))) {
                pendingTasks.remove(bitmapDownloaderTask);
            } else {
                // The same URL is already being downloaded.
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active download task (if any) associated with this imageView.
     * null if there is no such task.
     */
    @SuppressWarnings("unchecked")
	private static BitmapDownloaderTask getBitmapDownloaderTask(ImageView imageView) {
        if (imageView != null) {
        	Object ref = imageView.getTag();
            if (ref != null && ref instanceof WeakReference ) {
            	WeakReference<BitmapDownloaderTask> taskReference = (WeakReference<BitmapDownloaderTask>)ref;
            	return taskReference.get();
            }
        }
        return null;
    }

    Bitmap downloadBitmap(String url) {
        // AndroidHttpClient is not allowed to be used from the main thread
        HttpClient client = HttpClientFactory.getThreadSafeClient();
        final HttpGet getRequest = new HttpGet(url);

        try {
            HttpResponse response = client.execute(getRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                Log.w("ImageDownloader", "Error " + statusCode +
                        " while retrieving bitmap from " + url);
                return null;
            }

            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = null;
                try {
                    inputStream = entity.getContent();
                    // return BitmapFactory.decodeStream(inputStream);
                    // Bug on slow connections, fixed in future release.
                    return BitmapFactory.decodeStream(new FlushedInputStream(inputStream));
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    entity.consumeContent();
                }
            }
        } catch (IOException e) {
            getRequest.abort();
            Log.w(LOG_TAG, "I/O error while retrieving bitmap from " + url, e);
        } catch (IllegalStateException e) {
            getRequest.abort();
            Log.w(LOG_TAG, "Incorrect URL: " + url);
        } catch (Exception e) {
            getRequest.abort();
            Log.w(LOG_TAG, "Error while retrieving bitmap from " + url, e);
        }
        return null;
    }

    /*
     * An InputStream that skips the exact number of bytes provided, unless it reaches EOF.
     */
    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break;  // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }

    /**
     * The actual AsyncTask that will asynchronously download the image.
     */
    class BitmapDownloaderTask extends AsyncTask<Void, Void, Bitmap> {
        private String url;
        private final WeakReference<ImageView> imageViewReference;

        public BitmapDownloaderTask(String url, ImageView imageView) {
            imageViewReference = new WeakReference<ImageView>(imageView);
            this.url = url;
        }

        /**
         * Actual download method.
         */
        @Override
        protected Bitmap doInBackground(Void... params) {
        	ImageView image = imageViewReference.get();
            if (image != null && this == getBitmapDownloaderTask(image)) {
                return downloadBitmap(url);
            } else {
                return null;
            }
        }

        /**
         * Once the image is downloaded, associates it to the imageView
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled() || bitmap == null) {
                bitmap = null;
            }

            bitmapCache.addBitmapToCache(url, bitmap);

            if (imageViewReference != null) {
                ImageView imageView = imageViewReference.get();
                BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
                // Change bitmap only if this process is still associated with it
                // Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
                if (this == bitmapDownloaderTask) {
                    imageView.setImageBitmap(bitmap);
                }
            }

            if (!pendingTasks.isEmpty()) {
                loaderTask = pendingTasks.poll();
                loaderTask.execute();
            }

        }
    }
}
