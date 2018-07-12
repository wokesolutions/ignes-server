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
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.UserLevel;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
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
			return Response.status(Status.FORBIDDEN).entity(Log.LOGIN_DATA_INVALID).build();

		try {
			datastore.get(KeyFactory.createKey(DSUtils.USER, data.username));
		} catch (EntityNotFoundException e1) {
			LOG.info(Log.USER_NOT_FOUND);
			return Response.status(Status.FORBIDDEN).build();
		}

		int retries = 5;
		while(true) {
			try {
				return loginRetry(data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}


	private Response loginRetry(LoginData data, final HttpServletRequest request) {
		LOG.info(Log.ATTEMPT_LOGIN + data.username);

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		final Key userKey = KeyFactory.createKey(DSUtils.USER, data.username);
		try {
			Entity user;
			try {
				user = datastore.get(txn, userKey);
			} catch (EntityNotFoundException e) {
				LOG.warning(Log.FAILED_LOGIN + data.username);
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}

			String level = user.getProperty(DSUtils.USER_LEVEL).toString();

			if(level.equals(UserLevel.ORG)) {
				String activation = user.getProperty(DSUtils.USER_ACTIVATION).toString();
				if(!activation.equals(Profile.ACTIVATED)) {
					LOG.info(Log.ORG_NOT_CONFIRMED);
					txn.rollback();
					return Response.status(Status.FORBIDDEN).build();
				}
			}

			final String email = user.getProperty(DSUtils.USER_EMAIL).toString();

			Date date = new Date();

			Key statsK = KeyFactory.createKey(userKey, DSUtils.USERSTATS, data.username);

			Entity stats;
			try {
				stats = datastore.get(statsK);
			} catch(EntityNotFoundException e2) {
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			String hashedPWD = user.getProperty(DSUtils.USER_PASSWORD).toString();

			Object hashedForgotPWDo = user.getProperty(DSUtils.USER_FORGOTPASSWORD);

			if(!hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {
				if(hashedForgotPWDo != null) {
					if(!hashedForgotPWDo.equals(DigestUtils.sha512Hex(data.password))) {
						LOG.info(Log.WRONG_PASSWORD + data.username);

						stats.setProperty(DSUtils.USERSTATS_LOGINSFAILED,
								1L + (long) stats.getProperty(DSUtils.USERSTATS_LOGINSFAILED));

						datastore.put(txn,stats);				
						txn.commit();
						return Response.status(Status.FORBIDDEN).build();
					} else {
						user.setUnindexedProperty(DSUtils.USER_PASSWORD,
								user.getProperty(DSUtils.USER_FORGOTPASSWORD));
						
						user.setUnindexedProperty(DSUtils.USER_FORGOTPASSWORD, null);
					}
				} else {
					LOG.info(Log.WRONG_PASSWORD + data.username);

					stats.setProperty(DSUtils.USERSTATS_LOGINSFAILED,
							1L + (long) stats.getProperty(DSUtils.USERSTATS_LOGINSFAILED));

					datastore.put(txn,stats);				
					txn.commit();
					return Response.status(Status.FORBIDDEN).build();
				}
			} else
				user.setProperty(DSUtils.USER_FORGOTPASSWORD, null);

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
			//final String username = data.username;

			Query deviceQuery = new Query(DSUtils.DEVICE);
			Filter userFilter = new Query.FilterPredicate(DSUtils.DEVICE_USER,
					FilterOperator.EQUAL, userKey);
			deviceQuery.setFilter(userFilter);

			List<Entity> devices = datastore.prepare(deviceQuery)
					.asList(FetchOptions.Builder.withDefaults());

			Entity existingDevice = null;

			for(Entity device : devices) {
				if(device.getKey().getName().equals(deviceid)) {
					existingDevice = device;
					break;
				}
			}

			if(existingDevice == null) {
				String app = request.getAttribute(CustomHeader.DEVICE_APP_ATT).toString();

				existingDevice = new Entity(DSUtils.DEVICE, deviceid);
				existingDevice.setUnindexedProperty(DSUtils.DEVICE_COUNT, 1L);
				existingDevice.setProperty(DSUtils.DEVICE_USER, userKey);
				existingDevice.setProperty(DSUtils.DEVICE_APP, app);

				if(devices.size() != 0)
					Email.sendNewDeviceMessage(email,
							request.getAttribute(CustomHeader.DEVICE_INFO_ATT).toString());
			} else
				existingDevice.setProperty(DSUtils.DEVICE_COUNT,
						(long) existingDevice.getProperty(DSUtils.DEVICE_COUNT) + 1L);

			datastore.put(txn, existingDevice);

			Entity log = new Entity(DSUtils.USERLOG, user.getKey());

			log.setProperty(DSUtils.USERLOG_TYPE, IN);
			log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
			log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
			log.setProperty(DSUtils.USERLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
			log.setProperty(DSUtils.USERLOG_CITY, request.getHeader("X-AppEngine-City"));
			log.setProperty(DSUtils.USERLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
			log.setProperty(DSUtils.USERLOG_TIME, date);
			log.setProperty(DSUtils.USERLOG_DEVICE, deviceid);

			Entity newToken = new Entity(DSUtils.TOKEN);
			newToken.setProperty(DSUtils.TOKEN_STRING, token);
			newToken.setUnindexedProperty(DSUtils.TOKEN_DATE, date);
			newToken.setProperty(DSUtils.TOKEN_DEVICE, deviceid);
			newToken.setProperty(DSUtils.TOKEN_USER, userKey);

			stats.setProperty(DSUtils.USERSTATS_LOGINS,
					(long) stats.getProperty(DSUtils.USERSTATS_LOGINS) + 1L);
			stats.setProperty(DSUtils.USERSTATS_LASTIN, new Date());

			LOG.info(data.username + Log.LOGGED_IN);
			List<Entity> list = Arrays.asList(log, stats, newToken, user);
			datastore.put(txn, list);

			ResponseBuilder response;

			response = Response.ok()
					.header(CustomHeader.AUTHORIZATION, token)
					.header(CustomHeader.LEVEL, level);

			if(level.equals(UserLevel.WORKER)) {
				Key workerK = KeyFactory.createKey(user.getKey(), DSUtils.WORKER, data.username);
				
				Entity worker;
				try {
					worker = datastore.get(workerK);
				} catch(EntityNotFoundException e) {
					LOG.warning(Log.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
				
				response.header(CustomHeader.ORG, worker.getProperty(DSUtils.WORKER_ORGNAME));
				txn.commit();
				return response.build();
			}

			if(level.equals(UserLevel.ORG)) {
				response.header(CustomHeader.NIF, user.getKey().getName());

				Query orgQ = new Query(DSUtils.ORG).setAncestor(userKey);
				Entity org;
				try {
					org = datastore.prepare(orgQ).asSingleEntity();
				} catch(TooManyResultsException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				if(org == null) {
					LOG.info(Log.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				response.header(CustomHeader.ORG, org.getProperty(DSUtils.ORG_NAME).toString());
			}

			if(!(level.equals(UserLevel.GUEST) || level.equals(UserLevel.WORKER)
					|| level.equals(UserLevel.ADMIN))) {
				String actProp = user.getProperty(DSUtils.USER_ACTIVATION).toString();

				String activated = actProp.equals(Profile.ACTIVATED)?
						CustomHeader.TRUE : CustomHeader.FALSE;

				response.header(CustomHeader.ACTIVATED, activated);
			}

			txn.commit();
			return response.build();	
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				LOG.info(Log.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
