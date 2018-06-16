package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import javax.ws.rs.core.HttpHeaders;
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
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.UserOptionalData;
import com.wokesolutions.ignes.exceptions.NotSameNorAdminException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/profile")
public class Profile {

	private static final Logger LOG = Logger.getLogger(Profile.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final int BATCH_SIZE = 20;

	public static final String ACTIVATED = "activated";

	@POST
	@Path("/update/{username}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response updateProfile(@PathParam (ParamName.USERNAME) String username,
			UserOptionalData data,
			@Context HttpServletRequest request) {
		if(!data.isValid() || username == null || username.equals("")) {
			LOG.info(Message.PROFILE_UPDATE_DATA_INVALID);
			return Response.status(Status.BAD_REQUEST).entity(Message.PROFILE_UPDATE_DATA_INVALID).build();
		}

		int retries = 5;
		while(true) {
			try {
				return updateProfileRetry(username, data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response updateProfileRetry(String username, UserOptionalData data, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_UPDATE_PROFILE + username);

		Transaction txn = datastore.beginTransaction();
		try {
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			datastore.get(userKey);

			String requester;

			try {
				requester = sameUserOrAdmin(request, username);
			} catch(NotSameNorAdminException e2) {
				txn.rollback();
				LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
				return Response.status(Status.FORBIDDEN).build();
			}

			Key altererKey = KeyFactory.createKey(DSUtils.USER, requester);

			Query useroptionalQuery = new Query(DSUtils.USEROPTIONAL).setAncestor(userKey);

			Entity useroptional;

			try {
				useroptional = datastore.prepare(useroptionalQuery).asSingleEntity();
			} catch(TooManyResultsException e3) {
				LOG.info(Message.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(useroptional == null) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED)
						.entity(Message.NO_OPTIONAL_USER_ENTITY_FOUND).build();
			}

			Entity useroptionallog = new Entity(DSUtils.USEROPTIONALLOGS, altererKey);
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_CHANGETIME, new Date());
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_USERNAME, username);

			fillOptional(username, data, useroptional, useroptionallog);

			List<Entity> list = Arrays.asList(useroptional, useroptionallog);

			datastore.put(txn, list);
			LOG.info(Message.PROFILE_UPDATED);
			txn.commit();
			return Response.ok().build();

		} catch(EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.USER_NOT_FOUND).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	private void fillOptional(String username, UserOptionalData data, Entity useroptional,Entity useroptionallog) {
		if(data.useroptional_address != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_ADDRESS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDADDRESS, useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWADDRESS, data.useroptional_address);
			useroptional.setProperty(DSUtils.USEROPTIONAL_ADDRESS, data.useroptional_address);
		}
		if(data.useroptional_birth != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_BIRTH))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDBIRTH, useroptional.getProperty(DSUtils.USEROPTIONAL_BIRTH));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWBIRTH, data.useroptional_birth);
			useroptional.setProperty(DSUtils.USEROPTIONAL_BIRTH, data.useroptional_birth);
		}
		if(data.useroptional_gender != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_GENDER))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDGENDER, useroptional.getProperty(DSUtils.USEROPTIONAL_GENDER));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWGENDER, data.useroptional_gender);
			useroptional.setProperty(DSUtils.USEROPTIONAL_GENDER, data.useroptional_gender);
		}
		if(data.useroptional_job != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_JOB))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDJOB, useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWJOB, data.useroptional_job);
			useroptional.setProperty(DSUtils.USEROPTIONAL_JOB, data.useroptional_job);
		}
		if(data.useroptional_locality != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_LOCALITY))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDLOCALITY, useroptional.getProperty(DSUtils.USEROPTIONAL_LOCALITY));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWLOCALITY, data.useroptional_locality);
			useroptional.setProperty(DSUtils.USEROPTIONAL_LOCALITY, data.useroptional_locality);
		}
		if(data.useroptional_name != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_NAME))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDNAME, useroptional.getProperty(DSUtils.USEROPTIONAL_NAME));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWNAME, data.useroptional_name);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_NAME, data.useroptional_name);
		}
		if(data.useroptional_phone != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_PHONE))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDPHONE, useroptional.getProperty(DSUtils.USEROPTIONAL_PHONE));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWPHONE, data.useroptional_phone);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_PHONE, data.useroptional_phone);
		}
		if(data.useroptional_skills != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_SKILLS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDSKILLS, useroptional.getProperty(DSUtils.USEROPTIONAL_SKILLS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWSKILLS, data.useroptional_skills);
			useroptional.setProperty(DSUtils.USEROPTIONAL_SKILLS, data.useroptional_skills);
		}
		if(data.useroptional_zip != null) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_ZIP))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDZIP, useroptional.getProperty(DSUtils.USEROPTIONAL_ZIP));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWZIP, data.useroptional_zip);
			useroptional.setProperty(DSUtils.USEROPTIONAL_ZIP, data.useroptional_zip);
		}
	}

	@GET
	@Path("/votes/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getVotes(@PathParam (ParamName.USERNAME) String username,
			@Context HttpServletRequest request,
			@Context HttpHeaders headers) {
		if(username == null || username.equals(""))
			return Response.status(Status.BAD_REQUEST).entity(Message.PROFILE_UPDATE_DATA_INVALID).build();

		int retries = 5;

		while(true) {
			try {
				return getVotesRetry(username, request, headers);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response getVotesRetry(String username,
			HttpServletRequest request, HttpHeaders headers) {

		try {
			sameUserOrAdmin(request, username);
		} catch(NotSameNorAdminException e) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key userKey = KeyFactory.createKey(DSUtils.USER, username);
		Query query = new Query(DSUtils.USERVOTE).setAncestor(userKey);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(20);

		String cursor = headers.getHeaderString(CustomHeader.CURSOR);
		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		QueryResultList<Entity> allVotes = datastore.prepare(query).asQueryResultList(fetchOptions);

		JSONArray jsonarray = new JSONArray();

		for(Entity vote: allVotes) {
			JSONObject voteJson = new JSONObject();
			Map<String, Object> props = vote.getProperties();

			voteJson.put(DSUtils.USERVOTE_TYPE, props.get(DSUtils.USERVOTE_TYPE));
			voteJson.put(DSUtils.USERVOTE_USER, props.get(DSUtils.USERVOTE_USER));

			if(props.containsKey(DSUtils.USERVOTE_COMMENT))
				voteJson.put(DSUtils.USERVOTE_COMMENT, props.get(DSUtils.USERVOTE_COMMENT));
			if(props.containsKey(DSUtils.USERVOTE_EVENT))
				voteJson.put(DSUtils.USERVOTE_EVENT, props.get(DSUtils.USERVOTE_EVENT));
			if(props.containsKey(DSUtils.USERVOTE_REPORT))
				voteJson.put(DSUtils.USERVOTE_REPORT, props.get(DSUtils.USERVOTE_REPORT));

			jsonarray.put(voteJson);
		}

		if(jsonarray.length() < BATCH_SIZE)
			return Response.ok(jsonarray.toString()).build();

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
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response activateAccountRetry(String code, String username, HttpServletRequest request) {
		try {
			sameUserOrAdmin(request, username);
		} catch(NotSameNorAdminException e2) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		LOG.info("code - " + code);

		JSONObject codejson = new JSONObject(code);
		code = codejson.getString("code");

		LOG.info("new code - " + code);

		Key userkey = KeyFactory.createKey(DSUtils.USER, username);
		Entity user;
		try {
			user = datastore.get(userkey);
		} catch (EntityNotFoundException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		boolean bool = code.equals(user.getProperty(DSUtils.USER_CODE));
		if(!bool)
			return Response.status(Status.EXPECTATION_FAILED).build();

		user.setProperty(DSUtils.USER_CODE, ACTIVATED);

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
		} catch(NotSameNorAdminException e2) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		while(true) {
			try {
				return isActivatedRetry(username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
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
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		boolean yes =  entUser.getProperty(DSUtils.USER_CODE).toString().equals(ACTIVATED);

		return Response.ok().entity(yes).build();
	}

	@GET
	@Path("/view/{username}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getUserProfile(@Context HttpServletRequest request,
			@PathParam(ParamName.USERNAME) String username) {
		int retries = 5;

		try {
			sameUserOrAdmin(request, username);
		} catch(NotSameNorAdminException e) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		while(true) {
			try {
				return getUserProfileRetry(username);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
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
			LOG.info(Message.USER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject object = new JSONObject();

		object.put(DSUtils.USER, username);

		for(Entry<String, Object> prop : user.getProperties().entrySet()) {
			object.put(prop.getKey(), prop.getValue().toString());
		}

		return Response.ok(object.toString()).build();
	}

	public static String sameUserOrAdmin(HttpServletRequest request, String username)
			throws NotSameNorAdminException {

		String requester = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
		String level = request.getAttribute(CustomHeader.LEVEL_ATT).toString();

		if(!level.equals(UserLevel.ADMIN) && !requester.equals(username))
			throw new NotSameNorAdminException();

		return requester;
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
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	private Response getAllReportsRetry(String username, String cursor) {
		Query reportQuery = new Query(DSUtils.REPORT);
		
		Filter userFilter = new Query.FilterPredicate(DSUtils.REPORT_USERNAME,
				FilterOperator.EQUAL, username);
		
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		
		reportQuery.setFilter(userFilter);
		
		reportQuery
		.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USERNAME, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class));
		
		QueryResultList<Entity> reports = datastore.prepare(reportQuery).asQueryResultList(fetchOptions);
		
		if(reports.isEmpty()) {
			LOG.info(Message.NO_REPORTS_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}
		
		JSONArray array = new JSONArray();
		
		try {
			array = Report.buildJsonReports(reports, true);
		} catch(InternalServerErrorException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		cursor = reports.getCursor().toWebSafeString();
		
		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}
}
