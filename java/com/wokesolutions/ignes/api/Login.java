package com.wokesolutions.ignes.api;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.LoginData;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.UserLevel;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.JWTUtils;

@Path("/login")
public class Login {

	private static final Logger LOG = Logger.getLogger(Login.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final String IN = "in";

	public Login() {}

	@POST
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response login(LoginData data,
			@Context HttpServletRequest request,
			@Context HttpHeaders headers) {
		if(!data.isValid())
			return Response.status(Status.FORBIDDEN).entity(Message.LOGIN_DATA_INVALID).build();

		try {
			datastore.get(KeyFactory.createKey(DSUtils.USER, data.username));
		} catch (EntityNotFoundException e1) {
			LOG.info(Message.USER_NOT_FOUND);
			return Response.status(Status.FORBIDDEN).build();
		}

		int retries = 5;
		while(true) {
			try {
				return loginUserRetry(data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}


	private Response loginUserRetry(LoginData data, final HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_LOGIN + data.username);

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withDefaults());

		final Key userKey = KeyFactory.createKey(DSUtils.USER, data.username);
		try {
			Entity user;
			try {
				user = datastore.get(userKey);
			} catch (EntityNotFoundException e) {
				LOG.warning(Message.FAILED_LOGIN + data.username);
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}

			final String email = user.getProperty(DSUtils.USER_EMAIL).toString();

			Date date = new Date();

			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(userKey);

			Entity stats;
			try {
				stats = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(stats == null)
				stats = new Entity(DSUtils.USERSTATS, user.getKey());

			String hashedPWD = (String) user.getProperty(DSUtils.USER_PASSWORD);
			if(!hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {
				LOG.warning(Message.WRONG_PASSWORD + data.username);

				if(!stats.hasProperty(DSUtils.USERSTATS_LOGINSFAILED))
					stats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 1L);
				else
					stats.setProperty(DSUtils.USERSTATS_LOGINSFAILED,
							1L + (long) stats.getProperty(DSUtils.USERSTATS_LOGINSFAILED));

				datastore.put(txn,stats);				
				txn.commit();
				return Response.status(Status.FORBIDDEN).build();
			}

			String token;
			try {
				token = JWTUtils.createJWT(data.username,
						user.getProperty(DSUtils.USER_LEVEL).toString(), date);
			} catch (UnsupportedEncodingException e){
				LOG.warning(e.getMessage());
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			} catch (JWTCreationException e){
				LOG.warning(e.getMessage());
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}	

			final String deviceid = request.getAttribute(CustomHeader.DEVICE_ID_ATT).toString();
			final String username = data.username;

			Runnable deviceChecker = new Runnable() {
				public void run() {
					Query deviceQuery = new Query(DSUtils.DEVICE);
					Filter userFilter = new Query.FilterPredicate(DSUtils.DEVICE_USER,
							FilterOperator.EQUAL, userKey);
					deviceQuery.setFilter(userFilter);

					List<Entity> devices = datastore.prepare(deviceQuery)
							.asList(FetchOptions.Builder.withDefaults());

					Entity existingDevice = null;

					for(Entity device : devices) {
						if(device.getProperty(DSUtils.DEVICE_ID).toString().equals(deviceid)) {
							existingDevice = device;
							break;
						}
					}

					if(existingDevice == null) {
						existingDevice = new Entity(DSUtils.DEVICE);
						existingDevice.setProperty(DSUtils.DEVICE_ID, deviceid);
						existingDevice.setUnindexedProperty(DSUtils.DEVICE_COUNT, 1L);
						existingDevice.setProperty(DSUtils.DEVICE_USER, username);

						Email.sendNewDeviceMessage(email,
								request.getAttribute(CustomHeader.DEVICE_INFO_ATT).toString());
					} else
						existingDevice.setProperty(DSUtils.DEVICE_COUNT,
								(long) existingDevice.getProperty(DSUtils.DEVICE_COUNT) + 1L);

					datastore.put(existingDevice);
				}
			};

			Entity log = new Entity(DSUtils.USERLOG, user.getKey());

			log.setProperty(DSUtils.USERLOG_TYPE, IN);
			log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
			log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
			log.setProperty(DSUtils.USERLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
			log.setProperty(DSUtils.USERLOG_CITY, request.getHeader("X-AppEngine-City"));
			log.setProperty(DSUtils.USERLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
			log.setProperty(DSUtils.USERLOG_TIME, date);
			log.setProperty(DSUtils.USERLOG_DEVICE, deviceid);

			Thread checkNewDevice = ThreadManager.createBackgroundThread(deviceChecker);
			checkNewDevice.start();

			Entity newToken = new Entity(DSUtils.TOKEN);
			newToken.setProperty(DSUtils.TOKEN_STRING, token);
			newToken.setUnindexedProperty(DSUtils.TOKEN_DATE, date);
			newToken.setProperty(DSUtils.TOKEN_DEVICE, deviceid);
			newToken.setProperty(DSUtils.TOKEN_USER, userKey);

			LOG.info(data.username + Message.LOGGED_IN);
			// Batch operation
			List<Entity> logs = Arrays.asList(log, stats, newToken);
			datastore.put(txn, logs);
			txn.commit();

			ResponseBuilder response;

			String level = user.getProperty(DSUtils.USER_LEVEL).toString();

			response = Response.ok()
					.header(CustomHeader.AUTHORIZATION, token)
					.header(CustomHeader.LEVEL, level);

			if(level.equals(UserLevel.WORKER)) {
				response.header(CustomHeader.ORG, user.getProperty(DSUtils.WORKER_ORG));
				return response.build();
			}

			String userLevel = user.getProperty(DSUtils.USER_LEVEL).toString();

			if(!(userLevel.equals(UserLevel.GUEST) || userLevel.equals(UserLevel.WORKER))
					|| userLevel.equals(UserLevel.ADMIN)) {
				String actProp = user.getProperty(DSUtils.USER_ACTIVATION).toString();
				String activated = actProp.equals(Profile.ACTIVATED) ?
						CustomHeader.TRUE : CustomHeader.FALSE;

				response.header(CustomHeader.ACTIVATED, activated);
			}

			return response.build();	
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
