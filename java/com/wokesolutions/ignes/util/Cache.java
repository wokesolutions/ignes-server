package com.wokesolutions.ignes.util;

import javax.servlet.http.HttpServletRequest;

public class Cache {

	public static String GETId(String url, HttpServletRequest request) {
		return request.getRemoteAddr() + url;
	}
	
	public static String POSTId(String url, HttpServletRequest request) {
		return request.getRemoteAddr() + url;
	}
}
