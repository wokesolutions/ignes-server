package com.wokesolutions.ignes.data;

public class UserLoginData {

	public String username;
	public String password;
	
	public UserLoginData() {}
	
	public boolean isValid() {
		return username != null && password != null;
	}
}
