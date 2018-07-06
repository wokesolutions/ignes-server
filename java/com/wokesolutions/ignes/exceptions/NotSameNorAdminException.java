package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Log;

public class NotSameNorAdminException extends Exception {

	private static final long serialVersionUID = 1L;

	public NotSameNorAdminException() {
		super(Log.REQUESTER_IS_NOT_USER_OR_ADMIN);
	}
}
