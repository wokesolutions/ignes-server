package com.wokesolutions.ignes.data;

public class LoginData {

	public String username;
	public String password;
	
	public LoginData() {}
	
	public boolean isValid() {
		return username != null && password != null;
	}
}
