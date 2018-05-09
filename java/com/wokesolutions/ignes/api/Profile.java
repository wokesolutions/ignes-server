package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
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
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.UserOptionalData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;

@Path("/profile")
public class Profile {

	private static final Logger LOG = Logger.getLogger(Profile.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@PUT
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON + CustomHeader.CHARSET_UTF8)
	public Response updateProfile(@QueryParam (ParamName.USERNAME) String username,
			UserOptionalData data,
			@Context HttpServletRequest request) {
		if(!data.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.PROFILE_UPDATE_DATA_INVALID).build();

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

	// TODO
	private Response updateProfileRetry(String username, UserOptionalData data, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_UPDATE_PROFILE + username);

		Transaction txn = datastore.beginTransaction();
		try {
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			datastore.get(userKey);
			String altererusername = request.getAttribute(CustomHeader.USERNAME).toString();
			if(!altererusername.equals(username))
				return Response.status(Status.FORBIDDEN).entity(Message.ALTERER_IS_NOT_USER_OR_ADMIN).build();
			
			String alterer = request.getAttribute(CustomHeader.USERNAME).toString();
			Key altererKey = KeyFactory.createKey(DSUtils.USER, alterer);
			Query adminQuery = new Query(DSUtils.ADMIN).setAncestor(altererKey);
			List<Entity> admin = datastore.prepare(adminQuery).asList(FetchOptions.Builder.withDefaults());
			if(admin.isEmpty()) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity(Message.ALTERER_IS_NOT_USER_OR_ADMIN).build();
			}
			
			Query useroptionalQuery = new Query(DSUtils.USEROPTIONAL).setAncestor(userKey);
			List<Entity> useroptionallist = datastore.prepare(useroptionalQuery).asList(FetchOptions.Builder.withDefaults());
			if(useroptionallist.isEmpty()) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).entity(Message.NO_OPTIONAL_USER_ENTITY_FOUND).build();
			}

			Entity useroptionallog = new Entity(DSUtils.USEROPTIONALLOGS, altererKey);
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_CHANGETIME, new Date());
			useroptionallog.setProperty(DSUtils.USEROPTIONALLOGS_USERNAME, username);
			
			Entity useroptional = useroptionallist.get(0);
			
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
	
	private void fillOptional(String username, UserOptionalData data, Entity useroptional, Entity useroptionallog) {
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
}
