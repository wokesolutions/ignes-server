package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Message;

public class VoteException extends Exception {

	private static final long serialVersionUID = 1L;

	public VoteException() {
		super(Message.UNEXPECTED_ERROR);
	}
}
