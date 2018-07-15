package com.wokesolutions.ignes.data;

public class UserOptionalData {
	
	public String phone;
	public String name;
	public String gender;
	public String address;
	public String locality;
	public String zip;
	public String birth;
	public String job;
	public String skills;
	
	public UserOptionalData() {}

	public boolean isValid() {
		return phone != null || name != null || gender != null || address != null
				|| locality != null || (zip != null && zip.matches("\\d{4}(-\\d{3})?$")) || birth != null || job != null
				|| skills != null;
	}
}
