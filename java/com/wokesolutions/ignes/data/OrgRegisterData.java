package com.wokesolutions.ignes.data;

public class OrgRegisterData {
	
	public String name;
	public String nif;
	public String address;
	public String locality;
	public String zip;
	public String email;
	public String phone;
	public boolean isprivate;
	public String services;
	public String password;

	public OrgRegisterData() {}

	public boolean isValid() {
		return name != null && nif != null && nif.length() == 9 && email != null
				&& password != null && address != null && locality != null
				&& zip != null && services != null && phone != null
				&& !name.equals("") && !email.equals("") && email.contains("@")
				&& !password.equals("") && !address.equals("") && !locality.equals("")
				&& !zip.equals("") && !services.equals("") && !phone.equals("");
	}
}
