package com.wokesolutions.ignes.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;

public class Storage {
	
	public static final String BUCKET = "wokesolutions_ignes";
	public static final String IMG_FOLDER = "img/";
	public static final String THUMBNAIL_FOLDER = "thumbnail/";
	public static final String PROFILE_FOLDER = "profile/";
	public static final String REPORT_FOLDER = "report/";
	public static final String EVENT_FOLDER = "event/";

	private final static int BUFFER_SIZE = 1024 * 1024;

	private final static GcsService gcsService = GcsServiceFactory.createGcsService(new RetryParams.Builder()
			.initialRetryDelayMillis(10)
			.retryMaxAttempts(10)
			.totalRetryPeriodMillis(15000)
			.build());

	public static boolean saveImage(String img, String bucket, String name) {
		GcsFilename fileName = new GcsFilename(bucket, name);
		GcsFileOptions options = new GcsFileOptions.Builder()
                .mimeType("image/jpg")
                .acl("public-read")
                .build();
		GcsOutputChannel outputChannel;
		try {
			outputChannel = gcsService.createOrReplace(fileName, options);
			copy(new ByteArrayInputStream(img.getBytes()), Channels.newOutputStream(outputChannel));
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}

	private static void copy(InputStream input, OutputStream output) throws IOException {
		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead = input.read(buffer);
			while (bytesRead != -1) {
				output.write(buffer, 0, bytesRead);
				bytesRead = input.read(buffer);
			}
		} finally {
			input.close();
			output.close();
		}
	}
	
	public static String getImage(String path) {
		GcsFilename gcsFilename = new GcsFilename(BUCKET, path);
		GcsInputChannel readChannel = gcsService.openPrefetchingReadChannel(gcsFilename, 0, BUFFER_SIZE);
	    try {
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
			copy(Channels.newInputStream(readChannel), out);
			return out.toString();
		} catch (IOException e) {
			return null;
		}
	}
}
