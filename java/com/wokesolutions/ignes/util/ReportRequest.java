package com.wokesolutions.ignes.util;

public class ReportRequest {
	
	public static final String L = "L";
	public static final String R = "R";
	public static final String B = "B";

	public double minlat;
	public double minlng;
	public double maxlat;
	public double maxlng;
	
	public double lat;
	public double lng;
	public double radius;
	
	public String location;
	
	public int gravity;
	public String user;
	
	public String type;
	
	public ReportRequest(String type) {
		this.type = type;
	}
}
