package com.wokesolutions.ignes.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

public class Firebase {

	private static final String PATH = "./credentials.json";
	private static final String URL = "https://mimetic-encoder-209111.firebaseio.com";
	
	private static final String TITLE = "title";
	
	public static final String ORG_APPLIED_TITLE = "Nova candidatura de organização";

	public static void init() throws IOException {
		InputStream resourceStream = Thread.currentThread().getContextClassLoader()
			    .getResourceAsStream(PATH);

		FirebaseOptions options = new FirebaseOptions.Builder()
		    .setCredentials(GoogleCredentials.fromStream(resourceStream))
		    .setDatabaseUrl(URL)
		    .build();

		FirebaseApp.initializeApp(options);
	}
	
	public static void sendMessageToDevice(String title, Map<String, String> body, String token)
			throws FirebaseMessagingException {
		Message message = Message.builder()
				.putData(TITLE, title)
				.putAllData(body)
				.setToken(token)
				.build();

		try {
			FirebaseMessaging.getInstance().send(message);
		} catch(FirebaseMessagingException e) {
			throw e;
		}
	}
	
	public static void sendMessageToTopic(String title, Map<String, String> body, String topic)
			throws FirebaseMessagingException {
		Message message = Message.builder()
				.putData(TITLE, title)
				.putAllData(body)
				.setTopic(topic)
				.build();

		try {
			FirebaseMessaging.getInstance().send(message);
		} catch(FirebaseMessagingException e) {
			throw e;
		}
	}
}
