package com.wokesolutions.ignes.util;

public class Message {

	// Error User
		public static final String USER_ALREADY_EXISTS = "Este username já está a ser usado por outro utilizador";
		public static final String USER_ALREADY_ADMIN = "Este utilizador já é administrador";
		public static final String USER_NOT_FOUND = "Utilizador não existe";
		public static final String USER_NOT_ADMIN = "Utilizador não é administrador";
	
	// Error Org
		public static final String ORG_ALREADY_EXISTS = "Este NIF já está a ser usado por outra organização";
		public static final String ORG_CODE_NOT_FOUND = "Não foi encontrada nenhuma empresa com este código ativo";
		public static final String ORG_NOT_FOUND = "Organização não existe";
		public static final String ORG_CODE_ALREADY_EXISTS = "Erro a gerar código. Tente de novo";
	
	// Error Report
		public static final String DUPLICATE_REPORT = "Ocorrência já reportada";
		public static final String REPORT_NOT_FOUND = "Ocorrência não encontrada";
		public static final String REPORT_COMMENT_NOT_FOUND = "Erro nos comentários";
	
	// Error Admin
		public static final String MOTER_NOT_ADMIN = "Utilizador a fazer alterações não é administrador";
	
	// Error General
		public static final String WRONG_PASSWORD = "Password errada para o utilizador: ";
		public static final String TOO_MANY_RETRIES = "Demasiadas tentativas. Abortando...";
		public static final String REGISTER_DATA_INVALID = "Não há campos suficientes para registo";
		public static final String LOGIN_DATA_INVALID = "Não há campos suficientes para login";
		public static final String BAD_FORMAT = "Má formatação";
		public static final String INVALID_TOKEN = "Token inválido";
		public static final String TXN_ACTIVE = "Transição ativa";
		public static final String FAILED_LOGIN = "Tentativa de entrar falhou para: ";
		public static final String PROFILE_UPDATE_DATA_INVALID = "Não há campos suficientes para atualizar perfil";
		public static final String ALTERER_IS_NOT_USER_OR_ADMIN = "Alterador não é o utilizador ou administrador";
		public static final String NO_OPTIONAL_USER_ENTITY_FOUND = "Não existe entidade de informação opcional deste utilizador";
		public static final String GOOGLE_MAPS_ERROR = "Erro do Google Maps";
	
	
	// Info User
		public static final String ATTEMPT_REGISTER_USER = "Tentando registar o utilizador: ";
		public static final String USER_REGISTERED = "Utilizador registado: ";
	
	// Info Admin
		public static final String ADMIN_PROMOTED = "Administrador promovido";
		public static final String ADMIN_DEMOTED = "Administrador demovido";
		public static final String ATTEMPT_REGISTER_ADMIN = "Tentando registar o administrador: ";
		public static final String ATTEMPT_PROMOTE_TO_ADMIN = "Tentando promover para administrador o utilizador: ";
		public static final String ATTEMPT_DEMOTE_FROM_ADMIN = "Tentando demover de administrador o utilizador: ";
		public static final String ADMIN_REGISTERED = "Administrador registado: ";
		public static final String NOT_ADMIN_TRY_FILTER = "Utilizador não é administrador, a tentar próxima permissão";
	
	// Info Org
		public static final String GENERATING_CODE = "A gerar código para a empresa: ";
		public static final String CODE_INITIALS = "A gerar código com as iniciais: ";
		public static final String ORG_REGISTERED = "Organização registada: ";
		public static final String ATTEMPT_REGISTER_ORG = "Tentando registar a organização: ";
	
	// Info Worker
		public static final String WORKER_REGISTERED = "Trabalhador registado: ";
		public static final String ATTEMPT_REGISTER_WORKER = "Tentando registar o trabalhador: ";
	
	// Info Report
		public static final String REPORT_CREATED = "Ocorrência reportada: ";
		public static final String ATTEMPT_CREATE_REPORT = "Tentando criar a ocorrência: ";
		public static final String ATTEMPT_GIVE_ALL_REPORTS = "Tentando dar todas as ocorrências";
		public static final String VOTED_REPORT = "Ocorrência votada com sucesso";
	
	// Info General
		public static final String UPLOADED_LOG_STATS = "Upload das estatísticas e registos do utilizador para a BD concluído";
		public static final String ATTEMPT_LOGIN = "Tentando entrar como: ";
		public static final String LOGGED_IN = " entrou com sucesso";
		public static final String FILTER_VERIFYING = " está a filtrar o pedido: ";
		public static final String LOGGING_OUT = "A sair da conta: ";
		public static final String ATTEMPT_UPDATE_PROFILE = "Tentando atualizar perfil: ";
		public static final String PROFILE_UPDATED = "Perfil do utilizador foi atualizado";
	
	// Status
		public static final String OK = "OK";
		
	// Error Storage
		public static final String STORAGE_ERROR = "Erro no Google Storage";
		
	// Info Storage
		public static final String STORAGE_SAVED_IMG_REPORT = "Gravou a imagem de, com nome: ";
		public static final String USING_CACHE = "A usar a cache";
		
	// Email
		public static final String EMAIL_ERROR = "Erro a enviar a email";
}
