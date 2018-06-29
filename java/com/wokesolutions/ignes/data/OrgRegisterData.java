package com.wokesolutions.ignes.data;

public class OrgRegisterData {
	
	public String org_name;
	public String org_nif;
	public String org_address;
	public String org_locality;
	public String org_zip;
	public String org_email;
	public String org_phone;
	public boolean org_isfirestation;
	public String org_services;
	public String org_password;

	public OrgRegisterData() {}

	public boolean isValid() {
		return org_name != null && org_nif != null && org_nif.length() == 9 && org_email != null
				&& org_password != null && org_address != null && org_locality != null
				&& org_zip != null && org_services != null && org_phone != null
				&& !org_name.equals("") && !org_email.equals("") && org_email.contains("@")
				&& !org_password.equals("") && !org_address.equals("") && !org_locality.equals("")
				&& !org_zip.equals("") && !org_services.equals("") && !org_phone.equals("");
	}
}
