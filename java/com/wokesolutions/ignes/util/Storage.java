package com.wokesolutions.ignes.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;

import org.apache.geronimo.mail.util.Base64;

import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;

public class Storage {
	
	public static final String BUCKET = "wokesolutions_ignes";
	public static final String IMG_FOLDER = "img";
	public static final String THUMBNAIL_FOLDER = "thumbnail";
	public static final String PROFILE_FOLDER = "profile";
	public static final String REPORT_FOLDER = "report";
	public static final String EVENT_FOLDER = "event";

	private final static int BUFFER_SIZE = 1024 * 1024;

	private final static GcsService gcsService = GcsServiceFactory.createGcsService(new RetryParams.Builder()
			.initialRetryDelayMillis(10)
			.retryMaxAttempts(10)
			.totalRetryPeriodMillis(15000)
			.build());
	
	private static ImagesService imagesService = ImagesServiceFactory.getImagesService();

	public static boolean saveImage(String img, String bucket, StoragePath path) {
		GcsFilename fileName = new GcsFilename(bucket, path.makePath());
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
		
		// Make an image directly from a byte array, and transform it.
		Image image = ImagesServiceFactory.makeImage(Base64.decode(img));
		
		// 256
		
		Transform resize = ImagesServiceFactory.makeResize(image.getWidth() / 5, image.getHeight() / 5);
		Image resizedImage = imagesService.applyTransform(resize, image);
		
		StoragePath tnPath = path.clone();
		tnPath.addTn();
		
		GcsFilename fileNameTn = new GcsFilename(bucket, tnPath.makePath());
		GcsFileOptions optionsTn = new GcsFileOptions.Builder()
                .mimeType("image/jpg")
                .acl("public-read")
                .build();
		GcsOutputChannel outputChannelTn;
		try {
			outputChannelTn = gcsService.createOrReplace(fileNameTn, optionsTn);
			copy(new ByteArrayInputStream(resizedImage.getImageData()),
					Channels.newOutputStream(outputChannelTn));
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
	
	public static StoragePath getTnFromPath(StoragePath path) {
		StoragePath pathTn = path.clone();
		pathTn.addTn();
		return pathTn;
	}
	
	public static class StoragePath {
		public List<String> folders;
		public String name;
		
		public StoragePath(List<String> folders, String name) {
			this.name = name;
			this.folders = folders;
		}
		
		public String makePath() {
			String path = "";
			for(String folder : folders)
				path += folder + "/";
			
			path += name;
			
			return path;
		}
		
		public StoragePath clone() {
			return new StoragePath(folders, name);
		}
		
		public void addTn() {
			folders.add(THUMBNAIL_FOLDER);
		}
	}
}
