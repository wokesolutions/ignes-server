package com.wokesolutions.ignes.util;

import net.sargue.mailgun.Configuration;
import net.sargue.mailgun.Mail;

public class Email {

	private static final String DOMAIN = "mg.wokesolutionsignes.com";
	private static final String EMAIL = "wokesolutions@gmail.com";

	private static final String CONFIRM_SUBJECT = "Código de confirmação de conta Ignes";
	private static final String CONFIRM_TEXT =
			"Utilize o seguinte código para confirmar a sua conta na Ignes, "
					+ "para que possa começar a usufruir da aplicação e para que possa "
					+ "progredir no sistema de pontos e permissões!\n\n"
					+ "Depois da confirmação, complete o seu perfil no menu da aplicação.\n\n";

	private static final String WORKER_REGISTER_SUBJECT = "Ignes - Registo de trabalhador";
	private static final String WORKER_REGISTER_TEXT =
			"A sua empresa criou uma conta na Ignes para si. Aceda a aplicação móvel ou ao "
					+ "website https://wokesolutionsignes.com "
					+ "e utilize o este email e a palavra-passe abaixo para iniciar o seu trabalho "
					+ "e ajudar a sua comunidade.\n\nPoderá alterar a sua palavra-passe mais tarde, "
					+ "no menu da aplicação.\n\nA empresa que registou a conta foi: ";
	private static final String PASSWORD = "\n\nPassword: ";
	
	private static final String NEW_DEVICE_SUBJECT = "Novo dispositivo utilizado na sua conta";
	private static final String NEW_DEVICE_TEXT = "Um novo dispositivo foi detetado a utilizar as suas"
			+ " credenciais da Ignes. Se não foi você a iniciar sessão neste dispositivo, por favor"
			+ " entre em contacto connosco imediatamente para podermos resolver a situação."
			+ " Se foi você, pode ignorar este email, e esperemos que esteja a gostar da nossa aplicação!"
			+ " \n\nA informação do novo dispositivo é a seguinte:\n\n";
	
	private static final String ORG_CONFIRMED_SUBJECT = "A sua conta na Ignes foi confirmada";
	private static final String ORG_CONFIRMED_TEXT = "Obrigado por ter registado a sua organização da sua"
			+ " empresa na Ignes. A sua conta já foi confirmada, e já pode começar a usufruir das"
			+ " funcionabilidades da nossa aplicação!\n\n"
			+ "Bom trabalho!";
	
	private static final Configuration configuration = new Configuration()
			.domain(DOMAIN)
			.apiKey(Secrets.MAILGUN)
			.from("WokeSolutions", EMAIL);

	public static void sendConfirmMessage(String email, String code) {

		Mail.using(configuration)
		.to(email)
		.subject(CONFIRM_SUBJECT)
		.text(CONFIRM_TEXT + code)
		.build()
		.send();
	}

	public static void sendWorkerRegisterMessage(String email, String password, String org) {

		Mail.using(configuration)
		.to(email)
		.subject(WORKER_REGISTER_SUBJECT)
		.text(WORKER_REGISTER_TEXT + org + PASSWORD + password)
		.build()
		.send();
	}

	public static void sendNewDeviceMessage(String email, String deviceInfo) {

		Mail.using(configuration)
		.to(email)
		.subject(NEW_DEVICE_SUBJECT)
		.text(NEW_DEVICE_TEXT + deviceInfo)
		.build()
		.send();
	}
	
	public static void sendOrgConfirmedMessage(String email) {

		Mail.using(configuration)
		.to(email)
		.subject(ORG_CONFIRMED_SUBJECT)
		.text(ORG_CONFIRMED_TEXT)
		.build()
		.send();
	}
}