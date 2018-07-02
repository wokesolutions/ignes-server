package com.wokesolutions.ignes.util;

public class DSUtils {

	// Kinds
		public static final String ADMIN = "Admin";
		public static final String ADMINLOG = "AdminLog";
		public static final String USER = "User";
		public static final String USEROPTIONAL = "UserOptional";
		public static final String USEROPTIONALLOGS = "UserOptionalLogs";
		public static final String USERSTATS = "UserStats";
		public static final String USERLOG = "UserLog";
		public static final String USERPOINTS = "UserPoints";
		public static final String WORKER = "Worker";
		public static final String DELETEDWORKER = "DeletedWorker";
		public static final String ORG = "Org";
		public static final String UNCONFORG = "UnconfOrg";
		public static final String ORGCODE = "OrgCode";
		public static final String REPORT = "Report";
		public static final String CLOSEDREPORT = "ClosedReport";
		public static final String REPORTVOTES = "ReportVotes";
		public static final String USERVOTE = "UserVote";
		public static final String SPAMMEDREPORT = "SpammedReport";
		public static final String SKILL = "Skill";
		public static final String TOKEN = "Token";
		public static final String LEVELLOG = "LevelLog";
		public static final String TASK = "Task";
		public static final String PASSWORDCHANGELOG = "PasswordChangeLog";
		public static final String NOTE = "Note";
		public static final String REPORTSTATUSLOG = "ReportStatusLog";
		public static final String REPORTCOMMENT = "ReportComment";
		public static final String DEVICE = "Device";
		public static final String SKIPLOGIN = "SkipLogin";
	
	// Admin
		public static final String ADMIN_CREATIONTIME = "admin_creationtime";
		public static final String ADMIN_WASPROMOTED = "admin_waspromoted";
		public static final String ADMIN_WASDEMOTED = "admin_wasdemoted";
		public static final String ADMIN_OLDLEVEL = "admin_oldlevel";
	
	// AdminLog
		public static final String ADMINLOG_PROMOTED = "adminlog_promoted";
		public static final String ADMINLOG_DEMOTED = "adminlog_demoted";
		public static final String ADMINLOG_ADMIN = "adminlog_admin";
		public static final String ADMINLOG_TIME = "adminlog_time";
		public static final String ADMINLOG_DELETED = "adminlog_deleted";

	// User
		public static final String USER_PASSWORD = "user_password";
		public static final String USER_EMAIL = "user_email";
		public static final String USER_LEVEL = "user_level";
		public static final String USER_CREATIONTIME = "user_creationtime";
		public static final String USER_ACTIVATION = "user_activation";
		public static final String USER_CREATIONTIMEFORMATTED = "user_creationtimeformatted";

	// UserOptional
		public static final String USEROPTIONAL_NAME = "useroptional_name";
		public static final String USEROPTIONAL_BIRTH = "useroptional_birth";
		public static final String USEROPTIONAL_GENDER = "useroptional_gender";
		public static final String USEROPTIONAL_PHONE = "useroptional_phone";
		public static final String USEROPTIONAL_ADDRESS = "useroptional_address";
		public static final String USEROPTIONAL_LOCALITY = "useroptional_locality";
		public static final String USEROPTIONAL_ZIP = "useroptional_zip";
		public static final String USEROPTIONAL_SKILLS = "useroptional_skills";
		public static final String USEROPTIONAL_JOB = "useroptional_job";
		public static final String USEROPTIONAL_PICPATH = "useroptional_picpath";
		
