package com.wokesolutions.ignes.util;

public class Log {

	// Error User
		public static final String USER_ALREADY_EXISTS = "Este username já está a ser usado por outro utilizador";
		public static final String USER_ALREADY_ADMIN = "Este utilizador já é administrador";
		public static final String USER_NOT_FOUND = "Utilizador não existe";
		public static final String USER_NOT_ADMIN = "Utilizador não é administrador";
		public static final String NO_TOKENS_FOUND = "Nenhum token encontrado para o utilizador";
		public static final String NOT_REPORTER = "Utilizador não é o autor do report";
		public static final String USER_NOT_ACTIVE = "Utilizador não ativou ainda a conta";
	
	// Error Org
		public static final String ORG_ALREADY_EXISTS = "Este NIF já está a ser usado por outra organização";
		public static final String ORG_CODE_NOT_FOUND = "Não foi encontrada nenhuma empresa com este código ativo";
		public static final String ORG_NOT_FOUND = "Organização não existe";
		public static final String ORG_CODE_ALREADY_EXISTS = "Erro a gerar código. Tente de novo";
		public static final String ORG_NOT_CONFIRMED = "Organização ainda não confirmada";
		public static final String REPORT_STANDBY = "Ocorrência não está confirmada";
	
	// Error Report
		public static final String DUPLICATE_REPORT = "Ocorrência já reportada";
		public static final String REPORT_NOT_FOUND = "Ocorrência não encontrada";
		public static final String REPORT_COMMENT_NOT_FOUND = "Erro nos comentários";
		public static final String NO_REPORTS_IN_HEADER = "Header não contem ocorrências";
		public static final String TOO_MANY_REPORTS = "Header contém demasiados reports";
		public static final String NO_REPORTS_FOUND = "Não foram encontradas ocorrências";
		public static final String VOTED_REPORT_ERROR = "Ocorrência votada com erro: ";
		public static final String REPORT_IS_PRIVATE = "Ocorrência é privada";
	
	// Error Admin
		public static final String MOTER_NOT_ADMIN = "Utilizador a fazer alterações não é administrador";
		
	// Error Worker
		public static final String WORKER_NOT_FOUND = "Trabalhador não encontrado";
		public static final String WORKER_NOT_ALLOWED = "Trabalhador não está autorizado a alterar esta ocorrência";
		
	// Error Task
		public static final String NOTE_TOO_LONG = "Nota é demasiado longa";
		public static final String NOTE_EMPTY = "Nota não pode ser vazia";
		public static final String TASK_NOT_FOUND= "Tarefa não encontrada";
		public static final String DUPLICATED_TASK = "Trabalhador já tem essa tarefa";
		public static final String APPLICATION_NOT_FOUND = "Organização não se candidatou a essa tarefa";
	
	// Error General
		public static final String WRONG_PASSWORD = "Password errada para o utilizador: ";
		public static final String TOO_MANY_RETRIES = "Demasiadas tentativas. Abortando...";
		public static final String REGISTER_DATA_INVALID = "Não há campos suficientes para registo";
		public static final String LOGIN_DATA_INVALID = "Não há campos suficientes para login";
		public static final String BAD_FORMAT = "Má formatação";
		public static final String INVALID_TOKEN = "Token inválido";
		public static final String INVALID_TOKEN_ITSELF = "Token inválido em algoritmo";
		public static final String TXN_ACTIVE = "Transição ativa";
		public static final String FAILED_LOGIN = "Tentativa de entrar falhou para: ";
		public static final String PROFILE_UPDATE_DATA_INVALID = "Não há campos suficientes para atualizar perfil";
		public static final String REQUESTER_IS_NOT_USER_OR_ADMIN = "Pedido não é do utilizador ou administrador";
		public static final String NO_OPTIONAL_USER_ENTITY_FOUND = "Não existe entidade de informação opcional deste utilizador";
		public static final String GOOGLE_MAPS_ERROR = "Erro do Google Maps";
		public static final String EMAIL_ALREADY_IN_USE = "Email já está em uso";
		public static final String UNEXPECTED_ERROR = "Erro inesperado";
		public static final String INVALID_EMAIL = "Email inválido";
		public static final String INVALID_USERNAME = "Nome de utilizador inválido";
		public static final String FORBIDDEN = "Não tem permissão";
		
