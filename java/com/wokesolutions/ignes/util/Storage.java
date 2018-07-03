package com.wokesolutions.ignes.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.LinkedList;
import java.util.logging.Logger;

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
	
	private static final Logger LOG = Logger.getLogger(Storage.class.getName());

	public static final String BUCKET = "wokesolutions_ignes";
	public static final String IMG_FOLDER = "img";
	public static final String PROFILE_FOLDER = "profile";
	public static final String REPORT_FOLDER = "report";
	public static final String EVENT_FOLDER = "event";
	
	private static final int IMAGE_WIDTH = 256;

	private final static int BUFFER_SIZE = 1024 * 1024;

	private final static GcsService gcsService = GcsServiceFactory
			.createGcsService(new RetryParams.Builder()
			.initialRetryDelayMillis(10)
			.retryMaxAttempts(10)
			.totalRetryPeriodMillis(15000)
			.build());
	
	private static ImagesService imagesService = ImagesServiceFactory.getImagesService();
	
	public static boolean deleteImage(StoragePath path) {
		GcsFilename fileName = new GcsFilename(BUCKET, path.makePath());
		
		try {
			gcsService.delete(fileName);
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}

	public static boolean saveImage(String img, StoragePath path,
			int width, int height, int orientation, boolean withTn) {
		byte[] bytes = Base64.decode(img);
		Image image = ImagesServiceFactory.makeImage(bytes);
		LOG.info(Integer.toString(orientation));
		Transform rotate = ImagesServiceFactory.makeRotate(orientation);
		Image rotatedImage = imagesService.applyTransform(rotate, image);
		
		GcsFilename fileName = new GcsFilename(BUCKET, path.makePath());
		GcsFileOptions options = new GcsFileOptions.Builder()
                .mimeType("image/jpg")
                .acl("public-read")
                .build();
		GcsOutputChannel outputChannel;
		try {
			outputChannel = gcsService.createOrReplace(fileName, options);
			copy(new ByteArrayInputStream(Base64.encode(rotatedImage.getImageData())),
					Channels.newOutputStream(outputChannel));
		} catch(IOException e) {
			return false;
		}
		
		if(!withTn)
			return true;
		
		int newHeight = height * IMAGE_WIDTH / width;
		
		Transform resize = ImagesServiceFactory.makeResize(IMAGE_WIDTH, newHeight);
		Image resizedImage = imagesService.applyTransform(resize, rotatedImage);
		
		GcsFilename fileNameTn = new GcsFilename(BUCKET, path.makeTnPath());
		GcsFileOptions optionsTn = new GcsFileOptions.Builder()
                .mimeType("image/jpg")
                .acl("public-read")
                .build();
		GcsOutputChannel outputChannelTn;
		try {
			outputChannelTn = gcsService.createOrReplace(fileNameTn, optionsTn);
			copy(new ByteArrayInputStream(Base64.encode(resizedImage.getImageData())),
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
	
	public static class StoragePath {
		private static final String THUMBNAIL_FOLDER = "thumbnail/";
		
		public LinkedList<String> folders;
		public String name;
		
		public StoragePath(LinkedList<String> folders, String name) {
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
		
		public String makeTnPath() {
			String path = "";
			for(String folder : folders)
				path += folder + "/";
			
			path += THUMBNAIL_FOLDER;
			
			path += name;
			
			return path;
		}
		
		public StoragePath clone() {
			return new StoragePath(folders, name);
		}
	}
}
