package com.wokesolutions.ignes.data;

public class UserRegisterData {
	
	public String password;
	public String username;
	public String email;
	
	public UserRegisterData() {}
	
	public boolean isValid() {
		return username != null && email != null && password != null
				&& !username.equals("") && !password.equals("") && !email.equals("")
				&& email.contains("@") && isUsernameValid(username);
	}
	
	public static boolean isUsernameValid(String username) {
		return username.matches("^[a-zA-Z0-9_.]+$");
	}
}
