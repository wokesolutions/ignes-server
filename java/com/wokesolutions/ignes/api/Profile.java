package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
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
import com.wokesolutions.ignes.data.PasswordData;
import com.wokesolutions.ignes.data.ProfPicData;
import com.wokesolutions.ignes.data.UserOptionalData;
import com.wokesolutions.ignes.exceptions.NotSameNorAdminException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
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

	private static final String USER_REPORTS = "user_reports";
	private static final String PROFILEPIC = "profilepic";

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
		if(data.useroptional_address != null && !data.useroptional_address.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_ADDRESS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDADDRESS, useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWADDRESS, data.useroptional_address);
			useroptional.setProperty(DSUtils.USEROPTIONAL_ADDRESS, data.useroptional_address);
		}
		if(data.useroptional_birth != null && !data.useroptional_birth.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_BIRTH))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDBIRTH, useroptional.getProperty(DSUtils.USEROPTIONAL_BIRTH));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWBIRTH, data.useroptional_birth);
			useroptional.setProperty(DSUtils.USEROPTIONAL_BIRTH, data.useroptional_birth);
		}
		if(data.useroptional_gender != null && !data.useroptional_gender.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_GENDER))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDGENDER, useroptional.getProperty(DSUtils.USEROPTIONAL_GENDER));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWGENDER, data.useroptional_gender);
			useroptional.setProperty(DSUtils.USEROPTIONAL_GENDER, data.useroptional_gender);
		}
		if(data.useroptional_job != null && !data.useroptional_job.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_JOB))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDJOB, useroptional.getProperty(DSUtils.USEROPTIONAL_ADDRESS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWJOB, data.useroptional_job);
			useroptional.setProperty(DSUtils.USEROPTIONAL_JOB, data.useroptional_job);
		}
		if(data.useroptional_locality != null && !data.useroptional_locality.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_LOCALITY))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDLOCALITY, useroptional.getProperty(DSUtils.USEROPTIONAL_LOCALITY));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWLOCALITY, data.useroptional_locality);
			useroptional.setProperty(DSUtils.USEROPTIONAL_LOCALITY, data.useroptional_locality);
		}
		if(data.useroptional_name != null && !data.useroptional_name.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_NAME))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDNAME, useroptional.getProperty(DSUtils.USEROPTIONAL_NAME));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWNAME, data.useroptional_name);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_NAME, data.useroptional_name);
		}
		if(data.useroptional_phone != null && !data.useroptional_phone.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_PHONE))
				useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_OLDPHONE, useroptional.getProperty(DSUtils.USEROPTIONAL_PHONE));
			useroptionallog.setUnindexedProperty(DSUtils.USEROPTIONALLOGS_NEWPHONE, data.useroptional_phone);
			useroptional.setUnindexedProperty(DSUtils.USEROPTIONAL_PHONE, data.useroptional_phone);
		}
		if(data.useroptional_skills != null && !data.useroptional_skills.equals("")) {
			if(useroptional.hasProperty(DSUtils.USEROPTIONAL_SKILLS))
				useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_OLDSKILLS, useroptional.getProperty(DSUtils.USEROPTIONAL_SKILLS));
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_NEWSKILLS, data.useroptional_skills);
			useroptional.setProperty(DSUtils.USEROPTIONAL_SKILLS, data.useroptional_skills);
		}
		if(data.useroptional_zip != null && !data.useroptional_zip.equals("")) {
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
			@Context HttpHeaders headers, @QueryParam(ParamName.CURSOR) String cursor) {
		if(username == null || username.equals(""))
			return Response.status(Status.BAD_REQUEST).entity(Message.PROFILE_UPDATE_DATA_INVALID).build();

		int retries = 5;

		while(true) {
			try {
				return getVotesRetry(username, request, headers, cursor);
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
			HttpServletRequest request, HttpHeaders headers, String cursor) {

		LOG.info(Message.GIVING_VOTES + username);

		try {
			sameUserOrAdmin(request, username);
		} catch(NotSameNorAdminException e) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key userKey = KeyFactory.createKey(DSUtils.USER, username);
		Query query = new Query(DSUtils.USERVOTE).setAncestor(userKey);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(20);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		QueryResultList<Entity> allVotes = datastore.prepare(query).asQueryResultList(fetchOptions);

		JSONArray jsonarray = new JSONArray();

		for(Entity vote: allVotes) {
			JSONObject voteJson = new JSONObject();
			Map<String, Object> props = vote.getProperties();

			voteJson.put(DSUtils.USERVOTE_TYPE, props.get(DSUtils.USERVOTE_TYPE));
			//voteJson.put(DSUtils.USERVOTE_USER, props.get(DSUtils.USERVOTE_USER));

			if(props.containsKey(DSUtils.USERVOTE_COMMENT))
				voteJson.put(DSUtils.USERVOTE_COMMENT, props.get(DSUtils.USERVOTE_COMMENT));
			if(props.containsKey(DSUtils.USERVOTE_EVENT))
				voteJson.put(DSUtils.USERVOTE_EVENT, props.get(DSUtils.USERVOTE_EVENT));
			if(props.containsKey(DSUtils.USERVOTE_REPORT))
				voteJson.put(DSUtils.USERVOTE_REPORT, props.get(DSUtils.USERVOTE_REPORT));

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

		for(Entry<String, Object> prop : user.getProperties().entrySet())
			if(!prop.getKey().equals(DSUtils.USER_PASSWORD))
				object.put(prop.getKey(), prop.getValue().toString());

		Query query = new Query(DSUtils.USEROPTIONAL).setAncestor(user.getKey());

		Entity optionals;
		try {
			optionals = datastore.prepare(query).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		for(Entry<String, Object> prop : optionals.getProperties().entrySet())
			object.put(prop.getKey(), prop.getValue().toString());

		Query query2 = new Query(DSUtils.USERPOINTS).setAncestor(user.getKey());
		Entity points;
		try {
			points = datastore.prepare(query2).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		object.put(DSUtils.USERPOINTS_POINTS, points.getProperty(DSUtils.USERPOINTS_POINTS));

		Query query3 = new Query(DSUtils.REPORT);
		Filter filter = new Query.FilterPredicate(DSUtils.REPORT_USERNAME,
				FilterOperator.EQUAL, username);
		query3.setFilter(filter);

		int reports = datastore.prepare(query3).asList(FetchOptions.Builder.withDefaults()).size();

		object.put(USER_REPORTS, reports);

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
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				Transaction txn = datastore.beginTransaction();

				try {
					String newPw = DigestUtils.sha512Hex(data.newpassword);
					String oldPw = DigestUtils.sha512Hex(data.oldpassword);

					if(!user.getProperty(DSUtils.USER_PASSWORD).toString().equals(oldPw)) {
						LOG.info(Message.WRONG_PASSWORD + oldPw);
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
					LOG.info(Message.PASSWORD_CHANGED + username);
					return Response.ok().build();
				} finally {
					if(txn.isActive()) {
						LOG.info(Message.TXN_ACTIVE);
						txn.rollback();
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}
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
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, String.class));

		QueryResultList<Entity> reports = datastore.prepare(reportQuery).asQueryResultList(fetchOptions);

		if(reports.isEmpty()) {
			LOG.info(Message.NO_REPORTS_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONArray array = new JSONArray();

		try {
			array = Report.buildJsonReports(reports, false);
		} catch(InternalServerErrorException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		cursor = reports.getCursor().toWebSafeString();

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
					LOG.warning(Message.TOO_MANY_RETRIES);
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
		if(!Storage.saveImage(data.pic, Storage.BUCKET, pathImg, data.width, data.height))
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();

		Query query = new Query(DSUtils.USEROPTIONAL)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, username))
				.setKeysOnly();

		try {
			Entity optional = datastore.prepare(query).asSingleEntity();
			optional.setProperty(DSUtils.USEROPTIONAL_PICPATH, pathImg.makePath());
			optional.setProperty(DSUtils.USEROPTIONAL_PICTNPATH, pathImg.makeTnPath());
			
			datastore.put(optional);
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
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
					LOG.warning(Message.TOO_MANY_RETRIES);
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
			LOG.info(Message.USER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}
		
		path = optional.getProperty(DSUtils.USEROPTIONAL_PICPATH).toString();
		
		if(path == null) {
			LOG.info(Message.USER_HAS_NO_IMAGE);
			return Response.status(Status.NO_CONTENT).build();
		}
		
		img = Storage.getImage(path);
		
		JSONObject obj = new JSONObject();
		obj.put(PROFILEPIC, img);
		
		return Response.ok(obj.toString()).build();
	}
}
