package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.UserRegisterData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/admin")
public class Admin {

	private static final Logger LOG = Logger.getLogger(Admin.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH_SIZE = 10;

	public Admin() {}

	@POST
	@Path("/register")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
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
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.username);
			datastore.get(userKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {

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

			Date date = new Date();
			Entity user = new Entity(DSUtils.USER, registerData.username);
			Key userKey = user.getKey();
			Entity admin = new Entity(DSUtils.ADMIN, userKey);

			admin.setUnindexedProperty(DSUtils.ADMIN_CREATIONTIME, date);
			user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.password));
			user.setProperty(DSUtils.USER_EMAIL, registerData.email);
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

	@POST
	@Path("/promote/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response promoteToAdmin(@PathParam(ParamName.USERNAME) String username,
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

			Key promoterKey = null;
			try {
				promoterKey = getMoterKey(request);
			} catch(InternalServerErrorException e) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

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

	@POST
	@Path("/demote/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response demoteFromAdmin(@PathParam(ParamName.USERNAME) String username,
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
			Entity admin = null;
			try {
				admin = datastore.prepare(adminQuery).asSingleEntity();
			} catch(TooManyResultsException e) {
				Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			user.setProperty(DSUtils.USER_LEVEL, admin.getProperty(DSUtils.ADMIN_OLDLEVEL));

			datastore.delete(txn, admin.getKey());

			Key demoterKey = null;
			try {
				demoterKey = getMoterKey(request);
			} catch(InternalServerErrorException e) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

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
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
		Key promoterKey = KeyFactory.createKey(DSUtils.USER, username);

		Query promoterQuery = new Query(DSUtils.ADMIN).setAncestor(promoterKey);
		Entity promoter = null;
		try {
			promoter = datastore.prepare(promoterQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			throw new InternalServerErrorException();
		}

		return promoter.getKey();
	}

	@GET
	@Path("/userlist")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getAllUsers(@QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				return getAllUsersRetry(cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response getAllUsersRetry(String cursor) {
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query query = new Query(DSUtils.USER);

		query.addProjection(new PropertyProjection(DSUtils.USER_EMAIL, String.class))
		.addProjection(new PropertyProjection(DSUtils.USER_LEVEL, String.class));

		QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

		if(list.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		JSONArray array = new JSONArray();

		for(Entity user : list) {

			Query queryPoints = new Query(DSUtils.USERPOINTS).setAncestor(user.getKey());

			Entity points;

			try {
				points = datastore.prepare(queryPoints).asSingleEntity();
			} catch(TooManyResultsException e) {
				continue;
			}

			JSONObject us = new JSONObject();
			us.put(DSUtils.USER, user.getKey().getName());
			us.put(DSUtils.USER_EMAIL, user.getProperty(DSUtils.USER_EMAIL));
			us.put(DSUtils.USER_LEVEL, user.getProperty(DSUtils.USER_LEVEL));
			us.put(DSUtils.USERPOINTS_POINTS, points.getProperty(DSUtils.USERPOINTS_POINTS));
			array.put(us);
		}
		
		cursor = list.getCursor().toWebSafeString();
		
		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}

	@GET
	@Path("/adminlist")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getAllAdmins(@QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				return getAllAdminsRetry(cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response getAllAdminsRetry(String cursor) {
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		
		Filter adminFilter = new Query.FilterPredicate(DSUtils.USER_LEVEL,
				FilterOperator.EQUAL, UserLevel.ADMIN);

		Query query = new Query(DSUtils.USER).setFilter(adminFilter);

		query.addProjection(new PropertyProjection(DSUtils.USER_EMAIL, String.class));

		QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

		if(list.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		JSONArray array = new JSONArray();

		for(Entity user : list) {
			JSONObject us = new JSONObject();
			us.put(DSUtils.ADMIN, user.getKey().getName());
			us.put(DSUtils.USER_EMAIL, user.getProperty(DSUtils.USER_EMAIL));
			array.put(us);
		}
		
		cursor = list.getCursor().toWebSafeString();
		
		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}
	
	@GET
	@Path("/standbyreports")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getAllStandbys(@QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				return getAllStandbysRetry(cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response getAllStandbysRetry(String cursor) {
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		
		Filter adminFilter = new Query.FilterPredicate(DSUtils.REPORT_STATUS,
				FilterOperator.EQUAL, Report.STANDBY);

		Query query = new Query(DSUtils.REPORT).setFilter(adminFilter);

		query.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USERNAME, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class));

		QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

		if(list.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		JSONArray array = new JSONArray();

		for(Entity report : list) {
			JSONObject rep = new JSONObject();
			rep.put(DSUtils.REPORT, report.getKey().getName());
			rep.put(DSUtils.REPORT_TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			rep.put(DSUtils.REPORT_ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
			rep.put(DSUtils.REPORT_GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
			rep.put(DSUtils.REPORT_USERNAME, report.getProperty(DSUtils.REPORT_USERNAME));
			rep.put(DSUtils.REPORT_LAT, report.getProperty(DSUtils.REPORT_LAT));
			rep.put(DSUtils.REPORT_LNG, report.getProperty(DSUtils.REPORT_LNG));
			rep.put(DSUtils.REPORT_STATUS, report.getProperty(DSUtils.REPORT_STATUS));
			rep.put(DSUtils.REPORT_CREATIONTIMEFORMATTED,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
			
			array.put(rep);
		}
		
		cursor = list.getCursor().toWebSafeString();
		
		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}
	
	@POST
	@Path("/confirmorg/{nif}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response confirmOrg(@PathParam(ParamName.NIF) String org) {
		int retries = 5;

		while(true) {
			try {
				Key orgkey = KeyFactory.createKey(DSUtils.ORG, org);
				
				try {
					Entity orgE = datastore.get(orgkey);
					
					orgE.setProperty(DSUtils.ORG_CONFIRMED, CustomHeader.TRUE);
					
					return Response.ok().build();
				} catch (EntityNotFoundException e) {
					LOG.info(Message.ORG_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}
}
