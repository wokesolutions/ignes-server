package com.wokesolutions.ignes.exceptions;

import com.wokesolutions.ignes.util.Message;

public class NotSameNorAdminException extends Exception {

	private static final long serialVersionUID = 1L;

	public NotSameNorAdminException() {
		super(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
	}
}
