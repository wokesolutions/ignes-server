package com.wokesolutions.ignes.data;

import java.util.Date;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.api.client.util.Base64;
import com.wokesolutions.ignes.api.Category;
import com.wokesolutions.ignes.api.Report;

public class ReportData {
	
	public String title;
	public double lat;
	public double lng;
	public String points;
	public int gravity;
	public String description;
	public String city;
	public String locality;
	public String address;
	public String category;
	public String img;
	public boolean isprivate;
	public int imgheight;
	public int imgwidth;
	public int imgorientation;
	
	public ReportData() {}
	
	public boolean isValid() {
		return ((lat != 0 && lng != 0) || points != null) && img != null 
				&& imgwidth != 0 && imgheight != 0 && Category.isEq(category);
	}
	
	public static String generateId(String username, Date creationtime) {
		return Base64.encodeBase64String((username + creationtime.getTime()).getBytes());
	}
	
	public static double[] calcMiddle(JSONArray points) {
		double latsum = 0;
		double lngsum = 0;
		
		Iterator<Object> it = points.iterator();
		
		while(it.hasNext()) {
			JSONObject point = (JSONObject) it.next();
			
			double pointlat = point.getDouble(Report.LAT);
			double pointlng = point.getDouble(Report.LNG);
			
			latsum += pointlat;
			lngsum += pointlng;
		}
		
		int num = points.length();
		
		double[] middle = new double[2];
		middle[0] = latsum / num;
		middle[1] = lngsum / num;
		
		return middle;
	}
	
	@Override
	public String toString() {
		return "lat -> " + lat + "\nlng -> " + lng + "\npoints -> " + points +
				"\ncategory -> " + category;
	}
}
