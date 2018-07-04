package com.wokesolutions.ignes.data;

import org.json.JSONArray;

public class OrgRegisterData {
	
	public String name;
	public String nif;
	public String address;
	public String locality;
	public String zip;
	public String email;
	public String phone;
	public boolean isprivate;
	public JSONArray categories;
	public String password;

	public OrgRegisterData() {}

	public boolean isValid() {
		return name != null && nif != null && nif.length() == 9 && email != null
				&& password != null && address != null && locality != null
				&& zip != null && categories != null && phone != null
				&& !name.equals("") && !email.equals("") && email.contains("@")
				&& !password.equals("") && !address.equals("") && !locality.equals("")
				&& !zip.equals("") && categories.length() > 0 && !phone.equals("")
				&& categories.length() <= 3;
	}
}
