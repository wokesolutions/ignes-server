package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.OrgRegisterData;
import com.wokesolutions.ignes.data.UserRegisterData;
import com.wokesolutions.ignes.util.Category;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/register")
public class Register {

	private static final Logger LOG = Logger.getLogger(Register.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory
			.getDatastoreService();

	public Register() {}

	@POST
	@Path("/user")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerUser(UserRegisterData registerData) {
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Log.REGISTER_DATA_INVALID).build();

		int retries = 5;
		while(true) {
			try {
				return registerUserRetry(registerData);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response registerUserRetry(UserRegisterData data) {
		LOG.info(Log.ATTEMPT_REGISTER_USER + data.username);
		String code = null;
		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, data.username);

			try {
				datastore.get(userKey);
			} catch(EntityNotFoundException e2) {
				Filter filter =
						new Query.FilterPredicate(DSUtils.USER_EMAIL,
								FilterOperator.EQUAL, data.email);
				
				Query emailQuery = new Query(DSUtils.USER).setFilter(filter);

				Entity existingUser;

				try {
					existingUser = datastore.prepare(emailQuery).asSingleEntity();
				} catch(TooManyResultsException e1) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				if(existingUser != null) {
					txn.rollback();
					LOG.info(Log.EMAIL_ALREADY_IN_USE);
					return Response.status(Status.CONFLICT).entity(Log.EMAIL_ALREADY_IN_USE).build();
				}
				
				Date date = new Date();

				Entity user = new Entity(DSUtils.USER, data.username);
				userKey = user.getKey();
				user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(data.password));
				user.setUnindexedProperty(DSUtils.USER_FORGOTPASSWORD, null);
				user.setProperty(DSUtils.USER_EMAIL, data.email);
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL0);
				user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);
				
				SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				sdf.setTimeZone(TimeZone.getTimeZone(Report.PORTUGAL));
				
				user.setProperty(DSUtils.USER_CREATIONTIMEFORMATTED, sdf.format(date));

				code = Long.toString(System.currentTimeMillis()).substring(6, 13);

				user.setUnindexedProperty(DSUtils.USER_ACTIVATION, code);
				
				Entity userPoints = new Entity(DSUtils.USERPOINTS, data.username, user.getKey());
				userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

				Entity useroptional = new Entity(DSUtils.USEROPTIONAL, data.username, userKey);
				
				Entity userstats = new Entity(DSUtils.USERSTATS, data.username, userKey);
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGINS, 0L);
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGOUTS, 0L);

				List<Entity> list = Arrays.asList(user, useroptional, userPoints, userstats);

				datastore.put(txn, list);

				LOG.info(Log.USER_REGISTERED + data.username);
				txn.commit();

				return Response.ok().build();
			}

			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Log.USER_ALREADY_EXISTS).build(); 
		} finally {
			if(code != null)
				Email.sendConfirmMessage(data.email, code);
			
			if (txn.isActive() ) {
				txn.rollback();
				LOG.info(Log.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@POST
	@Path("/org")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerOrg(OrgRegisterData registerData) {
		if(!registerData.isValid()) {
			LOG.info(Log.REGISTER_DATA_INVALID);
			return Response.status(Status.BAD_REQUEST).entity(Log.REGISTER_DATA_INVALID).build();
		}

		int retries = 5;
		while(true) {
			try {
				return registerOrgRetry(registerData);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response registerOrgRetry(OrgRegisterData data) {
		LOG.info(Log.ATTEMPT_REGISTER_ORG + data.nif);

		JSONArray array = new JSONArray(data.categories);
		
		for(int i = 0; i < array.length(); i++)
			if(!Category.isEq(array.getString(i)))
				return Response.status(Status.BAD_REQUEST).build();

		Key orgKey;
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			orgKey = KeyFactory.createKey(DSUtils.USER, data.nif);
			datastore.get(orgKey);
			LOG.info(Log.USER_ALREADY_EXISTS);
			return Response.status(Status.CONFLICT).entity(Log.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Filter filter =
					new Query.FilterPredicate(DSUtils.USER_EMAIL,
							FilterOperator.EQUAL, data.email);

			Query emailQuery = new Query(DSUtils.USER).setFilter(filter);

			Entity existingOrg;

			try {
				existingOrg = datastore.prepare(emailQuery).asSingleEntity();
			} catch(TooManyResultsException e1) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(existingOrg != null) {
				LOG.info(Log.EMAIL_ALREADY_IN_USE);
				return Response.status(Status.CONFLICT).entity(Log.EMAIL_ALREADY_IN_USE).build();
			}

			Transaction txn = datastore.beginTransaction();

			Date date = new Date();

			try {
				Entity user = new Entity(DSUtils.USER, data.nif);
				user.setProperty(DSUtils.USER_EMAIL, data.email);
				user.setUnindexedProperty(DSUtils.USER_PASSWORD,
						DigestUtils.sha512Hex(data.password));
				user.setUnindexedProperty(DSUtils.USER_FORGOTPASSWORD, null);
				user.setProperty(DSUtils.USER_ACTIVATION, Profile.NOT_ACTIVATED);
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.ORG);
				user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);

				SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				sdf.setTimeZone(TimeZone.getTimeZone(Report.PORTUGAL));
				
				user.setProperty(DSUtils.USER_CREATIONTIMEFORMATTED, sdf.format(date));
				
				datastore.put(txn, user);

				Entity org = new Entity(DSUtils.ORG, data.nif, user.getKey());
				org.setProperty(DSUtils.ORG_NAME, data.name);
				org.setProperty(DSUtils.ORG_ADDRESS, data.address);
				org.setProperty(DSUtils.ORG_LOCALITY, data.locality);
				org.setUnindexedProperty(DSUtils.ORG_PHONE, data.phone);
				org.setUnindexedProperty(DSUtils.ORG_ZIP, data.zip);
				org.setProperty(DSUtils.ORG_CATEGORIES, data.categories);
				org.setProperty(DSUtils.ORG_PRIVATE, data.isprivate);

				Entity userstats = new Entity(DSUtils.USERSTATS, data.nif, user.getKey());
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGINS, 0L);
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
				userstats.setUnindexedProperty(DSUtils.USERSTATS_LOGOUTS, 0L);
				
				datastore.put(txn, userstats);

				datastore.put(txn, org);
				txn.commit();
				LOG.info(Log.ORG_REGISTERED + data.nif);
				
				return Response.ok().build();
			} finally {
				if (txn.isActive() ) {
					txn.rollback();
					LOG.info(Log.TXN_ACTIVE);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
			}
		}
	}
}
