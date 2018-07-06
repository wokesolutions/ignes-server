package com.wokesolutions.ignes.data;

public class LoginData {

	public String username;
	public String password;
	public String firebasetoken;
	
	public LoginData() {}
	
	public boolean isValid() {
		return username != null && password != null && firebasetoken != null
				&& !username.equals("") && !password.equals("") && !firebasetoken.equals("");
	}
}
