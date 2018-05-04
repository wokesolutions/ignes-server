package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.UserRegisterData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/adminmanager")
public class AdminManager {

	private static final Logger LOG = Logger.getLogger(AdminManager.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public AdminManager() {}

	@POST
	@Path("/register")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerAdmin(UserRegisterData registerData) {
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

		int retries = 5;
		while(true) {
			try {
				return registerAdminRetry(registerData);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response registerAdminRetry(UserRegisterData registerData) {
		LOG.info(Message.ATTEMPT_REGISTER_ADMIN + registerData);

		Transaction txn = datastore.beginTransaction();
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.user_username);
			datastore.get(userKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Date date = new Date();
			Entity user = new Entity(DSUtils.USER, registerData.user_username);
			Key userKey = user.getKey();
			Entity admin = new Entity(DSUtils.ADMIN, userKey);
			
			admin.setUnindexedProperty(DSUtils.ADMIN_CREATIONTIME, date);

			user.setProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.user_password));
			user.setProperty(DSUtils.USER_EMAIL, registerData.user_email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.ADMIN);
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);
			
			Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

			List<Entity> list = Arrays.asList(user, admin, useroptional);

			datastore.put(txn, list);
			LOG.info(Message.ADMIN_REGISTERED + registerData);
			txn.commit();
			return Response.ok().build();
		} finally {
			if (txn.isActive() ) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@PUT
	@Path("/promote")
	@Produces(MediaType.APPLICATION_JSON + CustomHeader.CHARSET_UTF8)
	public Response promoteToAdmin(@QueryParam ("username") String username,
			@Context HttpServletRequest request) {
		if(username == null)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return promoteToAdminRetry(username, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response promoteToAdminRetry(String username, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_PROMOTE_TO_ADMIN + username);

		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			Entity user = datastore.get(userKey);

			Entity admin = new Entity(userKey);
			admin.setUnindexedProperty(DSUtils.ADMIN_CREATIONTIME, new Date());
			admin.setUnindexedProperty(DSUtils.ADMIN_WASPROMOTED, true);
			admin.setUnindexedProperty(DSUtils.ADMIN_OLDLEVEL, user.getProperty(DSUtils.USER_LEVEL));

			user.setProperty(DSUtils.USER_LEVEL, UserLevel.ADMIN);

			Key promoterKey = getMoterKey(request);

			if(promoterKey == null)
				return Response.status(Status.EXPECTATION_FAILED).entity(Message.MOTER_NOT_ADMIN).build();

			Entity adminLog = new Entity(DSUtils.ADMINLOG, promoterKey);
			adminLog.setProperty(DSUtils.ADMINLOG_PROMOTED, username);
			adminLog.setUnindexedProperty(DSUtils.ADMINLOG_TIME, new Date());

			List<Entity> list = Arrays.asList(admin, adminLog, user);

			datastore.put(txn, list);
			LOG.info(Message.ADMIN_PROMOTED + username);
			txn.commit();
			return Response.ok().build();
		} catch (EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.USER_NOT_FOUND).build();
		} finally {
			if (txn.isActive() ) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@PUT
	@Path("/demote")
	@Produces(MediaType.APPLICATION_JSON + CustomHeader.CHARSET_UTF8)
	public Response demoteFromAdmin(@QueryParam ("username") String username,
			@Context HttpServletRequest request) {
		int retries = 5;
		while(true) {
			try {
				return demoteFromAdminRetry(username, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response demoteFromAdminRetry(String username, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_DEMOTE_FROM_ADMIN + username);

		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);
		try {
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			Entity user = datastore.get(userKey);
			Query adminQuery = new Query(DSUtils.ADMIN).setAncestor(userKey);
			List<Entity> results = datastore.prepare(adminQuery).asList(FetchOptions.Builder.withDefaults());
			Entity admin = null;
			if (results.isEmpty()) {
				return Response.status(Status.EXPECTATION_FAILED).build();
			} else {
				admin = results.get(0);
			}
			
			user.setProperty(DSUtils.USER_LEVEL, admin.getProperty(DSUtils.ADMIN_OLDLEVEL));

			datastore.delete(txn, admin.getKey());

			Key demoterKey = getMoterKey(request);

			if(demoterKey == null)
				return Response.status(Status.EXPECTATION_FAILED).entity(Message.MOTER_NOT_ADMIN).build();

			Entity adminLog = new Entity(DSUtils.ADMINLOG, demoterKey);
			adminLog.setProperty(DSUtils.ADMINLOG_DEMOTED, username);
			adminLog.setUnindexedProperty(DSUtils.ADMINLOG_TIME, new Date());

			List<Entity> list = Arrays.asList(adminLog, user);

			datastore.put(txn, list);
			LOG.info(Message.ADMIN_PROMOTED + username);
			txn.commit();
			return Response.ok().build();
			
		} catch (EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.USER_NOT_ADMIN).build();
		} finally {
			if (txn.isActive() ) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	private Key getMoterKey(HttpServletRequest request) {
		String username = request.getAttribute(CustomHeader.USERNAME).toString();
		Key promoterKey = KeyFactory.createKey(DSUtils.USER, username);

		Query promoterQuery = new Query(DSUtils.ADMIN).setAncestor(promoterKey);
		List<Entity> results = datastore.prepare(promoterQuery).asList(FetchOptions.Builder.withDefaults());
		Entity promoter = null;
		if (results.isEmpty()) {
			return null;
		} else {
			promoter = results.get(0);
			return promoter.getKey();
		}
	}
}