	// Error Requests
		public static final String TOO_MANY_REQUESTS = "Demasiados pedidos do mesmo IP";
		public static final String NOT_HTTP_REQUEST = "Pedido não é HTTP";
		public static final String REQUEST_ID_ERROR = "Erro a obter ID do pedido";
		public static final String MISSING_DEVICE_HEADER = "Não foi encontrado um dos headers";
	
	// Info User
		public static final String ATTEMPT_REGISTER_USER = "Tentando registar o utilizador: ";
		public static final String USER_REGISTERED = "Utilizador registado: ";
		public static final String PASSWORD_CHANGED = "Palavra-passe alterada para o utilizador: ";
		public static final String GIVING_VOTES = "Tentando dar os votos do utilizador: ";
		public static final String USER_HAS_NO_IMAGE = "Utilizador não tem imagem de perfil";
	
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
		public static final String DELETED_WORKER = "Trabalhador foi apagado do sistema: ";
		public static final String LISTING_TASKS = "Listando tarefas do trabalhador";
	
	// Info Report
		public static final String REPORT_CREATED = "Ocorrência reportada: ";
		public static final String ATTEMPT_CREATE_REPORT = "Tentando criar a ocorrência: ";
		public static final String ATTEMPT_GIVE_ALL_REPORTS = "Tentando dar todas as ocorrências";
		public static final String VOTED_REPORT = "Ocorrência votada com sucesso";
		public static final String SEARCHING_IN_COORDS = "Procurando nas seguintes coords: ";
		public static final String REPORT_CLOSED = "Ocorrência fechada com sucesso";
		public static final String REPORT_WIPED = "Ocorrência posta em trabalhos com sucesso";
		public static final String TASK_ALREADY_ASSIGNED = "Ocorrência já está atribuída";
		public static final String REPORT_IS_LATLNG = "Ocorrência é um ponto";
		public static final String REPORT_IS_POINTS = "Ocorrência é um polígono";
		
	// Info Levels
		public static final String TRYING_TO_CHANGE_LEVEL1 = "Tentando mudar para nível 1 o utilizador: ";
		public static final String TRYING_TO_CHANGE_LEVEL2 = "Tentando mudar para nível 2 o utilizador: ";
		public static final String TRYING_TO_CHANGE_LEVEL3 = "Tentando mudar para nível 3 o utilizador: ";
	
	// Info General
		public static final String UPLOADED_LOG_STATS = "Upload das estatísticas e registos do utilizador para a BD concluído";
		public static final String ATTEMPT_LOGIN = "Tentando entrar como: ";
		public static final String LOGGED_IN = " entrou com sucesso";
		public static final String FILTER_VERIFYING = " está a filtrar o pedido: ";
		public static final String LOGGING_OUT = "A sair da conta: ";
		public static final String ATTEMPT_UPDATE_PROFILE = "Tentando atualizar perfil: ";
		public static final String PROFILE_UPDATED = "Perfil do utilizador foi atualizado";
		public static final String VERIFYING_TOKEN = "Verificando o token";
		public static final String VERIFYING_TOKEN_OF_USER = "Verificando o token do user: ";
		public static final String NOT_GUEST_REQUEST = "Pedido exige autenticação";
		public static final String GUEST_REQUEST = "Pedido não exige autenticação";
		public static final String PERMISSION_GRANTED = "Autenticação foi verificada";
		
	// Info Requests
		public static final String REQUEST_IS_GOOD = "Pedido foi aceite";
		public static final String LEVEL_MANAGER_CHECKING = "Gestor de níveis verificando o"
				+ " utilizador: ";
	
	// Status
		public static final String OK = "OK";
		
	// Error Storage
		public static final String STORAGE_ERROR = "Erro no Google Storage";
		public static final String TOO_MANY_RESULTS = "Demasiadas entidades";
		
	// Info Storage
		public static final String STORAGE_SAVED_IMG_REPORT = "Gravou a imagem de, com nome: ";
		public static final String USING_CACHE = "A usar a cache";
		
	// Email
		public static final String EMAIL_ERROR = "Erro a enviar a email";
}