	// UserOptionalLogs
		public static final String USEROPTIONALLOGS_USERNAME = "useroptionallogs_username";
		public static final String USEROPTIONALLOGS_CHANGETIME = "useroptionallogs_changetime";
		public static final String USEROPTIONALLOGS_NEWNAME = "useroptionallogs_newname";
		public static final String USEROPTIONALLOGS_NEWBIRTH = "useroptionallogs_newbirth";
		public static final String USEROPTIONALLOGS_NEWGENDER = "useroptionallogs_newgender";
		public static final String USEROPTIONALLOGS_NEWPHONE = "useroptionallogs_newphone";
		public static final String USEROPTIONALLOGS_NEWADDRESS = "useroptionallogs_newaddress";
		public static final String USEROPTIONALLOGS_NEWLOCALITY = "useroptionallogs_newlocality";
		public static final String USEROPTIONALLOGS_NEWZIP = "useroptionallogs_newzip";
		public static final String USEROPTIONALLOGS_NEWSKILLS = "useroptionallogs_newskills";
		public static final String USEROPTIONALLOGS_NEWJOB = "useroptionallogs_newjob";
		public static final String USEROPTIONALLOGS_OLDNAME = "useroptionallogs_oldname";
		public static final String USEROPTIONALLOGS_OLDBIRTH = "useroptionallogs_oldbirth";
		public static final String USEROPTIONALLOGS_OLDGENDER = "useroptionallogs_oldgender";
		public static final String USEROPTIONALLOGS_OLDPHONE = "useroptionallogs_oldphone";
		public static final String USEROPTIONALLOGS_OLDADDRESS = "useroptionallogs_oldaddress";
		public static final String USEROPTIONALLOGS_OLDLOCALITY = "useroptionallogs_oldlocality";
		public static final String USEROPTIONALLOGS_OLDZIP = "useroptionallogs_oldzip";
		public static final String USEROPTIONALLOGS_OLDSKILLS = "useroptionallogs_oldskills";
		public static final String USEROPTIONALLOGS_OLDJOB = "useroptionallogs_oldjob";

	// UserStats
		public static final String USERSTATS_LOGINS = "userstats_logins";
		public static final String USERSTATS_LOGINSFAILED = "userstats_loginsfailed";
		public static final String USERSTATS_LASTIN = "userstats_lastin";
		public static final String USERSTATS_LOGOUTS = "userstats_logouts";

	// UserLogs
		public static final String USERLOG_TYPE = "userlog_type";
		public static final String USERLOG_IP = "userlog_ip";
		public static final String USERLOG_HOST = "userlog_host";
		public static final String USERLOG_LATLON = "userlog_latlon";
		public static final String USERLOG_CITY = "userlog_city";
		public static final String USERLOG_COUNTRY = "userlog_country";
		public static final String USERLOG_TIME = "userlog_time";
		public static final String USERLOG_DEVICE = "userlog_device";

	// Worker
		public static final String WORKER_ORG = "worker_org";
		public static final String WORKER_ORGNAME = "worker_orgname";
		public static final String WORKER_JOB = "worker_job";
		public static final String WORKER_CREATIONTIME = "worker_creationtime";
		public static final String WORKER_NAME = "worker_name";
		
	// DeletedWorker
		public static final String DELETEDWORKER_ORG = "deletedworker_org";
		public static final String DELETEDWORKER_JOB = "deletedworker_job";
		public static final String DELETEDWORKER_CREATIONTIME = "deletedworker_creationtime";
		public static final String DELETEDWORKER_PASSWORD = "deletedworker_password";
		public static final String DELETEDWORKER_DELETIONTIME = "deletedworker_deletiontime";

	// Org
		public static final String ORG_NAME = "org_name";
		public static final String ORG_ADDRESS = "org_address";
		public static final String ORG_PHONE = "org_phone";
		public static final String ORG_ZIP = "org_zip";
		public static final String ORG_LOCALITY = "org_locality";
		public static final String ORG_SERVICES = "org_services";
		public static final String ORG_ISFIRESTATION = "org_isfirestation";
		public static final String ORG_EMAIL = "org_email";
		
