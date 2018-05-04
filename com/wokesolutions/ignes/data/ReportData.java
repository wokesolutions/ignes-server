package com.wokesolutions.ignes.data;

import com.google.api.client.util.Base64;

public class ReportData {
	
	public String report_title;
	public double report_lat;
	public double report_lng;
	public int report_gravity;
	public String report_description;
	public String report_city;
	public String report_locality;
	public String report_address;
	public String report_img;
	
	public ReportData() {}
	
	public boolean isValid() {
		return report_lat != 0 && report_lng != 0 && report_img != null;
	}
	
	public static String generateId(String username, long creationtime) {
		return Base64.encodeBase64String((username + creationtime).getBytes());
	}
}
