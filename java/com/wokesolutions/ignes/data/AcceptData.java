package com.wokesolutions.ignes.data;

public class AcceptData {

	public String report;
	public String nif;
	
	public AcceptData() {}
	
	public boolean isValid() {
		return nif != null && report != null &&
				!nif.equals("") && !report.equals("")
				&& nif.length() == 9;
	}
}
