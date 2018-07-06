package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Log;

public class UserNotWorkerException extends Exception {

	private static final long serialVersionUID = 1L;

	public UserNotWorkerException() {
		super(Log.WORKER_NOT_FOUND);
	}
}
