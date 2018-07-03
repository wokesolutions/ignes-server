package com.wokesolutions.ignes.data;

import java.util.Date;

import org.json.JSONArray;

import com.google.api.client.util.Base64;

public class ReportData {
	
	public String title;
	public double lat;
	public double lng;
	public JSONArray points;
	public int gravity;
	public String description;
	public String city;
	public String locality;
	public String address;
	public String img;
	public boolean isprivate;
	public int imgheight;
	public int imgwidth;
	public int imgorientation;
	
	public ReportData() {}
	
	public boolean isValid() {
		return ((lat != 0 && lng != 0) || points != null) && img != null 
				&& imgwidth != 0 && imgheight != 0;
	}
	
	public static String generateId(String username, Date creationtime) {
		return Base64.encodeBase64String((username + creationtime.getTime()).getBytes());
	}
}
