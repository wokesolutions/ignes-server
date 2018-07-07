package com.wokesolutions.ignes.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.digest.DigestUtils;
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
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.AcceptData;
import com.wokesolutions.ignes.data.PasswordData;
import com.wokesolutions.ignes.data.ProfPicData;
import com.wokesolutions.ignes.data.UserOptionalData;
import com.wokesolutions.ignes.exceptions.NotSameNorAdminException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.UserLevel;
import com.wokesolutions.ignes.util.Storage.StoragePath;

@Path("/profile")
public class Profile {

	private static final Logger LOG = Logger.getLogger(Profile.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final int BATCH_SIZE = 20;

	public static final String ACTIVATED = "activated";
	public static final String NOT_ACTIVATED = "notactivated";

	@POST
	@Path("/update/{username}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response updateProfile(@PathParam (ParamName.USERNAME) String username,
			UserOptionalData data,
			@Context HttpServletRequest request) {
		if(!data.isValid() || username == null || username.equals("")) {
			LOG.info(Log.PROFILE_UPDATE_DATA_INVALID);
			return Response.status(Status.BAD_REQUEST)
					.entity(Log.PROFILE_UPDATE_DATA_INVALID).build();
		}

		int retries = 5;
		while(true) {
			try {
				return updateProfileRetry(username, data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response updateProfileRetry(String username, UserOptionalData data, HttpServletRequest request) {
		LOG.info(Log.ATTEMPT_UPDATE_PROFILE + username);

		Transaction txn = datastore.beginTransaction();
		try {
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			datastore.get(userKey);

			String requester;

			try {
				requester = sameUserOrAdmin(request, username);
			} catch(NotSameNorAdminException e2) {
				txn.rollback();
				LOG.info(Log.REQUESTER_IS_NOT_USER_OR_ADMIN);
				return Response.status(Status.FORBIDDEN).build();
			}

			Key altererKey = KeyFactory.createKey(DSUtils.USER, requester);

			Query useroptionalQuery = new Query(DSUtils.USEROPTIONAL).setAncestor(userKey);

			Entity useroptional;

			try {
				useroptional = datastore.prepare(useroptionalQuery).asSingleEntity();
			} catch(TooManyResultsException e3) {
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(useroptional == null) {
				txn.rollback();
				LOG.info(Log.NO_OPTIONAL_USER_ENTITY_FOUND);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			Entity useroptionallog = new Entity(DSUtils.USEROPTIONALLOGS, altererKey);
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_CHANGETIME, new Date());
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_USERNAME, username);

			fillOptional(username, data, useroptional, useroptionallog);

			List<Entity> list = Arrays.asList(useroptional, useroptionallog);

			datastore.put(txn, list);
			LOG.info(Log.PROFILE_UPDATED);
			txn.commit();
			return Response.ok().build();

		} catch(EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).entity(Log.USER_NOT_FOUND).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Log.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	private void fillOptional(String username, UserOptionalData data, Entity useroptional,Entity useroptionallog) {
		if(data.address != null && !data.address.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_ADDRESS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDADDRESS,
						useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWADDRESS, data.address);
			useroptional.setProperty(DSUtils.USEROPTIONAL_ADDRESS, data.address);
		}
		if(data.birth != null && !data.birth.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_BIRTH))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDBIRTH,
						useroptional.getProperty(DSUtils.USEROPTIONAL_BIRTH));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWBIRTH, data.birth);
			useroptional.setProperty(DSUtils.USEROPTIONAL_BIRTH, data.birth);
		}
		if(data.gender != null && !data.gender.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_GENDER))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDGENDER,
						useroptional.getProperty(DSUtils.USEROPTIONAL_GENDER));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWGENDER, data.gender);
			useroptional.setProperty(DSUtils.USEROPTIONAL_GENDER, data.gender);
		}
		if(data.job != null && !data.job.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_JOB))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDJOB,
						useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWJOB, data.job);
			useroptional.setProperty(DSUtils.USEROPTIONAL_JOB, data.job);
		}
		if(data.locality != null && !data.locality.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_LOCALITY))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDLOCALITY,
						useroptional.getProperty(DSUtils.USEROPTIONAL_LOCALITY));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWLOCALITY, data.locality);
			useroptional.setProperty(DSUtils.USEROPTIONAL_LOCALITY, data.locality);
		}
		if(data.name != null && !data.name.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_NAME))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDNAME,
						useroptional.getProperty(DSUtils.USEROPTIONAL_NAME));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWNAME, data.name);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_NAME, data.name);
		}
		if(data.phone != null && !data.phone.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_PHONE))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDPHONE,
						useroptional.getProperty(DSUtils.USEROPTIONAL_PHONE));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWPHONE, data.phone);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_PHONE, data.phone);
		}
		if(data.skills != null && !data.skills.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_SKILLS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDSKILLS,
						useroptional.getProperty(DSUtils.USEROPTIONAL_SKILLS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWSKILLS, data.skills);
			useroptional.setProperty(DSUtils.USEROPTIONAL_SKILLS, data.skills);
		}
		if(data.zip != null && !data.zip.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_ZIP))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDZIP,
						useroptional.getProperty(DSUtils.USEROPTIONAL_ZIP));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWZIP, data.zip);
			useroptional.setProperty(DSUtils.USEROPTIONAL_ZIP, data.zip);
		}
	}

	@GET
	@Path("/votes/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getVotes(@PathParam (ParamName.USERNAME) String username,
			@Context HttpServletRequest request,
			@Context HttpHeaders headers, @QueryParam(ParamName.CURSOR) String cursor) {
		if(username == null || username.equals(""))
			return Response.status(Status.BAD_REQUEST).entity(Log.PROFILE_UPDATE_DATA_INVALID).build();

		int retries = 5;

		while(true) {
			try {
				return getVotesRetry(username, request, headers, cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response getVotesRetry(String username,
			HttpServletRequest request, HttpHeaders headers, String cursor) {

		LOG.info(Log.GIVING_VOTES + username);

		try {
			sameUserOrAdmin(request, username);
		} catch(Exception e) {
			LOG.info(Log.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key userK = KeyFactory.createKey(DSUtils.USER, username);

		Query query = new Query(DSUtils.USERVOTE);
		Filter filter = new Query.FilterPredicate(DSUtils.USERVOTE_USER,
				FilterOperator.EQUAL, userK);
		query.setFilter(filter);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(20);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		QueryResultList<Entity> allVotes = datastore.prepare(query).asQueryResultList(fetchOptions);

		JSONArray jsonarray = new JSONArray();

		for(Entity vote: allVotes) {
			JSONObject voteJson = new JSONObject();
			Map<String, Object> props = vote.getProperties();

			voteJson.put(Prop.VOTE, props.get(DSUtils.USERVOTE_TYPE));

			if(props.containsKey(DSUtils.USERVOTE_REPORT))
				voteJson.put(Prop.REPORT, ((Key) props.get(DSUtils.USERVOTE_REPORT)).getName());
			else if(props.containsKey(DSUtils.USERVOTE_EVENT))
				voteJson.put(Prop.EVENT, props.get(DSUtils.USERVOTE_EVENT));
			else if(props.containsKey(DSUtils.USERVOTE_COMMENT))
				voteJson.put(Prop.COMMENT, props.get(DSUtils.USERVOTE_COMMENT));
			else {
				LOG.info(Log.UNEXPECTED_ERROR + " " + vote.getKey().getId());
				continue;
			}

			jsonarray.put(voteJson);
		}

		if(jsonarray.length() < BATCH_SIZE)
			return Response.ok()
					.entity(jsonarray.toString()).build();

		return Response.ok()
				.header(CustomHeader.CURSOR, allVotes.getCursor().toWebSafeString())
				.entity(jsonarray.toString()).build();
	}

	@POST
	@Path("/activate/{username}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response activateAccount(String code, @Context HttpServletRequest request,
			@PathParam ("username") String username) {
		int retries = 5;

		while(true) {
			try {
				return activateAccountRetry(code, username, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response activateAccountRetry(String code, String username, HttpServletRequest request) {
		try {
			sameUserOrAdmin(request, username);
		} catch(Exception e2) {
			LOG.info(Log.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		JSONObject codejson = new JSONObject(code);
		code = codejson.getString("code");
		Key userkey = KeyFactory.createKey(DSUtils.USER, username);
		Entity user;
		try {
			user = datastore.get(userkey);
		} catch (EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		boolean bool = code.equals(user.getProperty(DSUtils.USER_ACTIVATION));
		if(!bool)
			return Response.status(Status.EXPECTATION_FAILED).build();

		user.setProperty(DSUtils.USER_ACTIVATION, ACTIVATED);

		datastore.put(user);

		return Response.ok().build();
	}

	@GET
	@Path("/activated/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response isActivated(@PathParam(ParamName.USERNAME) String username,
			@Context HttpServletRequest request) {
		int retries = 5;

		try {
			sameUserOrAdmin(request, username);
		} catch(Exception e2) {
			LOG.info(Log.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		while(true) {
			try {
				return isActivatedRetry(username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response isActivatedRetry(String user) {
		Entity entUser;

		try {
			entUser = datastore.get(KeyFactory.createKey(DSUtils.USER, user));
		} catch (EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		boolean yes = entUser.getProperty(DSUtils.USER_ACTIVATION).toString().equals(ACTIVATED);

		return Response.ok(yes).build();
	}

	@GET
	@Path("/view/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getUserProfile(@Context HttpServletRequest request,
			@PathParam(ParamName.USERNAME) String username) {
		int retries = 5;

		while(true) {
			try {
				return getUserProfileRetry(username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response getUserProfileRetry(String username) {
		Entity user;

		try {
			user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));
		} catch(EntityNotFoundException e) {
			LOG.info(Log.USER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject object = new JSONObject();

		object.put(Prop.USERNAME, username);
		object.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
		object.put(Prop.LEVEL, user.getProperty(DSUtils.USER_LEVEL));

		Query query = new Query(DSUtils.USEROPTIONAL).setAncestor(user.getKey());

		Entity optionals;
		try {
			optionals = datastore.prepare(query).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_ADDRESS))
			object.put(Prop.ADDRESS, optionals.getProperty(DSUtils.USEROPTIONAL_ADDRESS));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_ZIP))
			object.put(Prop.ZIP, optionals.getProperty(DSUtils.USEROPTIONAL_ZIP));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_JOB))
			object.put(Prop.JOB, optionals.getProperty(DSUtils.USEROPTIONAL_JOB));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_SKILLS))
			object.put(Prop.SKILLS, optionals.getProperty(DSUtils.USEROPTIONAL_SKILLS));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_GENDER))
			object.put(Prop.GENDER, optionals.getProperty(DSUtils.USEROPTIONAL_GENDER));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_NAME))
			object.put(Prop.NAME, optionals.getProperty(DSUtils.USEROPTIONAL_NAME));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_LOCALITY))
			object.put(Prop.LOCALITY, optionals.getProperty(DSUtils.USEROPTIONAL_LOCALITY));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_BIRTH))
			object.put(Prop.BIRTH, optionals.getProperty(DSUtils.USEROPTIONAL_BIRTH));

		if(optionals.hasProperty(DSUtils.USEROPTIONAL_PHONE))
			object.put(Prop.PHONE, optionals.getProperty(DSUtils.USEROPTIONAL_PHONE));

		Query query2 = new Query(DSUtils.USERPOINTS).setAncestor(user.getKey());
		Entity points;
		try {
			points = datastore.prepare(query2).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		object.put(Prop.POINTS, points.getProperty(DSUtils.USERPOINTS_POINTS));

		Query query3 = new Query(DSUtils.REPORT);
		Filter filter = new Query.FilterPredicate(DSUtils.REPORT_USER,
				FilterOperator.EQUAL, user.getKey());
		query3.setFilter(filter);

		int reports = datastore.prepare(query3).asList(FetchOptions.Builder.withDefaults()).size();

		object.put(Prop.REPORTS, reports);

		Object pic = optionals.getProperty(DSUtils.USEROPTIONAL_PICPATH);

		if(pic != null) {
			String picpath = pic.toString();
			String picb64 = Storage.getImage(picpath);
			object.put(Prop.PROFPIC, picb64);
		}

		return Response.ok(object.toString()).build();
	}

	public static String sameUserOrAdmin(HttpServletRequest request, String username)
			throws NotSameNorAdminException, EntityNotFoundException {

		String requesterName = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Key requesterK = KeyFactory.createKey(DSUtils.USER, requesterName);

		Entity requester = datastore.get(requesterK);
		String level = requester.getProperty(DSUtils.USER_LEVEL).toString();

		if(!level.equals(UserLevel.ADMIN) && !requesterName.equals(username))
			throw new NotSameNorAdminException();

		return requesterName;
	}

	@POST
	@Path("/changepassword")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response changePassword(PasswordData data, @Context HttpServletRequest request) {
		int retries = 5;

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				Entity user;
				try {
					user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));
				} catch(EntityNotFoundException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				Transaction txn = datastore.beginTransaction();

				try {
					String newPw = DigestUtils.sha512Hex(data.newpassword);
					String oldPw = DigestUtils.sha512Hex(data.oldpassword);

					if(!user.getProperty(DSUtils.USER_PASSWORD).toString().equals(oldPw)) {
						LOG.info(Log.WRONG_PASSWORD + oldPw);
						txn.rollback();
						return Response.status(Status.FORBIDDEN).build();
					}

					user.setProperty(DSUtils.USER_PASSWORD, newPw);

					Entity pwLog = new Entity(DSUtils.PASSWORDCHANGELOG, user.getKey());
					pwLog.setProperty(DSUtils.PASSWORDCHANGELOG_NEW, newPw);
					pwLog.setProperty(DSUtils.PASSWORDCHANGELOG_OLD, oldPw);
					pwLog.setProperty(DSUtils.PASSWORDCHANGELOG_TIME, new Date());
					pwLog.setProperty(DSUtils.PASSWORDCHANGELOG_IP, request.getRemoteAddr());

					List<Entity> list = Arrays.asList(user, pwLog);

					datastore.put(txn, list);

					txn.commit();
					LOG.info(Log.PASSWORD_CHANGED + username);
					return Response.ok().build();
				} finally {
					if(txn.isActive()) {
						LOG.info(Log.TXN_ACTIVE);
						txn.rollback();
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}
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
	@Path("/reports/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getAllReports(@PathParam(ParamName.USERNAME) String username,
			@QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				return getAllReportsRetry(username, cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response getAllReportsRetry(String username, String cursor) {
		Query reportQuery = new Query(DSUtils.REPORT);

		Filter userFilter = new Query.FilterPredicate(DSUtils.REPORT_USER,
				FilterOperator.EQUAL, KeyFactory.createKey(DSUtils.USER, username));

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		reportQuery.setFilter(userFilter);

		reportQuery
		.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CATEGORY, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_POINTS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LOCALITY, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> reports = datastore.prepare(reportQuery)
				.asQueryResultList(fetchOptions);

		if(reports.isEmpty()) {
			LOG.info(Log.NO_REPORTS_FOUND);
			return Response.status(Status.NO_CONTENT).build();
		}

		JSONArray array = new JSONArray();

		for(Entity report : reports) {
			JSONObject jsonReport = new JSONObject();

			jsonReport.put(Prop.REPORT, report.getKey().getName());
			jsonReport.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			jsonReport.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));

			Object points = report.getProperty(DSUtils.REPORT_POINTS);
			if(points != null) {
				jsonReport.put(Prop.POINTS, new JSONArray(points.toString()));
			}

			jsonReport.put(Prop.CATEGORY, report.getProperty(DSUtils.CATEGORY));
			jsonReport.put(Prop.LAT, report.getProperty(DSUtils.REPORT_LAT));
			jsonReport.put(Prop.LNG, report.getProperty(DSUtils.REPORT_LNG));
			jsonReport.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
			jsonReport.put(Prop.STATUS, report.getProperty(DSUtils.REPORT_STATUS));
			jsonReport.put(Prop.DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
			jsonReport.put(Prop.CREATIONTIME,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
			jsonReport.put(Prop.ISPRIVATE, report.getProperty(DSUtils.REPORT_PRIVATE));

			Report.appendVotesAndComments(jsonReport, report);

			array.put(jsonReport);
		}

		cursor = reports.getCursor().toWebSafeString();

		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}

	@POST
	@Path("/changeprofilepic")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response changeProfPic(@Context HttpServletRequest request, ProfPicData data) {
		int retries = 5;

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				return changeProfPicRetry(data, username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response changeProfPicRetry(ProfPicData data, String username) {
		LinkedList<String> folders = new LinkedList<String>();
		folders.add(Storage.IMG_FOLDER);
		folders.add(Storage.PROFILE_FOLDER);
		StoragePath pathImg = new StoragePath(folders, username);

		LOG.info(Log.ATTEMPT_UPDATE_PROFILE);

		if(!Storage.saveImage(data.pic, pathImg,
				data.width, data.height, data.orientation, false))
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Log.STORAGE_ERROR).build();

		Query query = new Query(DSUtils.USEROPTIONAL)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, username));

		try {
			Entity optional = datastore.prepare(query).asSingleEntity();
			LOG.info(optional.toString());

			optional.setProperty(DSUtils.USEROPTIONAL_PICPATH, pathImg.makePath());
			LOG.info(optional.toString());

			datastore.put(optional);
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		return Response.ok().build();
	}

	@GET
	@Path("/getprofilepic/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getProfPic(@PathParam(ParamName.USERNAME) String username) {
		int retries = 5;
		while(true) {
			try {
				return getProfPicRetry(username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	public Response getProfPicRetry(String username) {
		String img;
		String path;
		Entity optional;

		try {
			optional = datastore.get(KeyFactory.createKey(DSUtils.USEROPTIONAL_PICPATH, username));
		} catch(EntityNotFoundException e) {
			LOG.info(Log.USER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		path = optional.getProperty(DSUtils.USEROPTIONAL_PICPATH).toString();

		if(path == null) {
			LOG.info(Log.USER_HAS_NO_IMAGE);
			return Response.status(Status.NO_CONTENT).build();
		}

		img = Storage.getImage(path);

		JSONObject obj = new JSONObject();
		obj.put(Prop.PROFPIC, img);

		return Response.ok(obj.toString()).build();
	}

	@POST
	@Path("/addfollow/{location}")
	public Response addFollow(@Context HttpServletRequest request,
			@PathParam(ParamName.LOCATION) String location) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				Entity follow = new Entity(DSUtils.FOLLOW, userK);
				follow.setProperty(DSUtils.FOLLOW_LOCATION, location);
				datastore.put(follow);
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

	@GET
	@Path("/follows")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response follows(@Context HttpServletRequest request) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				Key userK = KeyFactory.createKey(DSUtils.USER, username);

				Query followQ = new Query(DSUtils.FOLLOW).setAncestor(userK);
				List<Entity> list = datastore.prepare(followQ)
						.asList(FetchOptions.Builder.withDefaults());

				JSONArray array = new JSONArray();
				for(Entity location : list)
					array.put(location.getProperty(DSUtils.FOLLOW_LOCATION));

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

	@GET
	@Path("/getapplications/{report}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response applications(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String report) {
		int retries = 5;
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				Key reportK = KeyFactory.createKey(DSUtils.REPORT, report);
				Entity reportE;
				try {
					reportE = datastore.get(reportK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				if(!reportE.getProperty(DSUtils.REPORT_USER).equals(userK)) {
					LOG.info(Log.REPORT_IS_PRIVATE);
					return Response.status(Status.FORBIDDEN).build();
				}

				Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATION_REPORT,
						FilterOperator.EQUAL, reportK);
				Query applicationQ = new Query(DSUtils.APPLICATION).setFilter(applicationF);
				List<Entity> applications = datastore.prepare(applicationQ)
						.asList(FetchOptions.Builder.withDefaults());

				JSONArray array = new JSONArray();

				for(Entity application : applications) {
					JSONObject obj = new JSONObject();

					Entity org;
					try {
						org = datastore.get(application.getParent());
					} catch(EntityNotFoundException e) {
						LOG.info(Log.UNEXPECTED_ERROR);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}
					
					Entity user;
					try {
						user = datastore.get(org.getParent());
					} catch(EntityNotFoundException e) {
						LOG.info(Log.USER_NOT_FOUND);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					obj.put(Prop.NIF, org.getKey().getName());
					obj.put(Prop.BUDGET, application.getProperty(DSUtils.APPLICATION_BUDGET));
					obj.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
					obj.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
					obj.put(Prop.INFO, application.getProperty(DSUtils.APPLICATION_INFO));
					obj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
					
					array.put(obj);
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