	// UnconfOrg
		public static final String UNCONFORG_NAME = "unconforg_name";
		public static final String UNCONORG_ADDRESS = "unconforg_address";
		public static final String UNCONORG_PASSWORD = "unconforg_password";
		public static final String UNCONORG_PHONE = "unconforg_phone";
		public static final String UNCONORG_EMAIL = "unconforg_email";
		public static final String UNCONORG_ZIP = "unconforg_zip";
		public static final String UNCONORG_LOCALITY = "unconforg_locality";
		public static final String UNCONORG_SERVICES = "unconforg_services";
		public static final String UNCONORG_ISFIRESTATION = "unconforg_isfirestation";
		public static final String UNCONORG_CREATIONTIME = "unconforg_creationtime";
		public static final String UNCONORG_CONFIRMED = "unconforg_confirmed";

	// UserStats
		public static final String ORGSTATS_LOGINS = "orgstats_logins";
		public static final String ORGSTATS_LOGINSFAILED = "orgstats_loginsfailed";
		public static final String ORGSTATS_LASTIN = "orgstats_lastin";
		public static final String ORGSTATS_LOGOUTS = "orgstats_logouts";

	// UserLogs
		public static final String ORGLOG_TYPE = "orglog_type";
		public static final String ORGLOG_IP = "orglog_ip";
		public static final String ORGLOG_HOST = "orglog_host";
		public static final String ORGLOG_LATLON = "orglog_latlon";
		public static final String ORGLOG_CITY = "orglog_city";
		public static final String ORGLOG_COUNTRY = "orglog_country";
		public static final String ORGLOG_TIME = "orglog_time";

	// OrgCode
		public static final String ORGCODE_CODE = "orgcode_code";
		public static final String ORGCODE_ACTIVE = "orgcode_active";
		public static final String ORGCODE_WORKER = "orgcode_worker";

	// UserPoints
		public static final String USERPOINTS_POINTS = "userpoints_points";
	
	// Report
		public static final String REPORT_TITLE = "report_title";
		public static final String REPORT_LAT = "report_lat";
		public static final String REPORT_LNG = "report_lng";
		public static final String REPORT_ADDRESS = "report_address";
		public static final String REPORT_DISTRICT = "report_district";
		public static final String REPORT_LOCALITY = "report_locality";
		public static final String REPORT_USER = "report_user";
		public static final String REPORT_CREATIONTIME = "report_creationtime";
		public static final String REPORT_CREATIONTIMEFORMATTED = "report_creationtimeformatted";
		public static final String REPORT_GRAVITY = "report_gravity";
		public static final String REPORT_DESCRIPTION = "report_description";
		public static final String REPORT_CREATIONLATLNG = "report_creationlatlng";
		public static final String REPORT_IMGPATH = "report_imgpath";
		public static final String REPORT_IMG = "report_img";
		public static final String REPORT_THUMBNAILPATH = "report_thumbnailpath";
		public static final String REPORT_COMMENTSNUM = "report_commentsnum";
		public static final String REPORT_STATUS = "report_status";
		public static final String REPORT_PRIVATE = "report_private";
		public static final String REPORT_CLOSETIME = "report_closetime";
		
		public static final String REPORT_P0LAT = "report_p0lat";
		public static final String REPORT_P1LAT = "report_p1lat";
		public static final String REPORT_P2LAT = "report_p2lat";
		public static final String REPORT_P3LAT = "report_p3lat";
		public static final String REPORT_P4LAT = "report_p4lat";
		public static final String REPORT_P5LAT = "report_p5lat";
		public static final String REPORT_P6LAT = "report_p6lat";
		public static final String REPORT_P7LAT = "report_p7lat";
		public static final String REPORT_P8LAT = "report_p8lat";
		
		public static final String REPORT_P0LNG = "report_p0lng";
		public static final String REPORT_P1LNG = "report_p1lng";
		public static final String REPORT_P2LNG = "report_p2lng";
		public static final String REPORT_P3LNG = "report_p3lng";
		public static final String REPORT_P4LNG = "report_p4lng";
		public static final String REPORT_P5LNG = "report_p5lng";
		public static final String REPORT_P6LNG = "report_p6lng";
		public static final String REPORT_P7LNG = "report_p7lng";
		public static final String REPORT_P8LNG = "report_p8lng";
		
