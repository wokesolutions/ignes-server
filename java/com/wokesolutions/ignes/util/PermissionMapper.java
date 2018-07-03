package com.wokesolutions.ignes.util;

import java.util.ArrayList;
import java.util.List;

public class PermissionMapper {

	private final static String LOGIN = "login";
	private final static String LOGOUT = "logout";
	private final static String VERIFYTOKEN = "verifytoken";
	private final static String POST_COMMENT = "comment/post";
	private final static String GET_COMMENT = "comment/get";
	private final static String GET_REPORT = "report/get";
	private final static String THUMBNAIL = "thumbnail";
	private final static String CREATE_REPORT = "report/create";
	private final static String GET_REPORT_VOTE = "report/vote/get";
	private final static String POST_REPORT_VOTE = "report/vote";
	private final static String PROFILE = "profile";
	private final static String CLOSE_REPORT = "report/close";
	private final static String ADMIN = "admin";
	private final static String ORG_INFO = "org/info";
	private final static String ORG = "org";
	private final static String WORKER = "worker";
	private final static String TASK = "task";
	private final static String REGISTER = "register";

	public static List<String> getPermissions(String url) { //TODO fix time
		String req = url.substring(url.indexOf("/api/") + 5);

		List<String> permissions = new ArrayList<String>(7);

		if(req.contains(REGISTER)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}
		
		if(req.contains(POST_COMMENT)) {
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}
		
		if(req.contains(VERIFYTOKEN)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			permissions.add(UserLevel.ORG);
			permissions.add(UserLevel.WORKER);
			return permissions;
		}

		if(req.contains(GET_COMMENT)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			permissions.add(UserLevel.ORG);
			permissions.add(UserLevel.WORKER);
			return permissions;
		}
		
		if(req.contains(THUMBNAIL)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}

		if(req.contains(GET_REPORT)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}

		if(req.contains(CREATE_REPORT)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		if(req.contains(GET_REPORT_VOTE)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}

		if(req.contains(POST_REPORT_VOTE)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		if(req.contains(LOGIN)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}

		if(req.contains(LOGOUT)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			permissions.add(UserLevel.ORG);
			permissions.add(UserLevel.WORKER);
			return permissions;
		}

		if(req.contains(PROFILE)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		if(req.contains(CLOSE_REPORT)) {
			permissions.add(UserLevel.LEVEL1);
			permissions.add(UserLevel.LEVEL2);
			permissions.add(UserLevel.LEVEL3);
			permissions.add(UserLevel.ADMIN);
			permissions.add(UserLevel.WORKER);
			return permissions;
		}

		if(req.contains(ADMIN)) {
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		if(req.contains(ORG_INFO)) {
			permissions.add(UserLevel.GUEST);
			return permissions;
		}

		if(req.contains(ORG)) {
			permissions.add(UserLevel.ADMIN);
			permissions.add(UserLevel.ORG);
			return permissions;
		}

		if(req.contains(WORKER)) {
			permissions.add(UserLevel.WORKER);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		if(req.contains(TASK)) {
			permissions.add(UserLevel.WORKER);
			permissions.add(UserLevel.ADMIN);
			return permissions;
		}

		return permissions;
	}
}
