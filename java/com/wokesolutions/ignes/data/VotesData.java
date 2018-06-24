package com.wokesolutions.ignes.data;

public class VotesData {
	
	public String report1;
	public String report2;
	public String report3;
	public String report4;
	public String report5;
	public String report6;
	public String report7;
	public String report8;
	public String report9;
	public String report10;
	
	public VotesData() {}
	
	public boolean isValid() {
		return report1 != null && !report1.equals("");
	}
	
	public String getReport(int rep) {
		if(rep == 1)
			return report1;
		else if(rep == 2)
			return report2;
		else if(rep == 3)
			return report3;
		else if(rep == 4)
			return report4;
		else if(rep == 5)
			return report5;
		else if(rep == 6)
			return report6;
		else if(rep == 7)
			return report7;
		else if(rep == 8)
			return report8;
		else if(rep == 9)
			return report9;
		else if(rep == 10)
			return report10;
		else return null;
	}
}
