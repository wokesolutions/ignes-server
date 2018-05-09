package com.wokesolutions.ignes.data;

public class WorkerRegisterData {

	public String worker_name;
	public String worker_password;
	public String worker_username;
	public String worker_email;
	public String worker_gender;
	public String worker_phone;
	public String worker_address;
	public String worker_locality;
	public String worker_zip;
	public String worker_birth;
	public String worker_job;
	public String[] worker_skills;
	public String worker_code;
	
	public WorkerRegisterData() {}
	
	public boolean isValid() {
		return worker_username != null && worker_email != null && worker_password != null;
	}
}
