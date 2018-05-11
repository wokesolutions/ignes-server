package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
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
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.OrgRegisterData;
import com.wokesolutions.ignes.data.UserRegisterData;
import com.wokesolutions.ignes.data.WorkerRegisterData;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/register")
public class Register {

	private static final Logger LOG = Logger.getLogger(Register.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public Register() {}

	@POST
	@Path("/user")
	@Consumes(MediaType.APPLICATION_JSON)
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
		LOG.info(Message.ATTEMPT_REGISTER_USER + registerData.user_username);

		Transaction txn = datastore.beginTransaction();
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.user_username);
			datastore.get(userKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Entity user = new Entity(DSUtils.USER, registerData.user_username);
			Key userKey = user.getKey();
			user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.user_password));
			user.setUnindexedProperty(DSUtils.USER_EMAIL, registerData.user_email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1.toString());
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, new Date());
			
			Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
			userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);
			
			Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);
			
			List<Entity> list = Arrays.asList(user, useroptional, userPoints);
			
			datastore.put(txn, list);
			
			LOG.info(Message.USER_REGISTERED + registerData.user_username);
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

	@POST
	@Path("/org")
	@Consumes(MediaType.APPLICATION_JSON)
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
			Entity org = new Entity(DSUtils.ORG, registerData.org_nif);
			org.setUnindexedProperty(DSUtils.ORG_NAME, registerData.org_name);
			org.setUnindexedProperty(DSUtils.ORG_PASSWORD, DigestUtils.sha512Hex(registerData.org_password));
			org.setUnindexedProperty(DSUtils.ORG_EMAIL, registerData.org_email);
			org.setProperty(DSUtils.ORG_ADDRESS, registerData.org_address);
			org.setProperty(DSUtils.ORG_LOCALITY, registerData.org_locality);
			org.setUnindexedProperty(DSUtils.ORG_PHONE, registerData.org_phone);
			org.setUnindexedProperty(DSUtils.ORG_ZIP, registerData.org_zip);
			org.setProperty(DSUtils.ORG_SERVICES, registerData.org_services);
			org.setProperty(DSUtils.ORG_ISFIRESTATION, registerData.org_isfirestation);
			org.setUnindexedProperty(DSUtils.ORG_CREATIONTIME, new Date());

			Entity orgCode = new Entity(DSUtils.ORGCODE , org.getKey());
			orgCode.setProperty(DSUtils.ORGCODE_ACTIVE, false);

			List<Entity> org_and_code = Arrays.asList(org, orgCode);

			datastore.put(txn, org_and_code);
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

	@POST
	@Path("/worker")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerWorker(WorkerRegisterData registerData) {
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

		int retries = 5;
		while(true) {
			try {
				return registerWorkerRetry(registerData);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response registerWorkerRetry(WorkerRegisterData registerData) {
		LOG.info(Message.ATTEMPT_REGISTER_WORKER + registerData.worker_username);

		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);
		try {
			// If the entity does not exist an Exception is thrown. Otherwise,
			Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.worker_username);
			datastore.get(userKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build(); 
		} catch (EntityNotFoundException e) {
			Query codeQuery = new Query(DSUtils.ORGCODE);
			codeQuery.setFilter(new Query.FilterPredicate(DSUtils.ORGCODE_CODE, FilterOperator.EQUAL, registerData.worker_code));

			List<Entity> results =
					datastore.prepare(codeQuery).asList(FetchOptions.Builder.withDefaults());

			if(results.size() == 0) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).entity(Message.ORG_CODE_NOT_FOUND).build();
			} else {
				Entity orgCode = results.get(0);

				if((boolean) orgCode.getProperty(DSUtils.ORGCODE_ACTIVE) == false) {
					txn.rollback();
					return Response.status(Status.EXPECTATION_FAILED).entity(Message.ORG_CODE_NOT_FOUND).build();
				}

				orgCode.setProperty(DSUtils.ORGCODE_ACTIVE, false);

				Key userKey = KeyFactory.createKey(DSUtils.USER, registerData.worker_username);
				Entity user = new Entity(userKey);
				user.setUnindexedProperty(DSUtils.USER_PASSWORD, DigestUtils.sha512Hex(registerData.worker_password));
				user.setUnindexedProperty(DSUtils.USER_EMAIL, registerData.worker_email);
				user.setProperty(DSUtils.USER_LEVEL, UserLevel.WORKER.toString());
				user.setUnindexedProperty(DSUtils.ORG_CREATIONTIME, new Date());

				Entity worker = new Entity(DSUtils.WORKER, userKey);
				worker.setProperty(DSUtils.WORKER_ORG, orgCode.getKey().getParent().getName());
				
				Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
				userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);
				
				Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

				List<Entity> allEntities = Arrays.asList(orgCode, user, worker, useroptional, userPoints);

				datastore.put(txn, allEntities);
				LOG.info(Message.WORKER_REGISTERED + registerData.worker_username);
				txn.commit();
				return Response.ok().build();
			}
		} finally {
			if(txn.isActive() ) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
