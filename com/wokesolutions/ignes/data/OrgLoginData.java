package com.wokesolutions.ignes.data;

public class OrgLoginData {

	public String nif;
	public String password;
	
	public OrgLoginData() {}
	
	public boolean isValid() {
		return nif != null && password != null;
	}
}
