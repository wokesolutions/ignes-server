package com.wokesolutions.ignes.api;

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
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public Register() {}

	@POST
	@Path("/user")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerUser(UserRegisterData registerData) {
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

		if(!registerData.email.contains("@"))
			return Response.status(Status.BAD_REQUEST).entity(Message.INVALID_EMAIL).build();

		if(!UserRegisterData.isUsernameValid(registerData.username))
			return Response.status(Status.BAD_REQUEST).entity(Message.INVALID_USERNAME).build();

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

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.username);

			try {
				datastore.get(userKey);
			} catch(EntityNotFoundException e2) {
				try {
					Key orgKey = KeyFactory.createKey(DSUtils.ORG, registerData.username);
					datastore.get(orgKey);
				} catch(EntityNotFoundException e3) {
					Filter filter =
							new Query.FilterPredicate(DSUtils.USER_EMAIL,
									FilterOperator.EQUAL, registerData.email);

					Query emailQuery = new Query(DSUtils.USER).setFilter(filter);

					Entity existingUser;

					try {
						existingUser = datastore.prepare(emailQuery).asSingleEntity();
					} catch(TooManyResultsException e1) {
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					if(existingUser != null)
						return Response.status(Status.CONFLICT).entity(Message.EMAIL_ALREADY_IN_USE).build();

					Entity user = new Entity(DSUtils.USER, registerData.username);
					userKey = user.getKey();
					user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.password));
					user.setProperty(DSUtils.USER_EMAIL, registerData.email);
					user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1.toString());
					user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, new Date());

					String code = Long.toString(System.currentTimeMillis()).substring(6, 13);

					user.setUnindexedProperty(DSUtils.USER_CODE, code);

					Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
					userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

					Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

					List<Entity> list = Arrays.asList(user, useroptional, userPoints);

					datastore.put(txn, list);

					LOG.info(Message.USER_REGISTERED + registerData.username);
					txn.commit();

					Email.sendConfirmMessage(registerData.email, code);

					return Response.ok().build();
				}
			}

			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} finally {
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
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

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
		LOG.info(Message.ATTEMPT_REGISTER_ORG + registerData.org_nif);

		Transaction txn = datastore.beginTransaction();
		Key orgKey = null;
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			orgKey = KeyFactory.createKey(DSUtils.ORG, registerData.org_nif);
			datastore.get(orgKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.ORG_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Filter filter =
					new Query.FilterPredicate(DSUtils.USER_EMAIL,
							FilterOperator.EQUAL, registerData.org_email);

			Query emailQuery = new Query(DSUtils.ORG).setFilter(filter);

			Entity existingOrg;

			try {
				existingOrg = datastore.prepare(emailQuery).asSingleEntity();
			} catch(TooManyResultsException e1) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(existingOrg != null)
				return Response.status(Status.CONFLICT).entity(Message.EMAIL_ALREADY_IN_USE).build();

			Entity org = new Entity(DSUtils.ORG, registerData.org_nif);
			org.setUnindexedProperty(DSUtils.ORG_NAME, registerData.org_name);
			org.setUnindexedProperty(DSUtils.ORG_PASSWORD, DigestUtils.sha512Hex(registerData.org_password));
			org.setProperty(DSUtils.ORG_EMAIL, registerData.org_email);
			org.setProperty(DSUtils.ORG_ADDRESS, registerData.org_address);
			org.setProperty(DSUtils.ORG_LOCALITY, registerData.org_locality);
			org.setUnindexedProperty(DSUtils.ORG_PHONE, registerData.org_phone);
			org.setUnindexedProperty(DSUtils.ORG_ZIP, registerData.org_zip);
			org.setProperty(DSUtils.ORG_SERVICES, registerData.org_services);
			org.setProperty(DSUtils.ORG_ISFIRESTATION, registerData.org_isfirestation);
			org.setUnindexedProperty(DSUtils.ORG_CREATIONTIME, new Date());
			org.setProperty(DSUtils.ORG_CONFIRMED, CustomHeader.FALSE);

			datastore.put(txn, org);
			LOG.info(Message.ORG_REGISTERED + registerData.org_nif);
			txn.commit();
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
