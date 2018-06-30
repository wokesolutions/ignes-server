package com.wokesolutions.ignes.data;

public class WorkerRegisterData {

	public String name;
	public String email;
	public String ob;
	
	public WorkerRegisterData() {}
	
	public boolean isValid() {
		return email != null && email.contains("@")
				&& name != null && ob != null;
	}
	
	public static String generateCode(String org, String email) {
		String[] orgName = org.split(" ");
		String initials = "";
		for(String name : orgName)
			initials += name.toLowerCase().charAt(0);

		String codeStr = initials +
				String.valueOf(System.currentTimeMillis()).substring(7 - initials.length());
		codeStr += email.charAt(0);
		
		return codeStr;
	}
}
