package com.wokesolutions.ignes.data;

public class UserRegisterData {
	
	public String user_password;
	public String user_username;
	public String user_email;
	
	public UserRegisterData() {}
	
	public boolean isValid() {
		return user_username != null && user_email != null && user_password != null;
	}
}
