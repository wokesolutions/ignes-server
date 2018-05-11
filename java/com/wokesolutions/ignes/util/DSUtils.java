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
		public static final String ORG = "Org";
		public static final String ORGSTATS = "OrgStats";
		public static final String ORGLOG = "OrgLog";
		public static final String ORGCODE = "OrgCode";
		public static final String REPORT = "Report";
		public static final String REPORT_VOTES = "ReportVotes";
		public static final String REPORT_COMMENTS = "ReportComments";
	
	// Admin
		public static final String ADMIN_CREATIONTIME = "admin_creationtime";
		public static final String ADMIN_WASPROMOTED = "admin_waspromoted";
		public static final String ADMIN_WASDEMOTED = "admin_wasdemoted";
		public static final String ADMIN_OLDLEVEL = "admin_oldlevel";
	
	// AdminLog
		public static final String ADMINLOG_PROMOTED = "adminlog_promoted";
		public static final String ADMINLOG_DEMOTED = "adminlog_demoted";
		public static final String ADMINLOG_TIME = "adminlog_time";

	// User
		public static final String USER_PASSWORD = "user_password";
		public static final String USER_EMAIL = "user_email";
		public static final String USER_LEVEL = "user_level";
		public static final String USER_CREATIONTIME = "user_creationtime";

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
		public static final String USEROPTIONAL_JOB_STANDARD = "useroptional_job_standard";
		
	// UserOptionalLogs
		public static final String USEROPTIONALLOGS_USERNAME = "useroptionallog_username";
		public static final String USEROPTIONALLOGS_CHANGETIME = "useroptionallog_changetime";
		public static final String USEROPTIONALLOGS_NEWNAME = "useroptionallog_newname";
		public static final String USEROPTIONALLOGS_NEWBIRTH = "useroptionallog_newbirth";
		public static final String USEROPTIONALLOGS_NEWGENDER = "useroptionallog_newgender";
		public static final String USEROPTIONALLOGS_NEWPHONE = "useroptionallog_newphone";
		public static final String USEROPTIONALLOGS_NEWADDRESS = "useroptionallog_newaddress";
		public static final String USEROPTIONALLOGS_NEWLOCALITY = "useroptionallog_newlocality";
		public static final String USEROPTIONALLOGS_NEWZIP = "useroptionallog_newzip";
		public static final String USEROPTIONALLOGS_NEWSKILLS = "useroptionallog_newskills";
		public static final String USEROPTIONALLOGS_NEWJOB = "useroptionallog_newjob";
		public static final String USEROPTIONALLOGS_OLDNAME = "useroptionallog_oldname";
		public static final String USEROPTIONALLOGS_OLDBIRTH = "useroptionallog_oldbirth";
		public static final String USEROPTIONALLOGS_OLDGENDER = "useroptionallog_oldgender";
		public static final String USEROPTIONALLOGS_OLDPHONE = "useroptionallog_oldphone";
		public static final String USEROPTIONALLOGS_OLDADDRESS = "useroptionallog_oldaddress";
		public static final String USEROPTIONALLOGS_OLDLOCALITY = "useroptionallog_oldlocality";
		public static final String USEROPTIONALLOGS_OLDZIP = "useroptionallog_oldzip";
		public static final String USEROPTIONALLOGS_OLDSKILLS = "useroptionallog_oldskills";
		public static final String USEROPTIONALLOGS_OLDJOB = "useroptionallog_oldjob";

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

	// Worker
		public static final String WORKER_ORG = "worker_org";

	// Org
		public static final String ORG_NAME = "org_name";
		public static final String ORG_ADDRESS = "org_address";
		public static final String ORG_PASSWORD = "org_password";
		public static final String ORG_PHONE = "org_phone";
		public static final String ORG_EMAIL = "org_email";
		public static final String ORG_ZIP = "org_zip";
		public static final String ORG_LOCALITY = "org_locality";
		public static final String ORG_SERVICES = "org_services";
		public static final String ORG_ISFIRESTATION = "org_isfirestation";
		public static final String ORG_CREATIONTIME = "org_creationtime";

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

	// UserPoints
		public static final String USERPOINTS_POINTS = "userpoints_points";
	
	// Report
		public static final String REPORT_TITLE = "report_title";
		public static final String REPORT_LAT = "report_lat";
		public static final String REPORT_LNG = "report_lng";
		public static final String REPORT_ADDRESS = "report_address";
		public static final String REPORT_DISTRICT = "report_district";
		public static final String REPORT_LOCALITY = "report_locality";
		public static final String REPORT_USERNAME = "report_username";
		public static final String REPORT_CREATIONTIME = "report_creationtime";
		public static final String REPORT_CREATIONTIMEFORMATTED = "report_creationtimeformatted";
		public static final String REPORT_GRAVITY = "report_gravity";
		public static final String REPORT_STATUS = "report_status";
		public static final String REPORT_DESCRIPTION = "report_description";
		public static final String REPORT_CREATIONLATLNG = "report_creationlatlng";
		public static final String REPORT_IMGPATH = "report_imgpath";
		public static final String REPORT_IMG = "report_img";
		public static final String REPORT_THUMBNAILPATH = "report_thumbnailpath";
		public static final String REPORT_THUMBNAIL = "report_thumbnail";
		
	// ReportVotes
		public static final String REPORT_VOTES_UP = "reportvotes_up";
		public static final String REPORT_VOTES_DOWN = "reportvotes_down";
		
	// ReportComments
		public static final String REPORTCOMMENTS_NUM = "reportcomments_num";
		public static final String REPORTCOMMENTS_TEXT = "reportcomments_text";
		public static final String REPORTCOMMENTS_TIME = "reportcomments_time";
}
