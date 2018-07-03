package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Message;
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
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

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

	private Response registerUserRetry(UserRegisterData registerData) {
		LOG.info(Message.ATTEMPT_REGISTER_USER + registerData.username);
		String code = null;
		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.username);

			try {
				datastore.get(userKey);
			} catch(EntityNotFoundException e2) {
				Filter filter =
						new Query.FilterPredicate(DSUtils.USER_EMAIL,
								FilterOperator.EQUAL, registerData.email);
				
				Query emailQuery = new Query(DSUtils.USER).setFilter(filter);

				Entity existingUser;

				try {
					existingUser = datastore.prepare(emailQuery).asSingleEntity();
				} catch(TooManyResultsException e1) {
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				if(existingUser != null) {
					txn.rollback();
					LOG.info(Message.EMAIL_ALREADY_IN_USE);
					return Response.status(Status.CONFLICT).entity(Message.EMAIL_ALREADY_IN_USE).build();
				}
				
				Date date = new Date();

				Entity user = new Entity(DSUtils.USER, registerData.username);
				userKey = user.getKey();
				user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.password));
				user.setProperty(DSUtils.USER_EMAIL, registerData.email);
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1.toString());
				user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);
				user.setProperty(DSUtils.USER_CREATIONTIMEFORMATTED,
						new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date));

				code = Long.toString(System.currentTimeMillis()).substring(6, 13);

				user.setUnindexedProperty(DSUtils.USER_ACTIVATION, code);
				
				Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
				userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

				Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

				List<Entity> list = Arrays.asList(user, useroptional, userPoints);

				datastore.put(txn, list);

				LOG.info(Message.USER_REGISTERED + registerData.username);
				txn.commit();

				return Response.ok().build();
			}

			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch(Exception e) {
			LOG.info(e.getMessage());
			LOG.info(e.toString());
			return null;
		} finally {
			if(code != null)
				Email.sendConfirmMessage(registerData.email, code);
			
			if (txn.isActive() ) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@POST
	@Path("/org")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerOrg(OrgRegisterData registerData) {
		if(!registerData.isValid()) {
			LOG.info(Message.REGISTER_DATA_INVALID);
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();
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

	private Response registerOrgRetry(OrgRegisterData registerData) {
		LOG.info(Message.ATTEMPT_REGISTER_ORG + registerData.nif);

		Key orgKey;
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			orgKey = KeyFactory.createKey(DSUtils.USER, registerData.nif);
			datastore.get(orgKey);
			LOG.info(Message.USER_ALREADY_EXISTS);
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Filter filter =
					new Query.FilterPredicate(DSUtils.USER_EMAIL,
							FilterOperator.EQUAL, registerData.email);

			Query emailQuery = new Query(DSUtils.USER).setFilter(filter);

			Entity existingOrg;

			try {
				existingOrg = datastore.prepare(emailQuery).asSingleEntity();
			} catch(TooManyResultsException e1) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(existingOrg != null) {
				LOG.info(Message.EMAIL_ALREADY_IN_USE);
				return Response.status(Status.CONFLICT).entity(Message.EMAIL_ALREADY_IN_USE).build();
			}

			Transaction txn = datastore.beginTransaction();

			Date date = new Date();

			try {
				Entity user = new Entity(DSUtils.USER, registerData.nif);
				user.setProperty(DSUtils.USER_EMAIL, registerData.email);
				user.setProperty(DSUtils.USER_PASSWORD,
						DigestUtils.sha512Hex(registerData.password));
				user.setProperty(DSUtils.USER_ACTIVATION, Profile.NOT_ACTIVATED);
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.ORG);
				user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);
				user.setProperty(DSUtils.USER_CREATIONTIMEFORMATTED,
						new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date));
				
				datastore.put(txn, user);

				Entity org = new Entity(DSUtils.ORG, user.getKey());
				org.setProperty(DSUtils.ORG_NAME, registerData.name);
				org.setProperty(DSUtils.ORG_ADDRESS, registerData.address);
				org.setProperty(DSUtils.ORG_LOCALITY, registerData.locality);
				org.setUnindexedProperty(DSUtils.ORG_PHONE, registerData.phone);
				org.setUnindexedProperty(DSUtils.ORG_ZIP, registerData.zip);
				org.setProperty(DSUtils.ORG_SERVICES, registerData.services);
				org.setProperty(DSUtils.ORG_PRIVATE, registerData.isprivate);

				datastore.put(txn, org);
				txn.commit();
				LOG.info(Message.ORG_REGISTERED + registerData.nif);
				return Response.ok().build();
			} finally {
				if (txn.isActive() ) {
					txn.rollback();
					LOG.info(Message.TXN_ACTIVE);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
			}
		}
	}
}