	// ReportVotes
		public static final String REPORTVOTES_UP = "reportvotes_up";
		public static final String REPORTVOTES_DOWN = "reportvotes_down";
		public static final String REPORTVOTES_SPAM = "reportvotes_spam";
		public static final String REPORTVOTES_RELEVANCE = "reportvotes_relevance";
		
	// UserVotes
		public static final String USERVOTE_USER = "uservote_user";
		public static final String USERVOTE_REPORT = "uservote_report";
		public static final String USERVOTE_EVENT = "uservote_event";
		public static final String USERVOTE_COMMENT = "uservote_comment";
		public static final String USERVOTE_TYPE = "uservote_type";
		public static final String USERVOTE_TIME = "uservote_time";
		
	// ReportComment
		public static final String REPORTCOMMENT_TEXT = "reportcomment_text";
		public static final String REPORTCOMMENT_TIME = "reportcomment_time";
		public static final String REPORTCOMMENT_TIMEFORMATTED = "reportcomment_timeformatted";
		public static final String REPORTCOMMENT_USER = "reportcomment_user";
		public static final String REPORTCOMMENT_REPORT = "reportcomment_report";
		
	// Skill
		public static final String SKILL_NUMBEROFPEOPLE = "skill_numberofpeople";
		
	// Token
		public static final String TOKEN_STRING = "token_string";
		public static final String TOKEN_DATE = "token_date";
		public static final String TOKEN_DEVICE = "token_device";
		public static final String TOKEN_USER = "token_user";
		
	// LevelLog
		public static final String LEVELLOG_OLDLEVEL = "levellog_oldlevel";
		public static final String LEVELLOG_NEWLEVEL = "levellog_newlevel";
		public static final String LEVELLOG_TIME = "levellog_time";
		public static final String LEVELLOG_USER = "levellog_user";
		
	// Task
		public static final String TASK_WORKER = "task_worker";
		public static final String TASK_TIME = "task_time";
		public static final String TASK_TIMEFORMATTED = "task_timeformatted";
		public static final String TASK_INDICATIONS = "task_indications";
		public static final String TASK_ORG = "task_org";
		
	// PasswordChangeLog
		public static final String PASSWORDCHANGELOG_OLD = "passwordchangelog_old";
		public static final String PASSWORDCHANGELOG_NEW = "passwordchangelog_new";
		public static final String PASSWORDCHANGELOG_TIME = "passwordchangelog_time";
		public static final String PASSWORDCHANGELOG_IP = "passwordchangelog_ip";
		
	// Note
		public static final String NOTE_WORKER = "note_worker";
		public static final String NOTE_TIME = "note_time";
		public static final String NOTE_TASK = "note_task";
		public static final String NOTE_TEXT = "note_text";
		public static final String NOTE_TIMEFORMATTED = "note_timeformatted";
		
	// ReportStatusLog
		public static final String REPORTSTATUSLOG_TIME = "reportstatuslog_time";
		public static final String REPORTSTATUSLOG_USER = "reportstatuslog_user";
		public static final String REPORTSTATUSLOG_OLDSTATUS = "reportstatuslog_oldstatus";
		public static final String REPORTSTATUSLOG_NEWSTATUS = "reportstatuslog_newstatus";
		
	// Device
		public static final String DEVICE_ID = "device_id";
		public static final String DEVICE_COUNT = "device_count";
		public static final String DEVICE_USER = "device_user";
		public static final String DEVICE_APP = "device_app";
		
	// SkipLogin
		public static final String SKIPLOGIN_TIME = "skiplogin_time";
		public static final String SKIPLOGIN_DEVICE = "skiplogin_device";
}
