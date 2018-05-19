package com.wokesolutions.ignes.util;

import com.mashape.unirest.http.exceptions.UnirestException;

import net.sargue.mailgun.Configuration;
import net.sargue.mailgun.Mail;

public class Email {

	private static final String KEY = "key-262ff22fead02704a1095c0200b53716";
	private static final String DOMAIN = "mg.wokesolutionsignes.com";
	private static final String EMAIL = "wokesolutions@gmail.com";
	
	private static final String SUBJECT = "Código de confirmação de conta";
	private static final String TEXT = "Utilize o seguinte código para confirmar a sua conta na Ignes, "
			+ "para que possa começar a usufruir da aplicação!";

	public static void sendSimpleMessage(String email, String code) {

		Configuration configuration = new Configuration()
				.domain(DOMAIN)
				.apiKey(KEY)
				.from("WokeSolutions", EMAIL);

		Mail.using(configuration)
		.to(email)
		.subject(SUBJECT)
		.text(TEXT + "\n\n" + code)
		.build()
		.send();
	}

	public static void main(String[] args) throws UnirestException {
		sendSimpleMessage("g.roxo@campus.fct.unl.pt", "FUNCIONA CRL");
		sendSimpleMessage("f.elisa@campus.fct.unl.pt", "FUNCIONA CRL");
		sendSimpleMessage("mbc.silva@campus.fct.unl.pt", "FUNCIONA CRL");
	}
}