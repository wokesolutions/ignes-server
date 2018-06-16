package com.wokesolutions.ignes.data;

public class WorkerRegisterData {

	public String worker_name;
	public String worker_email;
	public String worker_job;
	
	public WorkerRegisterData() {}
	
	public boolean isValid() {
		return worker_email != null && worker_email.contains("@") && worker_name != null && worker_job != null;
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
