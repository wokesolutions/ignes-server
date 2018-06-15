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
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.OrgLoginData;
import com.wokesolutions.ignes.data.UserLoginData;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;

@Path("/login")
public class Login {

	private static final Logger LOG = Logger.getLogger(Login.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	
	private static final String IN = "in";
	private static final String TRUE = "true";
	private static final String FALSE = "false";
	private static final String ACTIVATED = "Activated";
	private static final String LEVEL = "Level";

	public Login() {}

	@POST
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response loginUser(UserLoginData data,
			@Context HttpServletRequest request,
			@Context HttpHeaders headers) {
		if(!data.isValid())
			return Response.status(Status.FORBIDDEN).entity(Message.LOGIN_DATA_INVALID).build();

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


	private Response loginUserRetry(UserLoginData data, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_LOGIN + data.username);

		Transaction txn = datastore.beginTransaction();
		Key userKey = KeyFactory.createKey(DSUtils.USER, data.username);
		try {
			Entity user = datastore.get(userKey);

			// Obtain the user login statistics
			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(userKey);

			Entity result;
			try {
				result = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			Entity ustats = null;
			if(result == null) {
				ustats = new Entity(DSUtils.USERSTATS, user.getKey());
				ustats.setProperty(DSUtils.USERSTATS_LOGINS, 0L);
				ustats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
				ustats.setProperty(DSUtils.USERSTATS_LOGOUTS, 0L);
			} else {
				ustats = result;
			}
			
			Date date = new Date();

			String hashedPWD = (String) user.getProperty(DSUtils.USER_PASSWORD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {

				// Construct the logs
				Entity log = new Entity(DSUtils.USERLOG, user.getKey());

				log.setProperty(DSUtils.USERLOG_TYPE, IN);
				log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.USERLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.USERLOG_CITY, request.getHeader("X-AppEngine-City"));
				log.setProperty(DSUtils.USERLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
				log.setProperty(DSUtils.USERLOG_TIME, date);

				// Get the user statistics and updates it
				ustats.setProperty(DSUtils.USERSTATS_LOGINS, 1L + (long) ustats.getProperty(DSUtils.USERSTATS_LOGINS));
				ustats.setProperty(DSUtils.USERSTATS_LASTIN, date);

				// Return token		
				try {
					String token = JWTUtils.createJWT(data.username,
							user.getProperty(DSUtils.USER_LEVEL).toString(), date);
					
					Entity newToken = new Entity(DSUtils.TOKEN, userKey);
					
					newToken.setProperty(DSUtils.TOKEN_STRING, token);
					newToken.setProperty(DSUtils.TOKEN_DATE, date);
					newToken.setProperty(DSUtils.TOKEN_IP, request.getRemoteAddr());

					LOG.info(data.username + Message.LOGGED_IN);
					// Batch operation
					List<Entity> logs = Arrays.asList(log, ustats, newToken);
					datastore.put(txn, logs);
					txn.commit();
					
					String activated;
					boolean act = user.getProperty(DSUtils.USER_CODE).equals(Profile.ACTIVATED);
					
					if(act)
						activated = TRUE;
					else
						activated = FALSE;
					
					return Response.ok()
							.header(JWTUtils.AUTHORIZATION, token)
							.header(LEVEL, user.getProperty(DSUtils.USER_LEVEL))
							.header(ACTIVATED, activated)
							.build();
				} catch (UnsupportedEncodingException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				} catch (JWTCreationException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}		
			} else {
				// Incorrect password
				LOG.warning(Message.WRONG_PASSWORD + data.username);
				ustats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 1L + (long) ustats.getProperty(DSUtils.USERSTATS_LOGINSFAILED));
				datastore.put(txn,ustats);				
				txn.commit();
				return Response.status(Status.FORBIDDEN).build();				
			}
		} catch (EntityNotFoundException e) {
			// Username does not exist
			LOG.warning(Message.FAILED_LOGIN + data.username);
			txn.rollback();
			return Response.status(Status.FORBIDDEN).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
	
	@POST
	@Path("/worker")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response loginWorker(UserLoginData logindata, @Context HttpServletRequest request) {
		if(!logindata.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.LOGIN_DATA_INVALID).build();

		int retries = 5;
		while(true) {
			try {
				return loginWorkerRetry(logindata, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}
	
	private Response loginWorkerRetry(UserLoginData data, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_LOGIN + data.username);

		Transaction txn = datastore.beginTransaction();
		Key workerKey = KeyFactory.createKey(DSUtils.WORKER, data.username);
		try {
			Entity user = datastore.get(workerKey);

			// Obtain the user login statistics
			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(workerKey);

			Entity result;
			try {
				result = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			Entity ustats = null;
			if(result == null) {
				ustats = new Entity(DSUtils.USERSTATS, user.getKey());
				ustats.setProperty(DSUtils.USERSTATS_LOGINS, 0L);
				ustats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
				ustats.setProperty(DSUtils.USERSTATS_LOGOUTS, 0L);
			} else {
				ustats = result;
			}
			
			Date date = new Date();

			String hashedPWD = (String) user.getProperty(DSUtils.USER_PASSWORD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {

				// Construct the logs
				Entity log = new Entity(DSUtils.USERLOG, user.getKey());

				log.setProperty(DSUtils.USERLOG_TYPE, IN);
				log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.USERLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.USERLOG_CITY, request.getHeader("X-AppEngine-City"));
				log.setProperty(DSUtils.USERLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
				log.setProperty(DSUtils.USERLOG_TIME, date);

				// Get the user statistics and updates it
				ustats.setProperty(DSUtils.USERSTATS_LOGINS, 1L + (long) ustats.getProperty(DSUtils.USERSTATS_LOGINS));
				ustats.setProperty(DSUtils.USERSTATS_LASTIN, date);

				// Return token		
				try {
					String token = JWTUtils.createJWT(data.username,
							user.getProperty(DSUtils.USER_LEVEL).toString(), date);

					LOG.info(data.username + Message.LOGGED_IN);

					Entity newToken = new Entity(DSUtils.TOKEN, workerKey);
					
					newToken.setProperty(DSUtils.TOKEN_STRING, token);
					newToken.setProperty(DSUtils.TOKEN_DATE, date);
					newToken.setProperty(DSUtils.TOKEN_IP, request.getRemoteAddr());

					// Batch operation
					List<Entity> logs = Arrays.asList(log, ustats, newToken);
					datastore.put(txn, logs);
					txn.commit();
					
					return Response.ok()
							.header(JWTUtils.AUTHORIZATION, token)
							.header(LEVEL, UserLevel.WORKER)
							.build();
				} catch (UnsupportedEncodingException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				} catch (JWTCreationException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}		
			} else {
				// Incorrect password
				LOG.warning(Message.WRONG_PASSWORD + data.username);
				ustats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 1L + (long) ustats.getProperty(DSUtils.USERSTATS_LOGINSFAILED));
				datastore.put(txn,ustats);				
				txn.commit();
				return Response.status(Status.FORBIDDEN).build();				
			}
		} catch (EntityNotFoundException e) {
			// Username does not exist
			LOG.warning(Message.FAILED_LOGIN + data.username);
			txn.rollback();
			return Response.status(Status.FORBIDDEN).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@POST
	@Path("/org")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response loginOrg(OrgLoginData loginData,
			@Context HttpServletRequest request) {
		if(!loginData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.LOGIN_DATA_INVALID).build();

		int retries = 5;
		while(true) {
			try {
				return loginOrgRetry(loginData, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}


	private Response loginOrgRetry(OrgLoginData loginData, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_LOGIN + loginData.nif);

		Transaction txn = datastore.beginTransaction();
		Key orgKey = KeyFactory.createKey(DSUtils.ORG, loginData.nif);
		try {
			Entity org = datastore.get(orgKey);
			
			if(!(Boolean) org.getProperty(DSUtils.ORG_CONFIRMED)) {
				LOG.info(Message.ORG_NOT_CONFIRMED);
				return Response.status(Status.FORBIDDEN).build();
			}

			// Obtain the user login statistics
			Query statsQuery = new Query(DSUtils.ORGSTATS).setAncestor(orgKey);
			List<Entity> results = datastore.prepare(statsQuery).asList(FetchOptions.Builder.withDefaults());
			Entity ostats = null;
			if (results.isEmpty()) {
				ostats = new Entity(DSUtils.ORGSTATS, org.getKey());
				ostats.setProperty(DSUtils.ORGSTATS_LOGINS, 0L);
				ostats.setProperty(DSUtils.ORGSTATS_LOGINSFAILED, 0L);
				ostats.setProperty(DSUtils.ORGSTATS_LOGOUTS, 0L);
			} else {
				ostats = results.get(0);
			}
			
			Date date = new Date();

			String hashedPWD = (String) org.getProperty(DSUtils.ORG_PASSWORD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(loginData.password))) {

				// Construct the logs
				Entity log = new Entity(DSUtils.ORGLOG, org.getKey());

				log.setProperty(DSUtils.ORGLOG_TYPE, IN);
				log.setProperty(DSUtils.ORGLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.ORGLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.ORGLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.ORGLOG_CITY, request.getHeader("X-AppEngine-City"));
				log.setProperty(DSUtils.ORGLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
				log.setProperty(DSUtils.ORGLOG_TIME, date);

				// Get the org statistics and updates it
				ostats.setProperty(DSUtils.ORGSTATS_LOGINS, 1L + (long) ostats.getProperty(DSUtils.ORGSTATS_LOGINS));
				ostats.setProperty(DSUtils.ORGSTATS_LASTIN, date);

				// Return token		
				try {
					Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
					String token = JWT.create()
							.withIssuer(JWTUtils.ISSUER)
							.withClaim(JWTUtils.ORG, true)
							.withClaim(JWTUtils.USERNAME, loginData.nif)
							.withClaim(JWTUtils.IAT, date)
							.sign(algorithm);

					LOG.info(loginData.nif + Message.LOGGED_IN);

					Entity newToken = new Entity(DSUtils.TOKEN, orgKey);
					
					newToken.setProperty(DSUtils.TOKEN_STRING, token);
					newToken.setProperty(DSUtils.TOKEN_DATE, date);
					newToken.setProperty(DSUtils.TOKEN_IP, request.getRemoteAddr());

					// Batch operation
					List<Entity> logs = Arrays.asList(log, ostats, newToken);
					datastore.put(txn, logs);
					txn.commit();
					
					return Response.ok(CustomHeader.LEVEL + ": " + DSUtils.ORG)
							.header(JWTUtils.AUTHORIZATION, token)
							.build();
				} catch (UnsupportedEncodingException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				} catch (JWTCreationException e){
					LOG.warning(e.getMessage());
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}		
			} else {
				// Incorrect password
				LOG.warning(Message.WRONG_PASSWORD + loginData.nif);
				ostats.setProperty(DSUtils.ORGSTATS_LOGINSFAILED, 1L + (long) ostats.getProperty(DSUtils.ORGSTATS_LOGINSFAILED));
				datastore.put(txn,ostats);				
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();				
			}
		} catch (EntityNotFoundException e) {
			// Username does not exist
			LOG.warning(Message.FAILED_LOGIN + loginData.nif);
			txn.rollback();
			return Response.status(Status.FORBIDDEN).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}

	}
}