package com.wokesolutions.ignes.data;

public class UserOptionalData {
	
	public String useroptional_phone;
	public String useroptional_name;
	public String useroptional_gender;
	public String useroptional_address;
	public String useroptional_locality;
	public String useroptional_zip;
	public String useroptional_birth;
	public String useroptional_job;
	public String useroptional_skills;
	
	public UserOptionalData() {}

	public boolean isValid() {
		return useroptional_phone != null || useroptional_name != null || useroptional_gender != null || useroptional_address != null
				|| useroptional_locality != null || useroptional_zip != null || useroptional_birth != null || useroptional_job != null
				|| useroptional_skills != null;
	}
}
