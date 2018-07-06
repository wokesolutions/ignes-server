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
	public String categories;
	public String password;
	public String firebasetoken;

	public OrgRegisterData() {}

	public boolean isValid() {
		JSONArray array = new JSONArray(categories);
		
		return name != null && nif != null && nif.length() == 9 && email != null
				&& password != null && address != null && locality != null
				&& zip != null && categories != null && phone != null
				&& !name.equals("") && !email.equals("") && email.contains("@")
				&& !password.equals("") && !address.equals("") && !locality.equals("")
				&& !zip.equals("") && array.length() > 0 && !phone.equals("")
				&& array.length() <= 3 && firebasetoken != null
						&& !firebasetoken.equals("");
	}
}
