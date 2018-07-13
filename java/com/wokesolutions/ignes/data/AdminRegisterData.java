package com.wokesolutions.ignes.data;

public class AdminRegisterData {

	public String username;
	public String email;
	public String locality;
	public String password;

	public AdminRegisterData() {}
	
	public boolean isValid() {
		return username != null && !username.equals("")
				&& email != null && !email.equals("") && email.contains("@") && email.contains(".")
				&& password != null && !password.equals("")
				&& locality != null && !locality.equals("");
	}
}
