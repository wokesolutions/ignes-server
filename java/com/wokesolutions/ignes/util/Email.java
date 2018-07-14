package com.wokesolutions.ignes.util;

import com.google.appengine.api.datastore.Entity;

import net.sargue.mailgun.Configuration;
import net.sargue.mailgun.Mail;

public class Email {

	private static final String DOMAIN = "mg.wokesolutionsignes.com";
	private static final String EMAIL = "wokesolutionsignes@gmail.com";

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

	private static final String FORGOT_PW_SUBJECT = "Email de recuperação de palavra-passe";
	private static final String FORGOT_PW_TEXT = "Recebemos um pedido de recuperação da sua palavra-"
			+ "passe. Utilize a seguinte palavra-passe para entrar na sua conta, e ir ao seu perfil"
			+ " alterá-la para uma à sua escolha.\n\n"
			+ "Palavra-passe: ";

	private static final String CLOSED_REPORT_SUBJECT = "A sua ocorrência foi dada com fechada";
	private static final String CLOSED_REPORT_TEXT = "A sua occorência \"";
	private static final String CLOSED_REPORT_TEXT_2 = "\" foi dada como fechada pelo/a ";
	private static final String CLOSED_REPORT_TEXT_3 = ".\n\nAgradecemos por ter reportado esta ocorrência"
			+ " e continue a ajudar-nos a manter a comunidade segura!"
			+ "\n\nA equipa da Ignes,\nWokeSolutions";

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

	public static void sendForgotPwMessage(String email, String password) {

		Mail.using(configuration)
		.to(email)
		.subject(FORGOT_PW_SUBJECT)
		.text(FORGOT_PW_TEXT + password)
		.build()
		.send();
	}

	public static void sendClosedReport(String email, Entity closer,
			String orgName, String reportTitle) {
		String username = closer.getKey().getName();
		String levelS = closer.getProperty(DSUtils.USER_LEVEL).toString();
		String level;
		String closertext;
		if(levelS.equals(UserLevel.WORKER)) {
			level = "colaborador/a ";
			closertext = level + closer.getProperty(DSUtils.WORKER_NAME).toString()
					+ " (" + username + ") do nosso parceiro " + orgName;
		} else if(levelS.equals(UserLevel.ADMIN)) {
			level = "administrador/a ";
			closertext = level + username;
		}
		else
			return;

		Mail.using(configuration)
		.to(email)
		.subject(CLOSED_REPORT_SUBJECT)
		.text(CLOSED_REPORT_TEXT + reportTitle + CLOSED_REPORT_TEXT_2 + closertext
				+ CLOSED_REPORT_TEXT_3)
		.build()
		.send();
	}
}