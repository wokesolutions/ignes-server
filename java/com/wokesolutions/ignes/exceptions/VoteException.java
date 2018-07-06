package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Log;

public class VoteException extends Exception {

	private static final long serialVersionUID = 1L;

	public VoteException() {
		super(Log.UNEXPECTED_ERROR);
	}
}
