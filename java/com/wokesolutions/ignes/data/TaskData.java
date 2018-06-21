package com.wokesolutions.ignes.data;

public class TaskData {
	
	public String email;
	public String report;
	public String indications;
	
	public TaskData() {}
	
	public boolean isValid() {
		return !(email == null || email.equals("") || report == null || report.equals(""));
	}
}
