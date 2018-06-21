package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Message;

public class UserNotWorkerException extends Exception {

	private static final long serialVersionUID = 1L;

	public UserNotWorkerException() {
		super(Message.WORKER_NOT_FOUND);
	}
}
