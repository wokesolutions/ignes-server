package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
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
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
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
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.Storage.StoragePath;
import com.wokesolutions.ignes.util.Log;
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
			return Response.status(Status.BAD_REQUEST).entity(Log.REGISTER_DATA_INVALID).build();

		int retries = 5;

		while(true) {
			try {
				return registerAdminRetry(registerData);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response registerAdminRetry(UserRegisterData registerData) {
		LOG.info(Log.ATTEMPT_REGISTER_ADMIN + registerData);

		Transaction txn = datastore.beginTransaction();
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.username);
			datastore.get(userKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Log.USER_ALREADY_EXISTS).build(); 
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
				return Response.status(Status.CONFLICT).entity(Log.EMAIL_ALREADY_IN_USE).build();

			Date date = new Date();
			Entity user = new Entity(DSUtils.USER, registerData.username);
			Key userKey = user.getKey();
			Entity admin = new Entity(DSUtils.ADMIN, userKey);

			admin.setUnindexedProperty(DSUtils.ADMIN_CREATIONTIME, date);
			user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.password));
			user.setProperty(DSUtils.USER_EMAIL, registerData.email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.ADMIN);
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);

			SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			sdf.setTimeZone(TimeZone.getTimeZone(Report.PORTUGAL));

			user.setProperty(DSUtils.USER_CREATIONTIMEFORMATTED, sdf.format(date));

			Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

			Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
			userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

			Entity userStats = new Entity(DSUtils.USERSTATS, userKey);
			userStats.setUnindexedProperty(DSUtils.USERSTATS_LOGINS, 0L);
			userStats.setUnindexedProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
			userStats.setUnindexedProperty(DSUtils.USERSTATS_LOGOUTS, 0L);

			List<Entity> list = Arrays.asList(user, admin, useroptional, userPoints);

			datastore.put(txn, list);
			LOG.info(Log.ADMIN_REGISTERED + registerData);
			txn.commit();
			return Response.ok().build();
		} finally {
			if(txn.isActive()) {
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
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response promoteToAdminRetry(String username, HttpServletRequest request) {
		LOG.info(Log.ATTEMPT_PROMOTE_TO_ADMIN + username);

		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			Entity user = datastore.get(userKey);

			Date date = new Date();

			Entity admin = new Entity(DSUtils.ADMIN, userKey);
			admin.setUnindexedProperty(DSUtils.ADMIN_CREATIONTIME, date);
			admin.setUnindexedProperty(DSUtils.ADMIN_OLDLEVEL, user.getProperty(DSUtils.USER_LEVEL));

			user.setProperty(DSUtils.USER_LEVEL, UserLevel.ADMIN);

			String promoterUsername = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
			Key promoterKey = KeyFactory.createKey(DSUtils.ADMIN, promoterUsername);

			Entity adminLog = new Entity(DSUtils.ADMINLOG);
			adminLog.setProperty(DSUtils.ADMINLOG_ADMIN, promoterKey);
			adminLog.setProperty(DSUtils.ADMINLOG_PROMOTED, username);
			adminLog.setUnindexedProperty(DSUtils.ADMINLOG_TIME, date);

			List<Entity> list = Arrays.asList(admin, adminLog, user);

			datastore.put(txn, list);
			LOG.info(Log.ADMIN_PROMOTED + username);
			txn.commit();
			return Response.ok().build();
		} catch (EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).entity(Log.USER_NOT_FOUND).build();
		} finally {
			if(txn.isActive()) {
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
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response demoteFromAdminRetry(String username, HttpServletRequest request) {
		LOG.info(Log.ATTEMPT_DEMOTE_FROM_ADMIN + username);

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
				txn.rollback();
				LOG.info(Log.USER_NOT_ADMIN);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(admin == null) {
				txn.rollback();
				LOG.info(Log.USER_NOT_ADMIN);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(admin.hasProperty(DSUtils.ADMIN_OLDLEVEL))
				user.setProperty(DSUtils.USER_LEVEL, admin.getProperty(DSUtils.ADMIN_OLDLEVEL));
			else
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1);

			datastore.delete(txn, admin.getKey());

			String demoterUsername = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
			Key demoterKey = KeyFactory.createKey(DSUtils.ADMIN, demoterUsername);

			Entity adminLog = new Entity(DSUtils.ADMINLOG, demoterKey);
			adminLog.setProperty(DSUtils.ADMINLOG_DEMOTED, username);
			adminLog.setUnindexedProperty(DSUtils.ADMINLOG_TIME, new Date());

			List<Entity> list = Arrays.asList(adminLog, user);

			datastore.put(txn, list);
			LOG.info(Log.ADMIN_DEMOTED + username);
			txn.commit();
			return Response.ok().build();
		} catch (EntityNotFoundException e) {
			txn.rollback();
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.EXPECTATION_FAILED).build();
		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Log.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
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
					LOG.warning(Log.TOO_MANY_RETRIES);
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

			LOG.info(user.getKey().getName());

			String level = user.getProperty(DSUtils.USER_LEVEL).toString();

			JSONObject us = new JSONObject();
			us.put(Prop.USERNAME, user.getKey().getName());
			us.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
			us.put(Prop.LEVEL, level);
			us.put(Prop.CREATIONTIME, user.getProperty(DSUtils.USER_CREATIONTIMEFORMATTED));

			if(!level.equals(UserLevel.ORG) && !level.equals(UserLevel.WORKER)) {
				Key pointsK = KeyFactory.createKey(user.getKey(), DSUtils.USERPOINTS, user.getKey().getName());

				Entity points;

				try {
					points = datastore.get(pointsK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.UNEXPECTED_ERROR + user.getKey().getName());
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				us.put(Prop.POINTS, points.getProperty(DSUtils.USERPOINTS_POINTS));
			}

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
					LOG.warning(Log.TOO_MANY_RETRIES);
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
			us.put(Prop.USERNAME, user.getKey().getName());
			us.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
			us.put(Prop.CREATIONTIME, user.getProperty(DSUtils.USER_CREATIONTIMEFORMATTED));
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
					LOG.warning(Log.TOO_MANY_RETRIES);
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
		.addProjection(new PropertyProjection(DSUtils.REPORT_USER, Key.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

		if(list.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		JSONArray array = new JSONArray();

		for(Entity report : list) {
			JSONObject rep = new JSONObject();
			rep.put(Prop.REPORT, report.getKey().getName());
			rep.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			rep.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
			rep.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
			rep.put(Prop.USERNAME,
					((Key) report.getProperty(DSUtils.REPORT_USER)).getName());
			rep.put(Prop.LAT, report.getProperty(DSUtils.REPORT_LAT));
			rep.put(Prop.LNG, report.getProperty(DSUtils.REPORT_LNG));
			rep.put(Prop.CREATIONTIME,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));

			array.put(rep);
		}

		cursor = list.getCursor().toWebSafeString();

		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}

	@POST
	@Path("/confirmreport/{report}")
	public Response confirmReport(@PathParam(ParamName.REPORT) String reportid,
			@Context HttpServletRequest request) {
		int retries = 5;

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Key userK = KeyFactory.createKey(DSUtils.USER, username);
		Key adminK = KeyFactory.createKey(userK, DSUtils.ADMIN, username);

		while(true) {
			try {
				Key reportK = KeyFactory.createKey(DSUtils.REPORT, reportid);

				Entity report;
				try {
					report = datastore.get(reportK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				if(!report.getProperty(DSUtils.REPORT_STATUS).equals(Report.STANDBY)) {
					LOG.info(Log.REPORT_STANDBY);
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				report.setProperty(DSUtils.REPORT_STATUS, Report.OPEN);

				Entity statuslog = new Entity(DSUtils.REPORTSTATUSLOG, report.getKey());
				statuslog.setProperty(DSUtils.REPORTSTATUSLOG_NEWSTATUS, Report.OPEN);
				statuslog.setProperty(DSUtils.REPORTSTATUSLOG_OLDSTATUS, Report.STANDBY);
				statuslog.setProperty(DSUtils.REPORTSTATUSLOG_TIME, new Date());
				statuslog.setProperty(DSUtils.REPORTSTATUSLOG_USER, adminK);

				Transaction txn = datastore.beginTransaction();

				datastore.put(txn, report);
				datastore.put(txn, statuslog);

				txn.commit();

				return Response.ok().build();
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	@POST
	@Path("/confirmorg/{nif}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response confirmOrg(@PathParam(ParamName.NIF) String org) {
		int retries = 5;

		while(true) {
			try {
				Key orgkey = KeyFactory.createKey(DSUtils.USER, org);

				try {
					Entity orgE = datastore.get(orgkey);

					orgE.setProperty(DSUtils.USER_ACTIVATION, Profile.ACTIVATED);

					datastore.put(orgE);

					Email.sendOrgConfirmedMessage(
							orgE.getProperty(DSUtils.USER_EMAIL).toString());

					return Response.ok().build();
				} catch (EntityNotFoundException e) {
					LOG.info(Log.ORG_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	@GET
	@Path("/orgstoconfirm")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response orgsToConfirm(@QueryParam(CustomHeader.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				Filter activationFilter = new Query.FilterPredicate(DSUtils.USER_ACTIVATION,
						FilterOperator.EQUAL, Profile.NOT_ACTIVATED);

				Filter orgFilter = new Query.FilterPredicate(DSUtils.USER_LEVEL,
						FilterOperator.EQUAL, UserLevel.ORG);

				List<Filter> filters = Arrays.asList(activationFilter, orgFilter);

				CompositeFilter filter = new Query
						.CompositeFilter(CompositeFilterOperator.AND, filters);

				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				Query query = new Query(DSUtils.USER).setFilter(filter);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				QueryResultList<Entity> list = datastore.prepare(query)
						.asQueryResultList(fetchOptions);

				JSONArray array = new JSONArray();

				for(Entity user : list) {
					Query orgQuery = new Query(DSUtils.ORG).setAncestor(user.getKey());
					Entity org;

					try {
						org = datastore.prepare(orgQuery).asSingleEntity();
					} catch(TooManyResultsException e) {
						LOG.info(Log.UNEXPECTED_ERROR);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					JSONObject obj = new JSONObject();
					obj.put(Prop.NIF, org.getParent().getName());
					obj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
					obj.put(Prop.ADDRESS, org.getProperty(DSUtils.ORG_ADDRESS));
					obj.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
					obj.put(Prop.ISFIRESTATION, org.getProperty(DSUtils.ORG_PRIVATE));
					obj.put(Prop.LOCALITY, org.getProperty(DSUtils.ORG_LOCALITY));
					obj.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
					obj.put(Prop.SERVICES, org.getProperty(DSUtils.ORG_CATEGORIES));
					obj.put(Prop.ZIP, org.getProperty(DSUtils.ORG_ZIP));
					obj.put(Prop.CREATIONTIME,
							user.getProperty(DSUtils.USER_CREATIONTIMEFORMATTED));

					array.put(obj);
				}

				if(array.length() < BATCH_SIZE)
					return Response.ok(array.toString()).build();

				cursor = list.getCursor().toWebSafeString();

				return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	@DELETE
	@Path("/delete/user/{username}")
	public Response deleteUser(@PathParam(ParamName.USERNAME) String username) {
		int retries = 5;

		while(true) {
			try {
				Entity user;
				try {
					user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));
				} catch(EntityNotFoundException e) {
					LOG.info(Log.USER_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				String level = user.getProperty(DSUtils.USER_LEVEL).toString();

				Key userK = user.getKey();

				if(level.equals(UserLevel.LEVEL1)) {

					Transaction txn = datastore
							.beginTransaction(TransactionOptions.Builder.withXG(true));
					try {
						if(deleteUserDef(userK, txn)) {
							Entity adminLog = new Entity(DSUtils.ADMINLOG);
							adminLog.setProperty(DSUtils.ADMINLOG_DELETED, userK);
							adminLog.setProperty(DSUtils.ADMINLOG_TIME, new Date());

							datastore.put(txn, adminLog);

							LinkedList<String> list = new LinkedList<String>();
							list.add(Storage.IMG_FOLDER);
							list.add(Storage.PROFILE_FOLDER);
							StoragePath path = new StoragePath(list, username);
							if(Storage.deleteImage(path, false))

								txn.commit();
							return Response.ok().build();
						}
					} finally {
						if(txn.isActive()) {
							txn.rollback();
							return Response.status(Status.INTERNAL_SERVER_ERROR).build();
						}
					}
				} else {

				}
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private boolean deleteUserDef(Key userK, Transaction txn) {
		Query statQ = new Query(DSUtils.USERSTATS).setAncestor(userK).setKeysOnly();

		Entity stat;
		try {
			stat = datastore.prepare(statQ).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return false;
		}

		if(stat == null) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return false;
		}

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query reportQ = new Query(DSUtils.REPORT).setKeysOnly();
		Filter reportF = new Query.FilterPredicate(DSUtils.REPORT_USER,
				FilterOperator.EQUAL, userK);
		reportQ.setFilter(reportF);

		List<Entity> reports = datastore.prepare(reportQ).asList(fetchOptions);
		List<Key> reportsK = new ArrayList<Key>(reports.size());
		for(Entity report : reports)
			reportsK.add(report.getKey());

		Query commentQ = new Query(DSUtils.REPORTCOMMENT).setKeysOnly();
		Filter commentF = new Query.FilterPredicate(DSUtils.REPORTCOMMENT_USER,
				FilterOperator.EQUAL, userK);
		commentQ.setFilter(commentF);

		List<Entity> comments = datastore.prepare(commentQ).asList(fetchOptions);
		List<Key> commentsK = new ArrayList<Key>(comments.size());
		for(Entity comment : comments)
			commentsK.add(comment.getKey());

		datastore.delete(txn, stat.getKey());
		datastore.delete(txn, reportsK);
		datastore.delete(txn, commentsK);
		return true;
	}

	@GET
	@Path("/publicreports")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getPublicReps(@QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;
		while(true) {
			try {
				Filter reportF = new Query.FilterPredicate(DSUtils.REPORT_PRIVATE,
						FilterOperator.EQUAL, false);
				Query reportQ = new Query(DSUtils.REPORT).setFilter(reportF);


				reportQ.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_USER, Key.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
				.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class));

				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				QueryResultList<Entity> reports = datastore.prepare(reportQ)
						.asQueryResultList(fetchOptions);

				if(reports.isEmpty())
					return Response.status(Status.NO_CONTENT).build();

				JSONArray array = new JSONArray();

				for(Entity report : reports) {
					JSONObject rep = new JSONObject();
					rep.put(Prop.REPORT, report.getKey().getName());
					rep.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
					rep.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
					rep.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
					rep.put(Prop.STATUS, report.getProperty(DSUtils.REPORT_STATUS));
					rep.put(Prop.USERNAME,
							((Key) report.getProperty(DSUtils.REPORT_USER)).getName());

					Object points = report.getProperty(DSUtils.REPORT_POINTS);
					if(points != null) {
						rep.put(Prop.POINTS, points);
					}

					rep.put(Prop.LAT, report.getProperty(DSUtils.REPORT_LAT));
					rep.put(Prop.LNG, report.getProperty(DSUtils.REPORT_LNG));
					rep.put(Prop.CREATIONTIME,
							report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
					
					Key reportK = report.getKey();
					
					Key orgtaskK = KeyFactory.createKey(reportK, DSUtils.ORGTASK, reportK.getName());
					
					LOG.info(orgtaskK.toString());
					
					Entity orgtask;
					try {
						orgtask = datastore.get(orgtaskK);
						
						JSONObject orgObj = new JSONObject();
						
						Key orgK = (Key) orgtask.getProperty(DSUtils.ORGTASK_ORG);
						Entity org;
						try {
							org = datastore.get(orgK);
						} catch(EntityNotFoundException e) {
							LOG.info(Log.ORG_NOT_FOUND);
							continue;
						}
						
						orgObj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
						orgObj.put(Prop.NIF, orgK.getParent().getName());
						
						rep.put(Prop.ORG, orgObj);
					} catch(EntityNotFoundException e) {
						if(report.getProperty(DSUtils.REPORT_STATUS).equals(Report.OPEN)) {
							Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATION_REPORT,
									FilterOperator.EQUAL, report.getKey());
							Query applicationQ = new Query(DSUtils.APPLICATION).setFilter(applicationF);
							
							FetchOptions defaultOptions = FetchOptions.Builder.withDefaults();
							List<Entity> applications = datastore.prepare(applicationQ).asList(defaultOptions);
							
							JSONArray apps = new JSONArray();
							
							for(Entity application : applications) {
								JSONObject app = new JSONObject();

								Key orgK = application.getParent();
								Entity org;
								
								Entity user;
								try {
									org = datastore.get(orgK);
									user = datastore.get(org.getParent());
								} catch(EntityNotFoundException e2) {
									LOG.info(Log.ORG_NOT_FOUND);
									continue;
								}
								
								app.put(Prop.BUDGET, application.getProperty(DSUtils.APPLICATION_BUDGET));
								app.put(Prop.INFO, application.getProperty(DSUtils.APPLICATION_INFO));
								app.put(Prop.APPLICATION_TIME, application.getProperty(DSUtils.APPLICATION_TIME));
								
								app.put(Prop.NIF, orgK.getName());
								app.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
								app.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
								app.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
								
								apps.put(app);
							}
							
							if(apps.length() > 0)
								rep.put(Prop.APPLICATIONS, apps);
						}
					}

					array.put(rep);
				}

				cursor = reports.getCursor().toWebSafeString();

				if(array.length() < BATCH_SIZE)
					return Response.ok(array.toString()).build();

				return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();

			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	// ---------------x--------------- SUBCLASS

	private static final String ORGNAME = "Organização";
	private static final String COUNT = "Número de candidaturas";

	@GET
	@Path("/stats/org/applications")
	public Response statsOrgApplications() {
		int retries = 5;

		while(true) {
			try {
				JSONArray array = new JSONArray();
				JSONArray arraytitle = new JSONArray();
				arraytitle.put(ORGNAME);
				arraytitle.put(COUNT);
				array.put(arraytitle);

				FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

				Query orgQ = new Query(DSUtils.ORG)
						.addProjection(new PropertyProjection(DSUtils.ORG_NAME, String.class));
				List<Entity> orgs = datastore.prepare(orgQ).asList(fetchOptions);

				for(Entity org : orgs) {
					JSONArray arrayinner = new JSONArray();

					Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATIONLOG_ORG,
							FilterOperator.EQUAL, org.getKey());
					Query applicationQ = new Query(DSUtils.APPLICATIONLOG).setFilter(applicationF);

					int count = datastore.prepare(applicationQ)
							.countEntities(FetchOptions.Builder.withDefaults());

					arrayinner.put(org.getKey().getName() + " - " +
							org.getProperty(DSUtils.ORG_NAME).toString());

					arrayinner.put(count);

					array.put(arrayinner);
				}

				return Response.ok(array.toString()).build();
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}
}
