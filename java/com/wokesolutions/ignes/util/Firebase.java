package com.wokesolutions.ignes.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

public class Firebase {

	private static final String PATH = "/mimetic-encoder-209111-firebase-adminsdk-iiucd-6672c855f3.json";
	private static final String URL = "https://mimetic-encoder-209111.firebaseio.com";
	
	private static final String TITLE = "title";
	
	public static final String ORG_APPLIED_TITLE = "Nova candidatura de organização";

	public static void init() throws IOException {
		FileInputStream serviceAccount = new FileInputStream(PATH);

		FirebaseOptions options = new FirebaseOptions.Builder()
		    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
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
