package com.wokesolutions.ignes.api;

import java.util.Date;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/verifytoken")
public class VerifyToken {

	public static final Logger LOG = Logger.getLogger(VerifyToken.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public VerifyToken() {}
	
	private static final String LEVEL = "LEVEL";

	@GET
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyToken(@Context HttpServletRequest request) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Entity user;
		Key userK = KeyFactory.createKey(DSUtils.USER, username);
		
		String deviceid = request.getAttribute(CustomHeader.DEVICE_ID_ATT).toString();
		Key deviceK = KeyFactory.createKey(DSUtils.DEVICE, deviceid);
		
		try {
			datastore.get(deviceK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}
		
		Entity skipLogin = new Entity(DSUtils.SKIPLOGIN, userK);
		skipLogin.setProperty(DSUtils.SKIPLOGIN_TIME, new Date());
		skipLogin.setProperty(DSUtils.SKIPLOGIN_DEVICE, deviceK);
		
		try {
			user = datastore.get(userK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.USER_NOT_FOUND);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}
		
		String level = user.getProperty(DSUtils.USER_LEVEL).toString();
		
		ResponseBuilder builder = Response.ok()
				.header(CustomHeader.LEVEL, level);

		if(level.startsWith(LEVEL) || level.equals(UserLevel.ORG))
			builder.header(CustomHeader.ACTIVATED,
					user.getProperty(DSUtils.USER_ACTIVATION).toString());
		
		datastore.put(skipLogin);
		
		return builder.entity(Status.OK).build();
	}
}